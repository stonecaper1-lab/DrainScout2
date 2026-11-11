## 🔋 DrainScout v1.1.0

💎 **Liquid-glass redesign**, 📉 **smaller APK**, and 🔬 **per-app evidence** instead of raw dumps.

### ✨ New
- 🪟 Liquid-glass UI: frosted translucent cards, specular edge highlights, glass tab bar
- ⏱️ **Since-reference drain rate** (%/h): set a reference point, capture later, compare
- 📊 **Top consumers** by estimated mAh, resolved to app package names
- ⏰ **Wakelocks tab**: app wake locks held right now, plus kernel wakelocks
- 🔔 **Alarms tab**: wakeups ranked per app
- 😴 **Doze tab**: deep and light Doze state
- 🧭 Plain-language findings built from all of the above

### 📦 Smaller
- ✂️ R8 code shrinking and resource shrinking enabled
- 🌐 English-only resources, no dependency metadata in the APK

### 🔏 Signing
- 🔑 APKs are now signed with a fixed key, so future updates install over this one
- ⚠️ v1.0 used a throwaway key: **uninstall v1.0 once** before installing this

### 📝 Notes
- 🛡️ Needs Android 8.0+ and Shizuku running
- 🧪 `dumpsys` formats vary by vendor. If a tab is empty on your device, please open an issue with the model and Android version
