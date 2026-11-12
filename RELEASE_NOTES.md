## 🔋 DrainScout v1.3.0

🧰 **Device info**, ℹ️ **About page**, and 🛡️ **reliability fixes**.

### 📱 New: Device tab
- 🧾 Model, codename, SoC, Android version, security patch, kernel
- ⚙️ CPU architecture, core count and max clocks
- 🧠 RAM and storage, with free space
- 🖥️ Resolution, density and supported refresh rates
- 🔋 Battery level, health, voltage, temperature, charge cycles (Android 14+), remaining charge and an estimated full capacity
- 💤 Uptime, CPU awake time and deep-sleep share
- 🛡️ Battery saver, Doze, sensors, and Shizuku status

### ℹ️ New: About tab
- ✍️ Version, signature, privacy summary and project links

### 🛠️ Reliability
- 🔎 If a capture returns nothing, the app now shows the actual reason instead of a generic message
- 🧱 Update alerts can no longer crash the app if scheduling fails
- 🔧 Shizuku command lookup no longer depends on one exact method signature
- 📦 Release builds are unminified again until a minified build is verified on a device
- 🧪 Wake lock parsing and verdict rules were run against sample `dumpsys` output

### 📝 Notes
- 🔑 Same signing key as v1.1.0 and v1.2.0, so it installs over them
- 🛡️ Needs Android 8.0+ and Shizuku running
