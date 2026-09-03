package g0lxs.morphe.patches.roguroo.integrity

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.rawResourcePatch
import g0lxs.morphe.patches.roguroo.shared.Constants.COMPATIBILITY_ROGUROO

// Internal (no name): applied automatically as a dependency of Enable Pro.
@Suppress("unused")
val patchNativeTamperCheckPatch = rawResourcePatch(
    description = "Neutralizes native APK signature verification in three native libraries " +
        "(libArmArchNewEncrypt.so, libdai.so, libVAVComposition.so) so the re-signed (patched) " +
        "build runs without crashing on startup. Requires a merged/universal APK — split " +
        "bundles (.apkm/.xapk/.apks) must be merged first (e.g. with APKEditor or AntiSplit-M).",
) {
    compatibleWith(COMPATIBILITY_ROGUROO)

    execute {
        // The app's native libraries verify the APK signing certificate via JNI:
        //   ActivityThread.currentActivityThread().getApplication()
        //     .getPackageManager().getPackageInfo(name, 64).signatures[0].hashCode()
        // The computed hash is compared against a list of ~12 known-good hashes
        // (one per signing key the developer has used). If none matches, the function
        // returns 0 (failure). The callers treat w0==0 as "tampered" and skip critical
        // functionality, causing the app to crash or freeze.
        //
        // Fix: overwrite the checkSig prologue with `mov w0, #1; ret` so it always
        // returns non-zero (= "signature valid"). Returning 1 instead of 0 is critical:
        // the caller uses `cbz w0, <skip>` so returning 0 would SKIP decryption.

        val libsToCheck = listOf(
            "lib/arm64-v8a/libArmArchNewEncrypt.so",
            "lib/arm64-v8a/libdai.so",
            "lib/arm64-v8a/libVAVComposition.so",
        )

        // mov w0, #1; ret — always return "signature OK"
        val bypassStub = byteArrayOf(
            0x20.toByte(), 0x00.toByte(), 0x80.toByte(), 0x52.toByte(), // mov w0, #1
            0xc0.toByte(), 0x03.toByte(), 0x5f.toByte(), 0xd6.toByte(), // ret
        )

        // The signature-check function in every library compares the computed hashCode()
        // against a chain of known hashes using: movz w9, #imm16; movk w9, #imm16, lsl#16;
        // CMP w0, w9; B.EQ <accept>; ... repeated for each hash.
        //
        // The unique anchor: the app's signing cert hash 0x07a594f0 always appears as
        //   movz w?, #0x94f0 (52 92 9e 09/08)  followed by  movk w?, #0x07a5, lsl #16
        // This 8-byte sequence is unique in each library.
        //
        // Strategy: find this anchor, walk backwards to the function prologue
        // (stp x29, x30, [sp, #-N]!), and overwrite it with the bypass stub.

        var primaryLibFound = false

        for (libPath in libsToCheck) {
            val lib = get(libPath)
            if (!lib.exists()) {
                if (libPath.contains("ArmArchNewEncrypt")) {
                    throw PatchException(
                        "$libPath not found in the APK. This patch requires a merged/universal " +
                            "APK (not a split .apkm/.xapk). Merge the split APK with APKEditor " +
                            "Studio, SAI, or AntiSplit-M before patching.",
                    )
                }
                continue
            }

            val bytes = lib.readBytes()

            // Find movz w?, #0x94f0 (any destination register)
            val hashAnchor = findHashConstant(bytes, 0x94f0, 0x07a5)
            if (hashAnchor == null) {
                // Not all libs may have the check in every version
                continue
            }

            // Walk backwards to find the function prologue: stp x29, x30, [sp, #-N]!
            // This is encoded as A9xx7BFD where bit 15 is set (pre-index writeback).
            val funcStart = findPrologueBackwards(bytes, hashAnchor, maxDistance = 0x800)
                ?: throw PatchException(
                    "Could not find function prologue in $libPath before hash constant at " +
                        "0x${hashAnchor.toString(16)}. Binary layout has changed.",
                )

            // Overwrite function entry with bypass stub
            bypassStub.forEachIndexed { i, b -> bytes[funcStart + i] = b }
            lib.writeBytes(bytes)

            if (libPath.contains("ArmArchNewEncrypt")) {
                primaryLibFound = true
            }
        }

        if (!primaryLibFound) {
            throw PatchException(
                "libArmArchNewEncrypt.so signature check pattern not found — binary layout has changed.",
            )
        }
    }
}

/**
 * Finds `movz wR, #[low16]` immediately followed by `movk wR, #[high16], lsl #16`
 * where R can be any register. Returns the byte offset of the movz instruction,
 * or null if not found.
 */
private fun findHashConstant(bytes: ByteArray, low16: Int, high16: Int): Int? {
    val last = bytes.size - 8
    for (i in 0..last step 4) {
        val w1 = bytes.readIntLE(i)
        // MOVZ Wd, #imm16, lsl #0: 0101_0010_100x_xxxx_xxxx_xxxx_xxxR_RRRR
        if (w1 and 0x7FE00000.toInt() != 0x52800000) continue
        val imm1 = (w1 ushr 5) and 0xFFFF
        if (imm1 != low16) continue
        val rd1 = w1 and 0x1F

        val w2 = bytes.readIntLE(i + 4)
        // MOVK Wd, #imm16, lsl #16: 0111_0010_101x_xxxx_xxxx_xxxx_xxxR_RRRR
        if (w2 and 0x7FE00000.toInt() != 0x72A00000.toInt()) continue
        val imm2 = (w2 ushr 5) and 0xFFFF
        if (imm2 != high16) continue
        val rd2 = w2 and 0x1F
        if (rd1 != rd2) continue

        return i
    }
    return null
}

/**
 * Walks backwards from [startOffset] looking for `stp x29, x30, [sp, #-N]!`
 * (pre-indexed store pair with frame-pointer setup). Returns the byte offset
 * of the prologue instruction, or null if not found within [maxDistance] bytes.
 */
private fun findPrologueBackwards(bytes: ByteArray, startOffset: Int, maxDistance: Int): Int? {
    var off = startOffset
    val minOff = maxOf(0, startOffset - maxDistance)
    while (off >= minOff) {
        val w = bytes.readIntLE(off)
        // stp x29, x30, [sp, #imm7]! — pre-index writeback
        // Encoding: 1x101001 1xxxxxxx 01111011 11111101
        // Mask:     FF80 7FFF == A980 7BFD (x64-bit, pre-index, Rt2=x30, Rn=sp, Rt=x29)
        if (w and 0xFF807FFF.toInt() == 0xA9807BFD.toInt()) {
            return off
        }
        off -= 4
    }
    return null
}

private fun ByteArray.readIntLE(offset: Int): Int =
    (this[offset].toInt() and 0xFF) or
        ((this[offset + 1].toInt() and 0xFF) shl 8) or
        ((this[offset + 2].toInt() and 0xFF) shl 16) or
        ((this[offset + 3].toInt() and 0xFF) shl 24)
