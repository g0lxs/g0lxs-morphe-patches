# 🍃 g0lxs's Morphe Patches

Personal [Morphe](https://morphe.software) patches by [g0lxs](https://github.com/g0lxs).

> Forked from [Hooman's Morphe Patches](https://github.com/arandomhooman/hoomans-morphe-patches).

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
| [Unlock Plus](#unlock-plus) | Unlocks Finch Plus features without a subscription, including the Plus shop items, extra themes and customization, seasonal event tiers, the monthly recap, and Plus insights. It also clears the upgrade prompts. Cloud backup and cross-device sync run on Finch's own servers and still need the real subscription. Re-signing breaks Google sign-in, so log in with email instead. |  |

</details>

<!-- PATCHES_END -->

## 📥 Como instalar / How to install

1. **Adicione a fonte no Morphe Manager:**
   - Adicione o repositório: `https://github.com/g0lxs/hoomans-morphe-patches`
   - Ou utilize o [deeplink direto](https://morphe.software/add-source?github=g0lxs/hoomans-morphe-patches).
2. **Obtenha o APK do Finch:**
   - Utilize uma das versões suportadas listadas na tabela acima.
   - Baixe a versão universal ARM64 ou o pacote split (`.apkm` / `.xapk` / `.apks`) pelo APKMirror ou exportando da Play Store.
3. **Selecione e aplique o patch:**
   - No Morphe Manager, selecione o app, marque o patch **Unlock Plus** e instale a versão modificada.

> **Nota:** A modificação re-assina o APK com uma chave local, portanto o login padrão com conta Google para de funcionar; utilize login por e-mail diretamente no aplicativo. Backups e sincronização na nuvem dependem dos servidores do Finch e não são cobertos.

## 🛠️ Build

Para compilar o bundle de patches localmente:

```bash
./gradlew buildAndroid
```

O arquivo `.mpp` compilado será gerado em `patches/build/libs/`.

## 📋 Licença

Distribuído sob a licença [GPLv3](LICENSE).
