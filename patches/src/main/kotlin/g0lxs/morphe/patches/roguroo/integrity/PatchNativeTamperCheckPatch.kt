package g0lxs.morphe.patches.roguroo.integrity

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.rawResourcePatch
import g0lxs.morphe.patches.roguroo.shared.Constants.COMPATIBILITY_ROGUROO

// Internal (no name): applied automatically as a dependency of Enable Pro.
@Suppress("unused")
val patchNativeTamperCheckPatch = rawResourcePatch(
    description = "Neutralizes native APK signature verification in libArmArchNewEncrypt.so " +
        "so the re-signed (patched) build runs without crashing on startup. " +
        "Requires a merged/universal APK — split bundles (.apkm/.xapk/.apks) must " +
        "be merged first (e.g. with APKEditor or SAI).",
) {
    compatibleWith(COMPATIBILITY_ROGUROO)

    execute {
        // --- 1. Patch libArmArchNewEncrypt.so ---
        val encryptLibPath = "lib/arm64-v8a/libArmArchNewEncrypt.so"
        val encryptLib = get(encryptLibPath)
        if (!encryptLib.exists()) {
            throw PatchException(
                "$encryptLibPath not found in the APK. This patch requires a merged/universal " +
                    "APK (not a split .apkm/.xapk). Merge the split APK with APKEditor Studio, " +
                    "SAI, or AntiSplit before patching.",
            )
        }

        val encryptBytes = encryptLib.readBytes()

        // checkSig function entry point (offset 0xc60 in 6.6.2).
        // Unique prologue: stp x29,x30,[sp,#-64]! ; stp x24,x23,[sp,#16] ;
        //                  stp x22,x21,[sp,#32] ; stp x20,x19,[sp,#48] ;
        //                  mov x29,sp ; adrp x8,... ; adrp x24,...
        // Overwrite with: mov w0, #0 ; ret  (always return 0 = "signature OK" token path)
        val checkSigPrologue = byteArrayOf(
            0xfd.toByte(), 0x7b.toByte(), 0xbc.toByte(), 0xa9.toByte(),
            0xf8.toByte(), 0x5f.toByte(), 0x01.toByte(), 0xa9.toByte(),
            0xf6.toByte(), 0x57.toByte(), 0x02.toByte(), 0xa9.toByte(),
            0xf4.toByte(), 0x4f.toByte(), 0x03.toByte(), 0xa9.toByte(),
            0xfd.toByte(), 0x03.toByte(), 0x00.toByte(), 0x91.toByte(),
            0x28.toByte(), 0x00.toByte(), 0x00.toByte(), 0xb0.toByte(),
            0x38.toByte(), 0x00.toByte(), 0x00.toByte(), 0xb0.toByte(),
        )

        val match1 = encryptBytes.findUnique(checkSigPrologue, encryptLibPath)
            ?: throw PatchException(
                "checkSig prologue not found in $encryptLibPath — binary layout has changed.",
            )

        // Overwrite checkSig entry: mov w0, #0; ret
        val movW0Zero = byteArrayOf(
            0x00.toByte(), 0x00.toByte(), 0x80.toByte(), 0x52.toByte(), // mov w0, #0
            0xc0.toByte(), 0x03.toByte(), 0x5f.toByte(), 0xd6.toByte(), // ret
        )
        movW0Zero.forEachIndexed { i, b -> encryptBytes[match1 + i] = b }

        encryptLib.writeBytes(encryptBytes)

        // --- 2. Patch libdai.so (also contains signature hash constant 0x07a594f0) ---
        val daiLibPath = "lib/arm64-v8a/libdai.so"
        val daiLib = get(daiLibPath)
        if (daiLib.exists()) {
            val daiBytes = daiLib.readBytes()

            // The signature check function in libdai.so starts with the same prologue pattern
            // followed by different adrp operands. Find the signature hash constant
            // movz w9, 0x94f0 ; movk w9, 0x07a5, lsl #16
            // bytes: 09 9e 92 52  a9 f4 a0 72
            val sigHashConstant = byteArrayOf(
                0x09.toByte(), 0x9e.toByte(), 0x92.toByte(), 0x52.toByte(),
                0xa9.toByte(), 0xf4.toByte(), 0xa0.toByte(), 0x72.toByte(),
            )

            val hashMatch = daiBytes.findFirst(sigHashConstant)
            if (hashMatch != null) {
                // Walk backwards to find the function prologue (stp x29, x30, ...)
                var funcStart = hashMatch
                while (funcStart > hashMatch - 0x500 && funcStart >= 0) {
                    if (daiBytes[funcStart] == 0xfd.toByte() &&
                        daiBytes[funcStart + 1] == 0x7b.toByte() &&
                        daiBytes[funcStart + 2] == 0xbc.toByte() &&
                        daiBytes[funcStart + 3] == 0xa9.toByte()
                    ) {
                        // Found prologue — overwrite with mov w0, #0; ret
                        movW0Zero.forEachIndexed { i, b -> daiBytes[funcStart + i] = b }
                        break
                    }
                    funcStart -= 4
                }
                daiLib.writeBytes(daiBytes)
            }
        }

        // --- 3. Patch libVAVComposition.so (also contains signature verification) ---
        val vavLibPath = "lib/arm64-v8a/libVAVComposition.so"
        val vavLib = get(vavLibPath)
        if (vavLib.exists()) {
            val vavBytes = vavLib.readBytes()

            // libVAVComposition.so contains a signature check that compares against multiple
            // hash constants including 0x07a594f0 at:
            //   movz w8, 0x94f0, lsl #0 -> 08 9e 92 52
            //   movk w8, 0x07a5, lsl #16 -> a8 f4 a0 72
            val vavSigHash = byteArrayOf(
                0x08.toByte(), 0x9e.toByte(), 0x92.toByte(), 0x52.toByte(),
                0xa8.toByte(), 0xf4.toByte(), 0xa0.toByte(), 0x72.toByte(),
            )

            val vavMatch = vavBytes.findFirst(vavSigHash)
            if (vavMatch != null) {
                // Walk backwards to find function prologue (stp x29, x30, [sp, #-16]!)
                var funcStart = vavMatch
                while (funcStart > vavMatch - 0x500 && funcStart >= 4) {
                    if (vavBytes[funcStart] == 0xfd.toByte() &&
                        vavBytes[funcStart + 1] == 0x7b.toByte() &&
                        vavBytes[funcStart + 2] == 0xbf.toByte() &&
                        vavBytes[funcStart + 3] == 0xa9.toByte()
                    ) {
                        // Found prologue — overwrite with mov w0, #0; ret
                        movW0Zero.forEachIndexed { i, b -> vavBytes[funcStart + i] = b }
                        break
                    }
                    funcStart -= 4
                }
                vavLib.writeBytes(vavBytes)
            }
        }
    }
}

private fun ByteArray.findUnique(pattern: ByteArray, context: String): Int? {
    var found: Int? = null
    val last = size - pattern.size
    outer@ for (i in 0..last) {
        for (j in pattern.indices) {
            if (this[i + j] != pattern[j]) continue@outer
        }
        if (found != null) {
            throw PatchException("Pattern matched more than once in $context.")
        }
        found = i
    }
    return found
}

private fun ByteArray.findFirst(pattern: ByteArray): Int? {
    val last = size - pattern.size
    outer@ for (i in 0..last) {
        for (j in pattern.indices) {
            if (this[i + j] != pattern[j]) continue@outer
        }
        return i
    }
    return null
}
