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

The app label is "Slate". The application id remains `moe.shizuku.privileged.api` for now.

## Build

```
./gradlew :manager:assembleDebug
```

Requires JDK 21, Android SDK 36 and NDK 29 (upstream requirements).

## Relation to the main repository

Research report, Windows auditor, standalone audit app and the patch:
https://github.com/ivankwanpn/slate

The patch in that repository (`patches/slate-fork.patch`) is the same change set as the second commit here.

## License

Shizuku is Apache-2.0. See `LICENSE`. The added files are released under the same license.
