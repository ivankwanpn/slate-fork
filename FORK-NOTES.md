# Slate fork of Shizuku

This repository is a fork of [Shizuku](https://github.com/RikkaApps/Shizuku) v13.6.0 (Apache-2.0)
with a small, self-contained addition: a component audit and management UI.

Everything else is upstream Shizuku and is unmodified.

## What was changed

Two commits:

1. `Import Shizuku v13.6.0 upstream` — pristine upstream source.
2. `Add Slate UI, shell service and rebrand` — the following files:

| File | Purpose |
|---|---|
| `manager/src/main/java/moe/shizuku/manager/phonefix/PhoneFixActivity.kt` | Five-screen UI: Home, Components, Record, Settings, About |
| `manager/src/main/java/moe/shizuku/manager/phonefix/ShellService.kt` | AIDL user service executed as shell |
| `manager/src/main/aidl/moe/shizuku/manager/phonefix/IShellService.aidl` | Interface for the above |
| `manager/src/main/res/drawable/ic_slate.xml` | App icon |
| `manager/src/main/AndroidManifest.xml` | Launcher activity, label/icon, `QUERY_ALL_PACKAGES` |
| `manager/build.gradle` | Enable AIDL |

The app label is "Slate". The application id is `io.github.ivankwanpn.slate`, so Slate can be installed alongside the real Shizuku instead of replacing it.

## Build

```
./gradlew :manager:assembleDebug
```

Requires JDK 21, Android SDK 36 and NDK 29 (upstream requirements).

## Relation to the main repository

Research report, Windows auditor, standalone audit app and the patch:
https://github.com/ivankwanpn/slate

The patch in that repository (`patches/slate-fork.patch`) is the same change set as the second commit here.

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

## License

Shizuku is Apache-2.0. See `LICENSE`. The added files are released under the same license.
