package g0lxs.morphe.patches.oldroll.integrity

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.rawResourcePatch
import g0lxs.morphe.patches.oldroll.shared.Constants.COMPATIBILITY_ROGUROO

// Internal (no name): applied automatically as a dependency of Enable Pro.
@Suppress("unused")
val patchNativeTamperCheckPatch = rawResourcePatch(
    description = "Neutralizes native APK signature verification in libArmArchNewEncrypt.so " +
        "so the re-signed (patched) build runs without crashing on startup.",
) {
    compatibleWith(COMPATIBILITY_ROGUROO)

    execute {
        val libPath = "lib/arm64-v8a/libArmArchNewEncrypt.so"
        val lib = get(libPath)
        if (!lib.exists()) {
            return@execute
        }

        val bytes = lib.readBytes()

        // 1. checkSig function entry point (0xc60)
        // Original prologue: stp x29, x30, [sp, #-64]! ... adrp x8, ... adrp x24, ...
        // We overwrite the first 12 bytes with:
        // movz w0, 0x226e (0x52844dc0)
        // movk w0, 0x08a8, lsl #16 (0x72a11500)
        // ret (0xd65f03c0)
        // This forces checkSig to immediately return the valid token 0x08a8226e without reading any signatures.
        val checkSigPrologue = byteArrayOf(
            0xfd.toByte(), 0x7b.toByte(), 0xbc.toByte(), 0xa9.toByte(),
            0xf8.toByte(), 0x5f.toByte(), 0x01.toByte(), 0xa9.toByte(),
            0xf6.toByte(), 0x57.toByte(), 0x02.toByte(), 0xa9.toByte(),
            0xf4.toByte(), 0x4f.toByte(), 0x03.toByte(), 0xa9.toByte(),
            0xfd.toByte(), 0x03.toByte(), 0x00.toByte(), 0x91.toByte(),
            0x28.toByte(), 0x00.toByte(), 0x00.toByte(), 0xb0.toByte(),
            0x38.toByte(), 0x00.toByte(), 0x00.toByte(), 0xb0.toByte(),
        )

        val match1 = bytes.findUnique(checkSigPrologue)
        if (match1 != null) {
            val returnValidToken = byteArrayOf(
                0xc0.toByte(), 0x4d.toByte(), 0x84.toByte(), 0x52.toByte(), // movz w0, 0x226e
                0x00.toByte(), 0x15.toByte(), 0xa1.toByte(), 0x72.toByte(), // movk w0, 0x08a8, lsl #16
                0xc0.toByte(), 0x03.toByte(), 0x5f.toByte(), 0xd6.toByte(), // ret
            )
            returnValidToken.forEachIndexed { i, b -> bytes[match1 + i] = b }
        }

        // 2. decryptPictureData checkSig branch check (0x13c4)
        // Original: BL checkSig; ldr x8, [x20, #80]; cbz w0, 0x1480
        // We overwrite cbz w0, 0x1480 with nop (1f 20 03 d5).
        val decryptCheckCall = byteArrayOf(
            0x63.toByte(), 0x01.toByte(), 0x00.toByte(), 0x94.toByte(),
            0x88.toByte(), 0x02.toByte(), 0x40.toByte(), 0xf9.toByte(),
            0xa0.toByte(), 0x05.toByte(), 0x00.toByte(), 0x34.toByte(),
        )

        val match2 = bytes.findUnique(decryptCheckCall)
        if (match2 != null) {
            val nop = byteArrayOf(0x1f.toByte(), 0x20.toByte(), 0x03.toByte(), 0xd5.toByte())
            nop.forEachIndexed { i, b -> bytes[match2 + 8 + i] = b }
        }

        if (match1 == null && match2 == null) {
            throw PatchException(
                "libArmArchNewEncrypt.so signature check not found — binary layout has changed.",
            )
        }

        lib.writeBytes(bytes)
    }
}

private fun ByteArray.findUnique(pattern: ByteArray): Int? {
    var found: Int? = null
    val last = size - pattern.size
    outer@ for (i in 0..last) {
        for (j in pattern.indices) {
            if (this[i + j] != pattern[j]) continue@outer
        }
        if (found != null) {
            throw PatchException("Pattern matched more than once in libArmArchNewEncrypt.so.")
        }
        found = i
    }
    return found
}
