package g0lxs.morphe.patches.roguroo.premium

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import g0lxs.morphe.patches.roguroo.integrity.patchNativeTamperCheckPatch
import g0lxs.morphe.patches.roguroo.shared.Constants.COMPATIBILITY_ROGUROO

private const val TRUE_RETURN = """
    const/4 v0, 0x1
    return v0
"""

private const val RETURN_CURRENT_TIME = """
    invoke-static {}, Ljava/lang/System;->currentTimeMillis()J
    move-result-wide v0
    return-wide v0
"""

@Suppress("unused")
val enableRogUrooProPatch = bytecodePatch(
    name = "Enable Pro",
    description = "Unlocks all pro/premium features by bypassing purchase validation and SharedPreferences checks.",
    default = true,
) {
    dependsOn(patchNativeTamperCheckPatch)
    compatibleWith(COMPATIBILITY_ROGUROO)

    execute {
        PurchaseSharedPrefManagerIsSkuPurchasedFingerprint.method.addInstructions(0, TRUE_RETURN)
        PurchaseSharedPrefManagerGetProMonthPurchaseTimeFingerprint.method.addInstructions(0, RETURN_CURRENT_TIME)
        PurchaseSharedPrefManagerGetProYearPurchaseTimeFingerprint.method.addInstructions(0, RETURN_CURRENT_TIME)
    }
}
