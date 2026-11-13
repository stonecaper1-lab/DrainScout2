# DrainScout

Find out **why your phone loses battery while it sits idle**: wakelocks, alarms, Doze and the top consumers, read through Shizuku. No root. Small plain-Views app, no ads, no tracking.

## Tabs
- **Overview**: battery ring, findings, top consumers
- **Wakelocks**: every held wakelock with a Usual / Unusual verdict and a plain description
- **Alarms**: apps that wake the device most
- **Device**: system info and the active command channel
- **Report**: copy or share a text report, set a baseline to measure drain since
- **About**: update alerts toggle, links

## Setup
1. Install and start [Shizuku](https://shizuku.rikka.app) (wireless debugging or root).
2. Open DrainScout, tap **Grant access**, then **Scan**.

## How it reads data
A Shizuku user service runs the shell commands (`dumpsys`, `cmd package`). If that fails, a reflection fallback reads the output directly. The Device tab shows which channel is active.

## Size
Plain Android Views (no Compose or AndroidX), R8 shrinking, minimal resources. The APK size is printed in the GitHub Actions step summary.

## Privacy
Everything is analysed on the device. The only network call is the optional update check against GitHub releases (toggle in About).

## Build
Push a `v*` tag; GitHub Actions builds and publishes the release APK. Signing key is fixed, so updates install over older versions.
