package hooman.morphe.patches.finch.plus

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.rawResourcePatch

private val FINCH_VERSIONS = listOf("3.73.179", "3.73.201", "3.73.202")

private class VersionSignatures(
    val version: String,
    val isUserSubscribedSig: ByteArray,
    val getStateSig: ByteArray,
)

private val versionSignatures = listOf(
    // Finch 3.73.202 (lib/arm64-v8a/libapp.so)
    VersionSignatures(
        version = "3.73.202",
        isUserSubscribedSig = byteArrayOf(
            0xfd.toByte(), 0x79.toByte(), 0xbf.toByte(), 0xa9.toByte(),
            0xfd.toByte(), 0x03.toByte(), 0x0f.toByte(), 0xaa.toByte(),
            0xef.toByte(), 0x81.toByte(), 0x00.toByte(), 0xd1.toByte(),
            0x50.toByte(), 0x27.toByte(), 0x40.toByte(), 0xf9.toByte(),
            0xff.toByte(), 0x01.toByte(), 0x10.toByte(), 0xeb.toByte(),
            0x29.toByte(), 0x0a.toByte(), 0x00.toByte(), 0x54.toByte(),
            0x40.toByte(), 0x3f.toByte(), 0x40.toByte(), 0xf9.toByte(),
            0x00.toByte(), 0x98.toByte(), 0x52.toByte(), 0xf9.toByte(),
            0x70.toByte(), 0x23.toByte(), 0x40.toByte(), 0xf9.toByte(),
            0x1f.toByte(), 0x00.toByte(), 0x10.toByte(), 0x6b.toByte(),
            0x61.toByte(), 0x00.toByte(), 0x00.toByte(), 0x54.toByte(),
            0x62.toByte(), 0x9b.toByte(), 0x7c.toByte(), 0xf9.toByte(),
            0x8c.toByte(), 0x79.toByte(), 0x58.toByte(), 0x94.toByte(),
            0x70.toByte(), 0x27.toByte(), 0x40.toByte(), 0x91.toByte(),
            0x10.toByte(), 0x4e.toByte(), 0x41.toByte(), 0xf9.toByte(),
        ),
        getStateSig = byteArrayOf(
            0xfd.toByte(), 0x79.toByte(), 0xbf.toByte(), 0xa9.toByte(),
            0xfd.toByte(), 0x03.toByte(), 0x0f.toByte(), 0xaa.toByte(),
            0xef.toByte(), 0x61.toByte(), 0x00.toByte(), 0xd1.toByte(),
            0x50.toByte(), 0x27.toByte(), 0x40.toByte(), 0xf9.toByte(),
            0xff.toByte(), 0x01.toByte(), 0x10.toByte(), 0xeb.toByte(),
            0x69.toByte(), 0x04.toByte(), 0x00.toByte(), 0x54.toByte(),
            0xc3.toByte(), 0xf7.toByte(), 0xfa.toByte(), 0x97.toByte(),
            0x01.toByte(), 0xf0.toByte(), 0x5f.toByte(), 0xf8.toByte(),
            0x21.toByte(), 0x7c.toByte(), 0x4c.toByte(), 0xd3.toByte(),
            0x70.toByte(), 0xe7.toByte(), 0x40.toByte(), 0x91.toByte(),
            0x10.toByte(), 0xbe.toByte(), 0x46.toByte(), 0xf9.toByte(),
        ),
    ),
    // Finch 3.73.201 (lib/arm64-v8a/libapp.so)
    VersionSignatures(
        version = "3.73.201",
        isUserSubscribedSig = byteArrayOf(
            0xfd.toByte(), 0x79.toByte(), 0xbf.toByte(), 0xa9.toByte(),
            0xfd.toByte(), 0x03.toByte(), 0x0f.toByte(), 0xaa.toByte(),
            0xef.toByte(), 0x81.toByte(), 0x00.toByte(), 0xd1.toByte(),
            0x50.toByte(), 0x27.toByte(), 0x40.toByte(), 0xf9.toByte(),
            0xff.toByte(), 0x01.toByte(), 0x10.toByte(), 0xeb.toByte(),
            0x29.toByte(), 0x0a.toByte(), 0x00.toByte(), 0x54.toByte(),
            0x40.toByte(), 0x3f.toByte(), 0x40.toByte(), 0xf9.toByte(),
            0x00.toByte(), 0x98.toByte(), 0x52.toByte(), 0xf9.toByte(),
            0x70.toByte(), 0x23.toByte(), 0x40.toByte(), 0xf9.toByte(),
            0x1f.toByte(), 0x00.toByte(), 0x10.toByte(), 0x6b.toByte(),
            0x61.toByte(), 0x00.toByte(), 0x00.toByte(), 0x54.toByte(),
            0x62.toByte(), 0x43.toByte(), 0x7c.toByte(), 0xf9.toByte(),
            0x73.toByte(), 0x0d.toByte(), 0x59.toByte(), 0x94.toByte(),
            0x70.toByte(), 0x27.toByte(), 0x40.toByte(), 0x91.toByte(),
            0x10.toByte(), 0xda.toByte(), 0x40.toByte(), 0xf9.toByte(),
        ),
        getStateSig = byteArrayOf(
            0xfd.toByte(), 0x79.toByte(), 0xbf.toByte(), 0xa9.toByte(),
            0xfd.toByte(), 0x03.toByte(), 0x0f.toByte(), 0xaa.toByte(),
            0xef.toByte(), 0x61.toByte(), 0x00.toByte(), 0xd1.toByte(),
            0x50.toByte(), 0x27.toByte(), 0x40.toByte(), 0xf9.toByte(),
            0xff.toByte(), 0x01.toByte(), 0x10.toByte(), 0xeb.toByte(),
            0x69.toByte(), 0x04.toByte(), 0x00.toByte(), 0x54.toByte(),
            0xdc.toByte(), 0xc5.toByte(), 0xfb.toByte(), 0x97.toByte(),
            0x01.toByte(), 0xf0.toByte(), 0x5f.toByte(), 0xf8.toByte(),
            0x21.toByte(), 0x7c.toByte(), 0x4c.toByte(), 0xd3.toByte(),
            0x70.toByte(), 0xdb.toByte(), 0x40.toByte(), 0x91.toByte(),
            0x10.toByte(), 0x42.toByte(), 0x44.toByte(), 0xf9.toByte(),
        ),
    ),
    // Finch 3.73.179 (lib/arm64-v8a/libapp.so)
    VersionSignatures(
        version = "3.73.179",
        isUserSubscribedSig = byteArrayOf(
            0xfd.toByte(), 0x79.toByte(), 0xbf.toByte(), 0xa9.toByte(),
            0xfd.toByte(), 0x03.toByte(), 0x0f.toByte(), 0xaa.toByte(),
            0xef.toByte(), 0x81.toByte(), 0x00.toByte(), 0xd1.toByte(),
            0x50.toByte(), 0x1f.toByte(), 0x40.toByte(), 0xf9.toByte(),
            0xff.toByte(), 0x01.toByte(), 0x10.toByte(), 0xeb.toByte(),
            0x49.toByte(), 0x0a.toByte(), 0x00.toByte(), 0x54.toByte(),
            0x40.toByte(), 0x37.toByte(), 0x40.toByte(), 0xf9.toByte(),
            0x00.toByte(), 0x3c.toByte(), 0x52.toByte(), 0xf9.toByte(),
            0x70.toByte(), 0x23.toByte(), 0x40.toByte(), 0xf9.toByte(),
            0x1f.toByte(), 0x00.toByte(), 0x10.toByte(), 0x6b.toByte(),
            0x81.toByte(), 0x00.toByte(), 0x00.toByte(), 0x54.toByte(),
            0x62.toByte(), 0x23.toByte(), 0x40.toByte(), 0x91.toByte(),
            0x42.toByte(), 0xe4.toByte(), 0x41.toByte(), 0xf9.toByte(),
            0xf0.toByte(), 0x0f.toByte(), 0x49.toByte(), 0x94.toByte(),
            0x70.toByte(), 0x23.toByte(), 0x40.toByte(), 0x91.toByte(),
            0x10.toByte(), 0x42.toByte(), 0x43.toByte(), 0xf9.toByte(),
        ),
        getStateSig = byteArrayOf(
            0xfd.toByte(), 0x79.toByte(), 0xbf.toByte(), 0xa9.toByte(),
            0xfd.toByte(), 0x03.toByte(), 0x0f.toByte(), 0xaa.toByte(),
            0xef.toByte(), 0x61.toByte(), 0x00.toByte(), 0xd1.toByte(),
            0x50.toByte(), 0x1f.toByte(), 0x40.toByte(), 0xf9.toByte(),
            0xff.toByte(), 0x01.toByte(), 0x10.toByte(), 0xeb.toByte(),
            0x89.toByte(), 0x04.toByte(), 0x00.toByte(), 0x54.toByte(),
            0x46.toByte(), 0x9c.toByte(), 0x05.toByte(), 0x94.toByte(),
            0x01.toByte(), 0xf0.toByte(), 0x5f.toByte(), 0xf8.toByte(),
            0x21.toByte(), 0x7c.toByte(), 0x4c.toByte(), 0xd3.toByte(),
            0x70.toByte(), 0x83.toByte(), 0x41.toByte(), 0x91.toByte(),
            0x10.toByte(), 0xa2.toByte(), 0x42.toByte(), 0xf9.toByte(),
        ),
    ),
)

