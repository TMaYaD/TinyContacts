# Temporary Contacts (Android)

An Android app that stores short-lived phone contacts (a painter, a tour guide, a delivery
driver…) under a **dedicated, local-only account type** so they are:

- **fully separated** from your real Google/Samsung contacts and **never synced** anywhere,
- **visible and indistinguishable** to every other app (dialer, messaging, …) via
  `ContactsContract`,
- **auto-deleted** at a per-contact expiry time,
- **bulk-wipeable** in one atomic operation by removing the account (nuclear delete).

## The account model (read this first)

Everything hinges on one idea: a custom account type that looks real to the OS but has no
backend.

| Piece | Role |
| --- | --- |
| `account/TempAuthenticator` (+ `AuthenticatorService`) | An `AbstractAccountAuthenticator` **stub** — no login, no server, no tokens. Its only job is to exist so `AccountManager` accepts an account of type `dev.loonybin.tempcontacts`. |
| `account/SyncAdapter` (+ `SyncService`) | An `AbstractThreadedSyncAdapter` whose `onPerformSync` is **empty**. Registering it (with `supportsUploading=false`) is what convinces the OS the account is a legitimate, backed contacts source — so it does **not** garbage-collect the account or its contacts — while guaranteeing nothing ever leaves the device. |
| `res/xml/authenticator.xml` | Declares `accountType` + the "Temporary Contacts" label. |
| `res/xml/sync_adapter.xml` | Binds the account type to the `com.android.contacts` authority, `supportsUploading=false`. |
| `res/xml/contacts.xml` | `CONTACTS_STRUCTURE` metadata so the system Contacts app renders the account as an editable "Temporary" source. |
| `account/SystemTempAccountManager` | The only class that talks to `AccountManager`: `ensureAccount()` (create-if-absent) and `removeAccount()` (`removeAccountExplicitly` → atomic bulk delete). |

### The two correctness traps this project is built around

1. **All contact writes/deletes go through the sync-adapter URI.**
   `ContactsContractRepository.syncAdapterUri()` appends `CALLER_IS_SYNCADAPTER=true` (plus the
   account name/type) to every write/delete URI. Without it, **deletes are soft** (the row is
   only flagged `DELETED=1`, kept pending a sync round-trip that never comes) and **inserts are
   marked dirty**. This decoration lives in exactly one place.

2. **Expiry is stored in the RawContact's `sync1` column** (epoch millis) — no separate
   database. A `WorkManager` periodic worker (`expiry/ExpiryWorker`) selects `sync1 < now` and
   hard-deletes those raw contacts.

## Package boundaries

Cross-package calls go through a small interface per package — never a concrete class.

```
account/    Authenticator/Service, SyncAdapter/Service, and SystemTempAccountManager
            (AccountBootstrap + AccountWipe interfaces). Knows nothing about UI or expiry.
contacts/   TempContactsRepository (interface) + ContactsContractRepository — the ONLY place
            that touches ContactsContract. Owns the CALLER_IS_SYNCADAPTER decoration and reads
            /writes sync1 as an opaque Long.
expiry/     ExpiryScheduler (WorkManager registration) + ExpiryWorker. The worker asks the pure
            ExpiryPolicy which rows are past due, then tells contacts/ to purge them. Knows
            nothing about authenticator internals.
ui/         Compose screens: add contact + duration picker, list of active temp contacts with
            remaining time, and a "Wipe all" button.
```

The **`:expiry-policy` module** is a separate, pure-Kotlin/JVM module (no Android plugin, no
Android deps) holding the `sync1 < now` selection logic. Keeping it Android-free is what lets
its unit tests run headless — see below.

## Building

Standard Android project, single `:app` module plus the pure `:expiry-policy` module.

```bash
./gradlew assembleDebug        # builds the APK (requires the Android SDK)
./gradlew :expiry-policy:test  # runs the pure expiry-policy unit tests (no SDK needed)
```

Requirements: JDK 17+, the Android SDK (`ANDROID_HOME` or `local.properties` with `sdk.dir`),
`compileSdk 34`, `minSdk 26`.

> **SDK-less environments (CI sandboxes, headless VMs).** `settings.gradle.kts` includes the
> Android `:app` module only when an Android SDK is detected. Without one it configures just
> `:expiry-policy`, so `./gradlew :expiry-policy:test` still works. This is deliberate — it
> keeps the pure logic testable where no emulator/SDK exists.

### Build-environment note (Claude Code web / allowlisted proxies)

Android dependency resolution — **both** the Android Gradle Plugin **and** AndroidX — comes from
Gradle's `google()` repository, which is backed by **`dl.google.com`** (`/dl/android/maven2`).
Installing the Android SDK command-line tools/platforms also fetches from `dl.google.com`. If
your environment uses an egress allowlist, add `dl.google.com` (and `maven.google.com`) to the
allowed domains **before** the first Gradle run, or resolution fails immediately with
"plugin `com.android.application` … was not found".

## Running the tests

The expiry policy tests are pure JVM logic and run with no Android runtime:

```
$ ./gradlew :expiry-policy:test
...
ExpiryPolicyTest > 11 tests, 0 failures
```

They lock the selection semantics: strictly-before-`now` is expired, the boundary (`== now`) is
**not** yet expired (matching the SQL `<` selection), `null` expiry never expires, and
`selectExpired` returns exactly the past-due ids in order.

## Why not the vestrel00 library everywhere?

The spec suggests [`vestrel00/contacts-android`](https://vestrel00.github.io/contacts-android/)
for `ContactsContract` batching, and it is a great fit for ordinary reads/writes against an
arbitrary account. It is **not** used for this app's sync-critical operations because — by
design — it does no syncing and therefore does **not** expose either of the two things this app
depends on: the `RawContacts.sync1` column (where we stash expiry) and the
`CALLER_IS_SYNCADAPTER` URI flag (which makes deletes hard instead of soft). Those operations
must use `ContentResolver` directly, encapsulated behind the single `syncAdapterUri()` helper in
`ContactsContractRepository`. Rather than ship an unused dependency, the repository owns this
plumbing directly; the library remains the recommended tool if you later add richer read queries
that don't need `sync1`.

## What you must verify yourself (cannot be checked in a headless VM)

A green `assembleDebug` proves the project compiles; it proves **nothing** about the two runtime
behaviors that are the entire point of the app. Verify these on a real device or emulator:

1. **Interoperability.** After adding a temp contact, open the **system Contacts app** and a
   **third app** (e.g. the dialer) and confirm the temp contact appears there **identically** to
   a normal contact — searchable, callable, indistinguishable.
2. **Persistence of the local-only account.** Confirm the account (authenticator present, no
   remote service) and its contacts **persist** across reboots and are **not** garbage-collected
   by the OS when it finds no sync backend. Leave the device idle and re-check.

Also worth exercising by hand: set a short expiry (edit `DurationOption` to add e.g. a 1-minute
option), confirm the `ExpiryWorker` purges it, and confirm **"Wipe all"** removes the account and
drops every temp contact at once.
