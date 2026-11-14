## DrainScout v1.5.0

- **New History tab:** every successful capture is saved on the device (last 40). See battery, screen state, wake lock counts, drain between captures, and tap a capture for its wake locks, consumers, alarms and kernel wakelocks. Repeat offenders (flagged unusual more than once) are listed on top. Clear history any time.
- More wakelock knowledge: media scanner, connectivity, sync loop, Google connection, activity manager, lock screen refresh and AlarmManager locks now get their own description and limit, based on the XDA wakelock guide (written for Android 4.x, so treated as hints).
- Includes the v1.4.0 capture fix (user service channel) and lightweight redesign.
- Same signing key: installs over earlier versions.