// Patch for Finch (com.finch.finch), a Flutter/Dart AOT app.
// Two Dart methods are patched inside lib/arm64-v8a/libapp.so:
//
//   isUserSubscribed()         → always returns true (Dart boolean)
//   getUserSubscriptionState() → always returns the "yearly" tier string
@Suppress("unused")
val unlockPlusPatch = rawResourcePatch(
    name = "Unlock Plus",
    description = "Unlocks Finch Plus features without a subscription, including the Plus shop " +
        "items, extra themes and customization, seasonal event tiers, the monthly recap, and Plus " +
        "insights. It also clears the upgrade prompts. Cloud backup and cross-device sync run on " +
        "Finch's own servers and still need the real subscription. Re-signing breaks Google " +
        "sign-in, so log in with email instead.",
) {
    compatibleWith(
        Compatibility(
            name = "Finch",
            packageName = "com.finch.finch",
            appIconColor = 0xBFC2D0,
            targets = FINCH_VERSIONS.map(::AppTarget),
        ),
    )

    execute {
        val libPath = "lib/arm64-v8a/libapp.so"
        val lib = get(libPath)
        if (!lib.exists()) {
            throw PatchException(
                "$libPath not found in the APK. This targets the arm64 build; apk-pure often " +
                    "serves a v7a-only bundle. Apply this to a merged arm64 universal built from " +
                    "a Play Store .apks export, or merge split_config.arm64_v8a.apk with APKEditor.",
            )
        }

        val bytes = lib.readBytes()

        // "return true" at function entry. Overwrite starts at AllocStack (byte offset 8).
        // In Dart ARM64: true = NullReg(x22) + 0x20 — ABI-stable since x22 and the heap
        // offset of true are part of the Dart object-layout specification.
        val isUserSubscribedOverwrite = byteArrayOf(
            0xc0.toByte(), 0x82.toByte(), 0x00.toByte(), 0x91.toByte(), // add  x0, x22, #0x20  → true
            0xef.toByte(), 0x03.toByte(), 0x1d.toByte(), 0xaa.toByte(), // mov  x15, x29          LeaveFrame
            0xfd.toByte(), 0x79.toByte(), 0xc1.toByte(), 0xa8.toByte(), // ldp  x29, x30, [x15], #0x10
            0xc0.toByte(), 0x03.toByte(), 0x5f.toByte(), 0xd6.toByte(), // ret
        )

        // Find matching version signatures
        var matchedVersion: VersionSignatures? = null
        var isSubMatchPos: Int? = null
        var stateMatchPos: Int? = null

        for (ver in versionSignatures) {
            val isSub = bytes.findSequence(ver.isUserSubscribedSig)
            val state = bytes.findSequence(ver.getStateSig)
            if (isSub != null && state != null) {
                matchedVersion = ver
                isSubMatchPos = isSub
                stateMatchPos = state
                break
            }
        }

        if (matchedVersion == null || isSubMatchPos == null || stateMatchPos == null) {
            throw PatchException(
                "Finch Plus: Unsupported Finch version. Supported versions: " +
                    FINCH_VERSIONS.joinToString(", ") + ". Please use a supported APK.",
            )
        }

        // Patch isUserSubscribed → always return true
        isUserSubscribedOverwrite.forEachIndexed { i, b -> bytes[isSubMatchPos + 8 + i] = b }

        // Patch getUserSubscriptionState → always return "yearly".
        // Pool-load bytes are extracted dynamically from the function body.
        val getStateOverwrite = extractYearlyPoolLoad(bytes, stateMatchPos + matchedVersion.getStateSig.size)
            ?: throw PatchException(
                "Finch Plus: could not locate the 'yearly' pool-load return path in " +
                    "getUserSubscriptionState().",
            )
        getStateOverwrite.forEachIndexed { i, b -> bytes[stateMatchPos + 8 + i] = b }

        lib.writeBytes(bytes)
    }
}

