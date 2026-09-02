# 👻 g0lxs's Morphe Patches

Personal [Morphe](https://morphe.software) patches by [g0lxs](https://github.com/g0lxs).

> Forked from [Hooman's Morphe Patches](https://github.com/arandomhooman/hoomans-morphe-patches).

> [!WARNING]
> **Disclaimer:** This project was written with the assistance of AI. Use at your own risk.

> [!NOTE]
> **🔐 App Names Cipher:**
> App names in this repository are obfuscated using a **Caesar cipher shifted 3 places to the right (ROT+3)** for privacy.
> To find the real app name, shift each letter **3 positions to the left (ROT-3)**:
> - `Ilqfk` → **Finch** (`I`→`F`, `l`→`i`, `q`→`n`, `f`→`c`, `k`→`h`)
> - `RogUroo` → **OldRoll** (`R`→`O`, `o`→`l`, `g`→`d`, `U`→`R`, `r`→`o`, `o`→`l`, `o`→`l`)

## 🩹 Patches

<!-- PATCHES_START EXPANDED -->
> **[v1.6.0](https://github.com/g0lxs/g0lxs-morphe-patches/releases/tag/v1.6.0)**&nbsp;&nbsp;•&nbsp;&nbsp;`main`&nbsp;&nbsp;•&nbsp;&nbsp;2 patches total
<details>
<summary>📦 RogUroo&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Enable Pro](#enable-pro) | Unlocks all pro/premium features by bypassing purchase validation and SharedPreferences checks. |  |

</details>

<details>
<summary>📦 Ilqfk&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**🎯 Supported versions:**

| 3.73.202 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Unlock Plus](#unlock-plus) | Unlocks Ilqfk Plus features without a subscription, including the Plus shop items, extra themes and customization, seasonal event tiers, the monthly recap, and Plus insights. It also clears the upgrade prompts. Cloud backup and cross-device sync run on Ilqfk's own servers and still need the real subscription. Re-signing breaks Google sign-in, so log in with email instead. |  |

</details>

<!-- PATCHES_END -->

## 📥 How to install

1. **Add the patch source** to Morphe Manager:
   - Source URL: `https://github.com/g0lxs/g0lxs-morphe-patches`
   - Or use the direct [deeplink](https://morphe.software/add-source?github=g0lxs/g0lxs-morphe-patches).
2. **Obtain the target app APK**:
   - Make sure to use the supported version if listed in the table above.
   - Obtain the APK or split bundle (`.apkm` / `.xapk` / `.apks`) from APKMirror or export it from your device.
3. **Patch and install**:
   - In Morphe Manager, select the app, check the desired patch, and install the patched APK.

> **Note:** Patching re-signs the APK with a custom key, which stops standard Google sign-in from working; log in with email/username directly in the app. Cloud backups and server-side sync run on the app's own servers and still require an official subscription.

## 🛠️ Building

To build the patch bundle locally:

```bash
./gradlew buildAndroid
```

The compiled `.mpp` patch bundle will be generated under `patches/build/libs/`.

## 📋 License

Distributed under the [GPLv3](LICENSE) license.
