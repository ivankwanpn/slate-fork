# Slate fork of Shizuku

This repository is a fork of [Shizuku](https://github.com/RikkaApps/Shizuku) v13.6.0 (Apache-2.0)
with a component audit and management UI.

The app is **client-only**: it requires the official Shizuku app (`moe.shizuku.privileged.api`)
to provide the shell channel. The upstream server/starter modules stay in the tree for reference,
but they are not part of the shipped build.

## What was changed

Most of the UI lives in `manager/src/slate` (compiled instead of `manager/src/main`):

| File | Purpose |
|---|---|
| `manager/src/slate/java/.../PhoneFixActivity.kt` | Five-screen UI: Home, Components, Record, Settings, About |
| `manager/src/slate/java/.../ShellService.kt` | AIDL user service executed as shell |
| `manager/src/slate/java/.../SlateProvider.kt` | Receives the Shizuku binder |
| `manager/src/slate/java/.../RegisterHelperClient.kt` | Experimental serverAddress redirect |
| `manager/src/slate/java/.../NetBlockVpnService.kt` | Optional per-app VPN network block |
| `manager/src/slate/AndroidManifest.xml` | Launcher activity, provider, services, VPN service |
| `manager/build.gradle` | Client-only source sets, signing fallback |

The app label is "Slate". The application id is `io.github.ivankwanpn.slate`, so Slate can be installed alongside the real Shizuku instead of replacing it.

## Build

```
./gradlew :manager:assembleRelease
```

Requires JDK 21, Android SDK 36 and the `api` submodule (Shizuku-API).

## Relation to the main repository

Research report, Windows auditor, standalone audit app and the historical patch:
https://github.com/ivankwanpn/slate

The patch in that repository (`patches/slate-fork.patch`) is an early snapshot and may lag behind
this fork. The source of truth for the Android app is this repository.

Releases: https://github.com/ivankwanpn/slate-fork/releases/latest

## Rebrand (2026-11)

Slate is no longer a drop-in replacement for Shizuku: it can be installed next to it.

| Item | Old | New |
|---|---|---|
| applicationId | `moe.shizuku.privileged.api` | `io.github.ivankwanpn.slate` |
| API permission | `moe.shizuku.manager.permission.API_V23` | `io.github.ivankwanpn.slate.permission.API_V23` |
| manager permission | `moe.shizuku.manager.permission.MANAGER` | `io.github.ivankwanpn.slate.permission.MANAGER` |
| permission group | `moe.shizuku.manager.permission-group.API` | `io.github.ivankwanpn.slate.permission-group.API` |
| server process | `shizuku_server` | `slate_server` |
| launcher label | `PhoneFix` | `Slate` |

The server and starter address the manager by package name (`ServerConstants.MANAGER_APPLICATION_ID`,
`starter.cpp PACKAGE_NAME`, `ServiceStarter`), and the provider authority is `${applicationId}.shizuku`;
all of them follow the new id. The server process was renamed so starting Slate does not kill the
real Shizuku server.

`moe.shizuku.privileged.api.intent.extra.BINDER` is kept as an opaque bundle key; it only has to agree
between starter, server and manager.

The `api` submodule (Shizuku-API) still carries the upstream constants in `ShizukuProvider`; they are
unused by this app. If third-party clients are added later, that submodule needs the same rename.

## Carrier DM redirect (experimental)

The component detail screen for `com.mobiletools.systemhelper` has an "上報位址" card. It binds the
exported, permission-less `RegisterHelperService` and sends transaction 5 (`setServerAddress`), which
writes the `serverAddress` key in the component's own SharedPreferences.

This is a local, non-root, persistent redirect: the vendor daemon only changes the package enabled
state, not the app's data, so the value survives reboots. The target package has to be enabled while
the transaction is sent; it can be disabled again afterwards.

Code: `RegisterHelperClient.kt` plus the card in `PhoneFixActivity.kt`.

## Client-only architecture (2026-11)

Slate no longer bundles Shizuku. The manager/server/starter modules stay in the repository for
reference, but `manager` now compiles `manager/src/slate` only:

- app id `io.github.ivankwanpn.slate`, label `Slate`
- requires the official Shizuku app (`moe.shizuku.privileged.api`); Slate requests
  `moe.shizuku.manager.permission.API_V23` and receives the binder through
  `<applicationId>.shizuku` (`SlateProvider`)
- `ShellService` is started by official Shizuku as a user service
- `manager/src/main` (the old bundled manager) is not part of the build

The bundled-server flow could not pair over wireless debugging on the target device, which is why
the client split was made.

## Current features (v0.3.7)

- Scans carrier DM / registration components: `com.mobiletools.systemhelper`,
  `com.vivo.devicereg` and related names.
- Package states: running / disabled / uninstalled (with `cmd package install-existing` restore).
- Runtime permission switches (ON = granted), AppOps `GET_USAGE_STATS`, install-time permissions
  collapsed behind a toggle.
- Snapshot of granted permissions before revoke, exact restore from that snapshot.
- `netpolicy` background restriction for metered data:

  ```
  cmd netpolicy add restrict-background-blacklist <uid>
  cmd netpolicy list restrict-background-blacklist
  ```

  Querying uses `cmd netpolicy list`, not `dumpsys netpolicy` (vivo does not print the word
  "blacklist" there).
- Optional per-app VPN network block for foreground + Wi-Fi + mobile data. It takes the single
  VPN slot, so it conflicts with proxy VPNs; it is off by default, never auto-starts and warns
  before enabling.
- Experimental `serverAddress` redirect for SystemHelper, with a record of the last written value
  (the app has no getter and its private prefs are unreadable without root).
- Dark mode, status labels, async scan/commands with progress dialogs.

Research details: [slate/docs/HARDENING.md](https://github.com/ivankwanpn/slate/blob/master/docs/HARDENING.md)
and [slate/docs/DEVICEREG.md](https://github.com/ivankwanpn/slate/blob/master/docs/DEVICEREG.md).

## License

Shizuku is Apache-2.0. See `LICENSE`. The added files are released under the same license.