/**
 * Scans up to 512 bytes from [scanStart] inside [bytes] for the pair
 *
 *   `add xR, x27, #N, lsl #12`  immediately followed by  `ldr x0, [xR, #N]`
 *
 * where the ldr loads into x0 and uses the add's destination register as base.
 * For each candidate, the distance (in instructions) to the nearest LeaveFrame+ret
 * sequence is measured; the candidate closest to a return is selected as the
 * "yearly" return path.
 *
 * Returns 20 bytes:
 *   [8 bytes: add+ldr extracted from the function body]
 *   [12 bytes: ABI-stable LeaveFrame+ret — mov x15,x29 / ldp x29,x30,[x15],#0x10 / ret]
 */
private fun extractYearlyPoolLoad(bytes: ByteArray, scanStart: Int): ByteArray? {
    // LeaveFrame + ret — ABI-stable in every Dart ARM64 build
    val leaveFrame = byteArrayOf(
        0xef.toByte(), 0x03.toByte(), 0x1d.toByte(), 0xaa.toByte(), // mov  x15, x29
        0xfd.toByte(), 0x79.toByte(), 0xc1.toByte(), 0xa8.toByte(), // ldp  x29, x30, [x15], #0x10
        0xc0.toByte(), 0x03.toByte(), 0x5f.toByte(), 0xd6.toByte(), // ret
    )

    data class Candidate(val pos: Int, val distToReturn: Int)
    val candidates = mutableListOf<Candidate>()

    val scanEnd = minOf(scanStart + 512, bytes.size - 20)
    var i = scanStart
    while (i <= scanEnd) {
        val a0 = bytes[i].toInt() and 0xFF
        val a1 = bytes[i + 1].toInt() and 0xFF
        val a2 = bytes[i + 2].toInt() and 0xFF
        val a3 = bytes[i + 3].toInt() and 0xFF

        // Detect: add xRd, x27, #N, lsl #12
        if (a3 == 0x91 && (a2 and 0x40) != 0 && (a1 and 0x03) == 0x03 && (a0 ushr 5) and 0x07 == 0x03) {
            val addRd = a0 and 0x1F
            val b0 = bytes[i + 4].toInt() and 0xFF
            val b1 = bytes[i + 5].toInt() and 0xFF
            val b3 = bytes[i + 7].toInt() and 0xFF

            // Detect: ldr x0, [xRn, #N]  (Rt = x0)
            if (b3 == 0xf9 && (b0 and 0x1F) == 0) {
                val ldrRn = ((b0 ushr 5) and 0x07) or ((b1 and 0x03) shl 3)
                if (ldrRn == addRd) {
                    // Measure distance to nearest LeaveFrame+ret (0 = immediately after ldr)
                    var distToRet = Int.MAX_VALUE
                    for (j in 0..6) {
                        val ri = i + 8 + j * 4
                        if (ri + leaveFrame.size <= bytes.size &&
                            leaveFrame.indices.all { k -> bytes[ri + k] == leaveFrame[k] }
                        ) {
                            distToRet = j
                            break
                        }
                    }
                    candidates.add(Candidate(i, distToRet))
                }
            }
        }
        i += 4 // ARM64 instructions are always 4 bytes
    }

    if (candidates.isEmpty()) return null
    val best = candidates.minByOrNull { it.distToReturn } ?: return null
    // 8 bytes from function body + 12 bytes stable teardown = 20-byte overwrite
    return bytes.slice(best.pos until best.pos + 8).toByteArray() + leaveFrame
}

/**
 * Returns the byte index in [this] where the exact sequence [pattern] matches, or null if absent.
 */
private fun ByteArray.findSequence(pattern: ByteArray): Int? {
    val last = size - pattern.size
    outer@ for (i in 0..last) {
        for (j in pattern.indices) {
            if (this[i + j] != pattern[j]) continue@outer
        }
        return i
    }
    return null
}
