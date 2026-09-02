# 🍃 Hooman's Morphe Patches GFork

Personal [Morphe](https://morphe.software) patches for paid Android apps.

> Patches are based on the prior work of [ReVanced](https://github.com/ReVanced).
## 🙏 Requesting Patches

**All** requests for patches **must** go under Patch Requests in the **[Discussions Tab](https://github.com/arandomhooman/hoomans-morphe-patches/discussions/new?category=new-patches)**.

## 🩹 Patches

<!-- PATCHES_START EXPANDED -->
> **[v1.4.0](https://github.com/g0lxs/hoomans-morphe-patches/releases/tag/v1.4.0)**&nbsp;&nbsp;•&nbsp;&nbsp;`main`&nbsp;&nbsp;•&nbsp;&nbsp;1 patches total
<details open>
<summary>📦 Finch&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**🎯 Supported versions:**

| 3.73.179 | 3.73.201 | 3.73.202 |
| :---: | :---: | :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Unlock Plus GFork](#unlock-plus-gfork) | Unlocks Finch Plus features without a subscription, including the Plus shop items, extra themes and customization, seasonal event tiers, the monthly recap, and Plus insights. It also clears the upgrade prompts. Cloud backup and cross-device sync run on Finch's own servers and still need the real subscription. Re-signing breaks Google sign-in, so log in with email instead. |  |

</details>

<!-- PATCHES_END -->

## 📥 How to install

The patches apply to the official app, which you supply yourself; this repo doesn't host or redistribute any app. For Finch:

1. **Add the patch source** to Morphe Manager once: `https://github.com/g0lxs/hoomans-morphe-patches`, or use the [deeplink](https://morphe.software/add-source?github=g0lxs/hoomans-morphe-patches).
2. **Get Finch's APK**: export it from the Play Store, or download it from APKMirror / APKCombo. Make sure to use the arm64 universal or merge split bundles (`.apks` / `.xapk`).
3. **Patch and install** in Morphe Manager with Unlock Plus GFork.

> Patching re-signs the app, so Google sign-in stops working on patched builds; log in with email or username instead. Cloud backup and sync run on Finch's own servers and still need a real subscription.

## 🛠️ Building

```bash
./gradlew buildAndroid
```

Produces a `.mpp` patch bundle under `patches/build/libs/`.

## 📋 License

[GPLv3](LICENSE).
