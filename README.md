<div align="center">

# 🔋 DrainScout

### Find out what may be keeping your Android phone awake.

**A lightweight battery-diagnostics app for inspecting wakelocks, alarms, Doze, and top battery consumers—with Shizuku, no root required.**

[📥 Releases](https://github.com/stonecaper1-lab/DrainScout2/releases) · [🛠️ Report an issue](https://github.com/stonecaper1-lab/DrainScout2/issues)

</div>

---

## ✨ What you can investigate

| Screen | What it shows |
|---|---|
| 🏠 **Overview** | Battery summary, findings, and top consumers |
| 🔒 **Wakelocks** | Held wakelocks with **Usual / Unusual** hints and plain-language descriptions |
| ⏰ **Alarms** | Apps that request device wake-ups |
| 📱 **Device** | System information, active command channel, and the latest shell error |
| 📄 **Report** | Copy or share a text report; set a baseline to compare later drain against |
| ℹ️ **About** | App information, links, and optional update alerts |

> **A hint, not a verdict:** “Unusual” is a heuristic to help you investigate. A single scan does not prove that an app is causing battery drain.

## 🚀 Get started

1. Install and start [Shizuku](https://shizuku.rikka.app). Wireless debugging works without root; Shizuku can also be started using root.
2. Open DrainScout and tap **Grant access**.
3. Tap **Scan** and review the findings.

Shizuku access is used to read system diagnostics. If a scan is incomplete, check the **Device** screen for the active command channel and any reported error.

## 🔍 How it works

DrainScout runs Android shell diagnostics through a Shizuku user service, including `dumpsys` and package queries. A reflection-based fallback is available if the user-service command path cannot be used. The app reports which channel is active and can flag when only some data was readable.

Wakelock labels and duration thresholds are **DrainScout heuristics**, not definitive attribution of battery use. See [How DrainScout judges wakelocks](docs/WAKELOCKS.md) for the reasoning and caveats.

## 🔐 Privacy

Diagnostics are analysed on your device. The only network request is the optional check for GitHub releases; update alerts can be disabled in **About**. Reports are shared only when you choose to copy or share them.

## 🧰 Technical details

- **Platform:** Android 8.0 (API 26) and newer
- **Language:** Kotlin
- **UI:** Native Android Views; no Compose or AndroidX UI toolkit
- **System access:** Shizuku
- **Build:** Gradle, JDK 17; compile/target SDK 34
- **APK:** R8 code shrinking and resource shrinking enabled for release builds
- **Design:** Lightweight interface with liquid-glass styling

## 🏗️ Build

Build a release APK with Gradle:

```bash
gradle :app:assembleRelease
```

To publish a release, push a version tag matching `v*`; GitHub Actions builds and publishes the APK. See the [Releases](https://github.com/stonecaper1-lab/DrainScout2/releases) page for published builds.

## 📚 More details

- [Wakelock classification and thresholds](docs/WAKELOCKS.md)
- [Release notes](RELEASE_NOTES.md)
- [Issues and feature requests](https://github.com/stonecaper1-lab/DrainScout2/issues)

---

<div align="center">

Made for curious Android users 🔎 · Small, focused, and ad-free

</div>
