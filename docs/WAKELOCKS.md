# How DrainScout judges wake locks

DrainScout marks each wake lock it finds as **Usual** or **Unusual**. This page explains the rules so you can disagree with them.

## What is measured

`dumpsys power` lists the wake locks held at the moment of capture, with their tag, owner and how long ago they were acquired (`ACQ=`). When the system holds a lock on behalf of an app, the WorkSource uid is used as the owner.

## Known lock kinds

Tag patterns follow Android's guide to identifying wake lock use cases.

| Tag pattern | Kind | Expected hold | Unusual above |
|---|---|---|---|
| `GOOGLE_C2DM`, `GCM_*` | Push message (FCM) | seconds | 2 min |
| `*alarm*` | AlarmManager | seconds | 2 min |
| `*job*` | JobScheduler or WorkManager task | short | 30 min |
| `*location*`, `Nlp*`, `CollectionLib*` | Location collection | brief | 15 min |
| `Audio*`, `MmapCapture`, `MmapPlayback` | Audio | while media is active | 3 h |
| `_UNKNOWN` | Name hidden by the system | n/a | 30 min |
| anything else | Custom app lock | n/a | 60 min, 10 min while the screen is off |

Screen locks (`SCREEN_*`, `FULL`) are usual while the screen is on and unusual while it is off.

## Kernel wakelocks

Totals since the last full charge. A kernel wakelock held for 60 minutes or more in total is marked unusual. Descriptions are based on kernel naming and community guides, so treat them as hints.

## Where the limits come from

The minute limits are DrainScout heuristics. The only official numbers involved are Google's:

- Android vitals calls partial wake lock use excessive when all partial wake locks together run **2 hours or more in 24 hours**, counted in the background or in a foreground service. Audio, location and user-initiated JobScheduler locks are exempt.
- Google Play's technical quality enforcement uses **2 hours on average while the screen is off, in more than 5% of sessions over 28 days**.

A single capture shows a moment in time. One Unusual lock is a lead, not a verdict.

## Sources

- Android Developers: Excessive partial wake locks
- Android Developers: Identify and optimize wake lock use cases
- Android Developers blog: Battery technical quality enforcement
