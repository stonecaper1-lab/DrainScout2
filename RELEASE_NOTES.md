## DrainScout v1.7.0

**Built to find the culprit of idle drain.**

- **New Culprit tab:** answers "Did the phone sleep?" by comparing CPU awake time with screen-off time. A phone that is awake 90% or more of the time it is locked is flagged as never sleeping.
- **Ranked suspects** with High / Medium / Low confidence and a "what to try" line: the system doze service holding the CPU, unusual wake locks, busy processes, noisy alarms, heavy kernel wakelocks.
- **Doze service lock:** a `dream:doze` lock held for minutes with the screen off is now flagged as unusual.
- **Processes:** busiest processes by CPU (Details > Processes).
- **Alarms** now show wakeups per hour since boot, so a large total spread over days is not mistaken for a problem.
- Hardware components such as CPU and Screen are no longer reported as if they were apps.
- **Timeline** (was History) shows each capture's sleep result.
- Four tabs instead of seven: Culprit, Timeline, Details, More. The report now includes the sleep check, suspects and busy processes.
- Same signing key: installs over earlier versions. Saved history from v1.5.0 and v1.6.0 is kept.
