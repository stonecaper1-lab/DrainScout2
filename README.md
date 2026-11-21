# DrainScout

Find out **why your phone loses battery while it sits idle**. DrainScout checks whether the CPU actually sleeps with the screen off, then ranks the likely culprits: wake locks, busy processes, alarms and Doze. It reads the data through Shizuku, needs no root, and has no ads or tracking.

## Tabs
- **Culprit**: "Did the phone sleep?" verdict (CPU awake time against screen-off time), ranked suspects with a confidence level and what to try, an idle-test guide, then battery, findings and consumers
- **Timeline**: every capture saved (last 300) with its sleep result, drain between captures and repeat offenders; the **continuous monitor** captures every 15, 30 or 60 minutes in the background, even with the app closed
- **Details**: Wakelocks (Usual / Unusual verdicts and descriptions), Alarms (with per-hour rate), Processes (busiest by CPU)
- **More**: Report (copy or share, includes the sleep check), Device info, About and update alerts

## Setup
1. Install and start [Shizuku](https://shizuku.rikka.app) (wireless debugging or root).
2. Open DrainScout, tap **Connect**, then **Capture**.
3. For the idle test, start the monitor, lock the phone for an hour or more, and open the Culprit tab.

## How it reads data
A Shizuku user service runs the shell commands (`dumpsys`, `cmd package`). If that fails, a reflection fallback reads the output directly. The Device tab shows which channel is active. The sleep check compares realtime with uptime from `batterystats`: uptime stops while the CPU is suspended, so awake time while the screen is off shows whether the phone really sleeps.

## Size
Plain Android Views (no Compose or AndroidX), R8 shrinking, minimal resources. The APK size is printed in the GitHub Actions step summary.

## Privacy
Everything is analysed on the device. The only network call is the optional update check against GitHub releases (toggle in About).

## Build
Push a `v*` tag; GitHub Actions builds and publishes the release APK. The signing key is fixed, so updates install over older versions.
