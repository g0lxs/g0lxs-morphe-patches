package hooman.morphe.patches.finch.plus

import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.rawResourcePatch

// Version-agnostic patch for Finch (com.finch.finch), a Flutter/Dart AOT app.
// Two Dart methods are patched inside lib/arm64-v8a/libapp.so:
//
//   isUserSubscribed()         → always returns true (Dart boolean)
//   getUserSubscriptionState() → always returns the "yearly" tier string
//
// SIGNATURE STRATEGY
// ------------------
// Functions are located with masked byte sequences (-1 = match any byte).
// Bytes matched exactly (stable across builds):
//   · EnterFrame (stp x29,x30 / mov x29,x15): identical in every Flutter function
//   · AllocStack instruction type (sub x15,x15,#N): opcode/registers stable, size wildcarded
//   · Stack-overflow check register pair: cmp x15, x16 and cmp w0, w16
//   · Dart ABI registers: x22 = null reg, x26 = THR, x27 = PP
//   · In getUserSubscriptionState: ubfx x1, x1, #0xc, #0x14 (encoding fully determined
//     by the Dart object-header bit positions, not by any pool slot)
//   · Destination registers for pool-page ADD / LDR instructions (x2, x16)
// Bytes wildcarded (change per version):
//   · Branch immediate offsets
//   · Object-pool slot offsets (PP-relative)
//   · THR field offsets
//   · AllocStack frame size
//
// OVERWRITE STRATEGY
// ------------------
// isUserSubscribed: "return true" uses `add x0, x22, #0x20` (Dart's ABI-stable encoding
//   of boolean true — x22 is NullReg, offset 0x20 is fixed in the Dart heap layout)
//   + standard LeaveFrame + ret. All bytes are stable across all Dart versions.
//
// getUserSubscriptionState: the pool-load bytes for "yearly" change every build, so
//   extractYearlyPoolLoad() scans the function body for the add+ldr pair that loads a
//   PP object into x0 closest to a return sequence and copies those 8 bytes. The
//   LeaveFrame+ret appended after are ABI-stable.
//
// If a major Dart or Finch refactor breaks a signature, run Blutter on the new
// libapp.so and update the masked pattern for the affected function.
@Suppress("unused")
val unlockPlusPatch = rawResourcePatch(
    name = "Unlock Plus GFork",
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

        // ── isUserSubscribed() ───────────────────────────────────────────────────
        // Uniquely identified among all FinchSettingsManager getters by: field-table
        // load into x0 → sentinel compare (cmp w0, w16) → InitLateFinalStaticField
        // stub call with x2 as the field register (other getters use x16) →
        // <bool?> type-args pool load into x16.
        //
        // Encoding reference for byte0 stability (Dart ABI: x26=THR, x27=PP):
        //   ldr x16, [x26, #N] → byte0 = 0x50  (Rn[2:0]=010, Rt=x16=10000)
        //   ldr x0,  [x26, #N] → byte0 = 0x40  (Rn[2:0]=010, Rt=x0 =00000)
        //   ldr x0,  [x0,  #N] → byte0 = 0x00  (Rn[2:0]=000, Rt=x0 =00000)
        //   ldr x16, [x27, #N] → byte0 = 0x70  (Rn[2:0]=011, Rt=x16=10000)
        //   add x2,  x27, #N   → byte0 = 0x62  (Rn[2:0]=011, Rd=x2 =00010)
        //   ldr x2,  [x2,  #N] → byte0 = 0x42  (Rn[2:0]=010, Rt=x2 =00010)
        //   add x16, x27, #N   → byte0 = 0x70  (Rn[2:0]=011, Rd=x16=10000)
        //   ldr x16, [x16, #N] → byte0 = 0x10  (Rn[2:0]=000, Rt=x16=10000)
        val isUserSubscribedSig = intArrayOf(
            0xfd, 0x79, 0xbf, 0xa9,     // stp  x29, x30, [x15, #-0x10]!   EnterFrame
            0xfd, 0x03, 0x0f, 0xaa,     // mov  x29, x15
            0xef,  -1,  0x00, 0xd1,     // sub  x15, x15, #N                AllocStack (size wildcarded)
            0x50,  -1,   -1,  0xf9,     // ldr  x16, [x26, #N]              THR::stack_limit → x16
            0xff, 0x01, 0x10, 0xeb,     // cmp  x15, x16                    (register pair stable)
             -1,   -1,   -1,  0x54,     // b.ls #N
            0x40,  -1,   -1,  0xf9,     // ldr  x0,  [x26, #N]              field_table_values → x0
            0x00,  -1,   -1,  0xf9,     // ldr  x0,  [x0,  #N]              field slot → x0
            0x70,  -1,   -1,  0xf9,     // ldr  x16, [x27, #N]              PP::Sentinel → x16
            0x1f, 0x00, 0x10, 0x6b,     // cmp  w0, w16                     (register pair stable)
             -1,   -1,   -1,  0x54,     // b.ne #N
            0x62,  -1,   -1,  0x91,     // add  x2,  x27, #N, lsl #12       finchSettingsManager (x2 distinguishes)
            0x42,  -1,   -1,  0xf9,     // ldr  x2,  [x2,  #N]
             -1,   -1,   -1,   -1,      // bl   InitLateFinalStaticFieldStub (all offset bytes vary)
            0x70,  -1,   -1,  0x91,     // add  x16, x27, #N, lsl #12       <bool?> type-args pool page
            0x10,  -1,   -1,  0xf9,     // ldr  x16, [x16, #N]              <bool?> type args (unique to this getter)
        )

        // "return true" at function entry. Overwrite starts at AllocStack (byte offset 8).
        // In Dart ARM64: true = NullReg(x22) + 0x20 — ABI-stable since x22 and the heap
        // offset of true are part of the Dart object-layout specification.
        val isUserSubscribedOverwrite = byteArrayOf(
            0xc0.toByte(), 0x82.toByte(), 0x00.toByte(), 0x91.toByte(), // add  x0, x22, #0x20  → true
            0xef.toByte(), 0x03.toByte(), 0x1d.toByte(), 0xaa.toByte(), // mov  x15, x29          LeaveFrame
            0xfd.toByte(), 0x79.toByte(), 0xc1.toByte(), 0xa8.toByte(), // ldp  x29, x30, [x15], #0x10
            0xc0.toByte(), 0x03.toByte(), 0x5f.toByte(), 0xd6.toByte(), // ret
        )

        // ── getUserSubscriptionState() ───────────────────────────────────────────
        // Uniquely identified by `ubfx x1, x1, #0xc, #0x14` which extracts the Dart
        // class tag from bits[31:12] of the object header. This 4-byte encoding is fully
        // determined by the bit positions alone (not by any pool slot), making it stable
        // across all Dart versions. The bl to getAccountId early in the function and the
        // two-instruction pool load for the settings key "8LLJTDVPH1" together anchor the
        // match to this specific function.
        val getStateSig = intArrayOf(
            0xfd, 0x79, 0xbf, 0xa9,     // stp  x29, x30, [x15, #-0x10]!
            0xfd, 0x03, 0x0f, 0xaa,     // mov  x29, x15
            0xef,  -1,  0x00, 0xd1,     // sub  x15, x15, #N
            0x50,  -1,   -1,  0xf9,     // ldr  x16, [x26, #N]
            0xff, 0x01, 0x10, 0xeb,     // cmp  x15, x16
             -1,   -1,   -1,  0x54,     // b.ls #N
             -1,   -1,   -1,   -1,      // bl   getAccountId (all offset bytes vary)
            0x01,  -1,   -1,  0xf8,     // ldur x1, [x0, #-1]               Rt=x1, LDUR opcode
            0x21, 0x7c, 0x4c, 0xd3,     // ubfx x1, x1, #0xc, #0x14         ABI-stable class-tag extract
            0x70,  -1,   -1,  0x91,     // add  x16, x27, #N, lsl #12
            0x10,  -1,   -1,  0xf9,     // ldr  x16, [x16, #N]              "8LLJTDVPH1" settings key
        )

        // ── Find and patch both functions ────────────────────────────────────────
        val isSubMatchPos = bytes.findUniqueMasked(isUserSubscribedSig)
            ?: throw PatchException(
                "Finch Plus: isUserSubscribed() signature not found in $libPath. " +
                    "Use Blutter on the new libapp.so to re-derive the masked signature.",
            )
        val stateMatchPos = bytes.findUniqueMasked(getStateSig)
            ?: throw PatchException(
                "Finch Plus: getUserSubscriptionState() signature not found in $libPath. " +
                    "Use Blutter on the new libapp.so to re-derive the masked signature.",
            )

        // Patch isUserSubscribed → always return true
        isUserSubscribedOverwrite.forEachIndexed { i, b -> bytes[isSubMatchPos + 8 + i] = b }

        // Patch getUserSubscriptionState → always return "yearly".
        // Pool-load bytes are extracted dynamically from the function body.
        val getStateOverwrite = extractYearlyPoolLoad(bytes, stateMatchPos + getStateSig.size)
            ?: throw PatchException(
                "Finch Plus: could not locate the 'yearly' pool-load return path in " +
                    "getUserSubscriptionState(). Use Blutter to identify the pool slot and " +
                    "supply the overwrite bytes manually.",
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
 *
 * Ready to be written at offset 8 in getUserSubscriptionState(). Returns null if
 * no matching candidate is found within the scan window.
 *
 * ARM64 encoding facts used in the detection (little-endian, 4 bytes/instruction):
 *   `add xRd, x27, #N, lsl #12`
 *     byte3 = 0x91          ADD 64-bit, no flags
 *     bit22 = 1 (lsl#12)  → (byte2 & 0x40) != 0
 *     Rn = x27 = 11011b   → (byte1 & 0x03) == 0x03  (Rn[4:3] = 11)
 *                          → (byte0 ushr 5) & 7 == 3  (Rn[2:0] = 011)
 *     addRd = byte0 & 0x1F
 *   `ldr x0, [xRn, #N]`
 *     byte3 = 0xf9          LDR 64-bit unsigned offset
 *     Rt = x0 = 0         → (byte0 & 0x1F) == 0
 *     ldrRn = ((byte0 ushr 5) & 7) or ((byte1 & 3) shl 3)
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
 * Returns the single byte index in [this] where [pattern] matches, or null if absent.
 * Pattern values: 0..255 = exact byte match, -1 = wildcard (match any byte).
 * Throws [PatchException] when the pattern matches at more than one location —
 * an ambiguous signature is too risky to use for a binary overwrite.
 */
private fun ByteArray.findUniqueMasked(pattern: IntArray): Int? {
    var found: Int? = null
    val last = size - pattern.size
    outer@ for (i in 0..last) {
        for (j in pattern.indices) {
            val p = pattern[j]
            if (p != -1 && (this[i + j].toInt() and 0xFF) != p) continue@outer
        }
        if (found != null) throw PatchException(
            "Finch Plus: signature matched at multiple locations — too ambiguous to apply safely.",
        )
        found = i
    }
    return found
}
