## 🔋 DrainScout v1.2.0

🔬 **Deep wakelock analysis** and 🔔 **update alerts**.

### 🔒 Wakelocks, usual or unusual
- 🏷️ Every wake lock held right now is marked **Usual** or **Unusual**
- 📖 Each one gets a plain-language description of what it is and why apps hold it
- ⏱️ Shows how long it has been held, and why it was judged the way it was
- 🎯 Locks held by the system on behalf of an app are attributed to the real app
- 🌙 Uses the screen state: the same lock is judged more strictly while the screen is off
- 🧩 Recognises FCM push, alarms, JobScheduler/WorkManager jobs, location and audio locks
- 🐧 Kernel wakelocks get descriptions too, and heavy ones are flagged
- 📚 See `docs/WAKELOCKS.md` for the rules and their sources

### 🔔 Update alerts
- 📲 A normal system notification when a new release is published, even if the app is closed
- ⏰ Checked about every 6 hours, and again after a reboot
- 🎚️ Turn it off any time with the **Update alerts** button
- 🌐 Adds the Internet permission, used only to ask GitHub for the latest release

### 📝 Notes
- 🔑 Same signing key as v1.1.0, so this installs over it
- 🧪 The usual/unusual limits are DrainScout heuristics, not official Android thresholds
- 🛡️ Needs Android 8.0+ and Shizuku running
