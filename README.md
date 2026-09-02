# 👻 g0lxs's Morphe Patches

Personal [Morphe](https://morphe.software) patches by [g0lxs](https://github.com/g0lxs).

> Forked from [Hooman's Morphe Patches](https://github.com/arandomhooman/hoomans-morphe-patches).

> [!WARNING]
> **Disclaimer:** This project was written with the assistance of AI. Use at your own risk.

## 🩹 Patches

<!-- PATCHES_START EXPANDED -->
> **[v1.5.0](https://github.com/g0lxs/g0lxs-morphe-patches/releases/tag/v1.5.0)**&nbsp;&nbsp;•&nbsp;&nbsp;`main`&nbsp;&nbsp;•&nbsp;&nbsp;1 patches total
<details open>
<summary>📦 Finch&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**🎯 Supported versions:**

| 3.73.202 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Unlock Plus](#unlock-plus) | Unlocks Finch Plus features without a subscription, including the Plus shop items, extra themes and customization, seasonal event tiers, the monthly recap, and Plus insights. It also clears the upgrade prompts. Cloud backup and cross-device sync run on Finch's own servers and still need the real subscription. Re-signing breaks Google sign-in, so log in with email instead. |  |

</details>

<!-- PATCHES_END -->

## 📥 How to install

1. **Add the patch source** to Morphe Manager:
   - Source URL: `https://github.com/g0lxs/g0lxs-morphe-patches`
   - Or use the direct [deeplink](https://morphe.software/add-source?github=g0lxs/g0lxs-morphe-patches).
2. **Get Finch's APK**:
   - Make sure to use the supported version listed in the table above.
   - Download the ARM64 universal APK or split bundle (`.apkm` / `.xapk` / `.apks`) from APKMirror or export it from the Play Store.
3. **Patch and install**:
   - In Morphe Manager, select Finch, check the **Unlock Plus** patch, and install the patched APK.

> **Note:** Patching re-signs the APK with a custom key, which stops standard Google sign-in from working; log in with email directly in the app. Cloud backups and sync run on Finch's own servers and still require an official subscription.

## 🛠️ Building

To build the patch bundle locally:

```bash
./gradlew buildAndroid
```

The compiled `.mpp` patch bundle will be generated under `patches/build/libs/`.

## 📋 License

Distributed under the [GPLv3](LICENSE) license.
