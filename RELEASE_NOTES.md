## DrainScout v1.4.0

- **Fixed:** "No data came back / process hasn't exited". Commands now run through a Shizuku user service (same approach as wakelogs), with a safe reflection fallback.
- **Redesign:** lighter plain-Views UI with liquid-glass styling, much smaller APK (R8, no Compose/AndroidX).
- Scrollable tab bar so six tabs fit small screens.
- Amber notice when only part of the data could be read.
- Device tab shows the command channel and last shell error.
- Same signing key: installs over v1.1 to v1.3.
