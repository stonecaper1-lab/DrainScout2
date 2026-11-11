<div align="center">

# 🔋 DrainScout

**Find out what's draining your battery while your phone sleeps** 🕵️‍♂️



![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B-3DDC84?logo=android&logoColor=white)




![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white)




![Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white)




![Shizuku](https://img.shields.io/badge/Powered%20by-Shizuku-8AE6C9)



</div>

---

## ✨ What it does

DrainScout uses [Shizuku](https://shizuku.rikka.app/) to run read-only `dumpsys` commands and turns the noisy output into something you can actually read.

- 📸 **One-tap baseline capture** of battery, power, alarm and device-idle state
- 🔍 **Smart filtering** that keeps only the lines relevant to idle drain
- 🧭 **Overview tab** with plain-language findings
- ⏰ **Wakelocks, Alarms and Idle tabs** for the raw evidence
- 📋 **Report tab** to copy or share everything as text
- 🌙 **Dark UI** that's easy on the eyes

> ⚠️ DrainScout is a **triage tool**. A finding means "look here", not "this is the cause". Capture at the same battery level and screen-off time, and compare two or more snapshots before drawing conclusions.

## 📋 Requirements

| | |
|---|---|
| 📱 Android | 8.0 (API 26) or newer |
| 🛡️ Shizuku | Installed and running |
| 🔑 Permission | Shizuku access granted to DrainScout |

## 📥 Install

1. ⬇️ Download `DrainScout.apk` from the [latest release](../../releases/latest)
2. 📦 Install it (allow installs from your browser or file manager if asked)
3. ▶️ Start Shizuku, then open DrainScout
4. 🔗 Tap **Connect Shizuku** and allow the permission
5. 📸 Tap **Capture baseline**

> 🔏 Releases are signed with a fixed project key, so updates install over each other. Coming from v1.0? Uninstall it once first.

## 🛠️ Build from source

Requires JDK 17 and Gradle 8.9+.

    git clone https://github.com/stonecaper1-lab/DrainScout2.git
    cd DrainScout2
    gradle :app:assembleDebug

📂 The APK lands in `app/build/outputs/apk/debug/`.
🤖 Every push also builds on GitHub Actions and uploads the APK as the `drainscout-debug` artifact.

## 🚧 Known limitations

- 🧪 `dumpsys` output differs between vendors and Android versions. If a tab says "No common summary lines matched", the filter patterns need tuning for that device
- 🪞 Commands run through Shizuku's process API via reflection, because it isn't public in Shizuku API 13.1.x. A future Shizuku update could break this
- 🎯 Findings are keyword heuristics, not a root-cause analysis

## 🔒 Privacy

🌐 The only network use is an optional update check against the GitHub releases API (switch it off with the Update alerts button). Nothing else leaves your device unless you copy or share a report yourself.

## 🤝 Contributing

🐛 Found a device where the filters miss something? Open an issue with the model, Android version and a trimmed `dumpsys` sample.

---

<div align="center">Made with 💚 on a phone</div>
