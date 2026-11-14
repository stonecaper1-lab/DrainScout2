## DrainScout v1.6.0

- **Continuous monitor:** History tab has a Start/Stop switch. A foreground service captures every 15, 30 or 60 minutes (default 15), survives swiping the app away, restarts after a reboot, and keeps going in Doze, so a whole 100% to 0% discharge is recorded.
- **Current discharge card:** start and end level, duration, average %/h, and estimated time left.
- Charging samples are skipped (only the first of a charge is kept) so history stays about draining. History keeps the last 300 captures.
- "Keep alive" button opens Android's battery setting. Choose Unrestricted for DrainScout. Some phones also need auto-start allowed.
- Shows a notification while monitoring, with a Stop action.
- Includes History tab and XDA wakelock knowledge (v1.5.0), capture fix and redesign (v1.4.0). Same signing key.
