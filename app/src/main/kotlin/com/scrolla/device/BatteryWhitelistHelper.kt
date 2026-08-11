package com.scrolla.device

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

/**
 * Returns manufacturer-specific instructions for adding Scroll accessibility service to the battery whitelist.
 * Instructions are case-insensitive, matching Build.MANUFACTURER.
 */
class BatteryWhitelistHelper {

    data class BatteryInstructions(
        val manufacturer: String,
        val title: String,
        val steps: List<String>
    )

    private val MANUFACTURER_SPECIFIC = mapOf(
        "samsung" to BatteryInstructions(
            manufacturer = "Samsung",
            title = "Enable Scroll in Battery Optimization",
            steps = listOf(
                "1. Open Settings → Battery → Battery optimization",
                "2. Find 'Scroll' from the list of apps",
                "3. Select 'Scroll' and choose 'Don't optimize'",
                "4. Re-enable the Accessibility service if prompted"
            )
        ),
        "xiaomi" to BatteryInstructions(
            manufacturer = "Xiaomi",
            title = "Allow Autostart for Scroll",
            steps = listOf(
                "1. Open Settings → Apps → Autostart",
                "2. Find 'Scroll' app",
                "3. Enable 'Start in background'",
                "4. Exit Settings, then enable Accessibility service in 'Privacy & Security' section"
            )
        ),
        "oneplus" to BatteryInstructions(
            manufacturer = "OnePlus",
            title = "Whitelist Scroll in Do Not Disturb",
            steps = listOf(
                "1. Open Settings → Notifications & status bar",
                "2. Select 'App notifications' → 'Scroll'",
                "3. Enable 'Allow notifications' and 'Override Do Not Disturb'",
                "4. Also add Scroll to battery optimization exceptions"
            )
        ),
        "huawei" to BatteryInstructions(
            manufacturer = "Huawei",
            title = "Disable Battery Optimization for Scroll",
            steps = listOf(
                "1. Open Settings → Battery manager → Junk cleaner → Auto-start manager",
                "2. Find 'Scroll' app",
                "3. Allow it to start when system boots",
                "4. Return to Settings → Security → App permissions → Battery optimization, select 'Allow'",
                "5. Re-enable Accessibility service if prompted"
            )
        )
    )

    private val GENERIC = BatteryInstructions(
        manufacturer = "Generic",
        title = "Enable Scroll in Battery Optimization",
        steps = listOf(
            "1. Open Settings → Battery",
            "2. Select 'Battery optimization' (or 'Optimize battery usage')",
            "3. Find 'Scroll' from the list of apps",
            "4. Select 'Scroll' and choose 'Don't optimize' (or similar)",
            "5. Re-enable the Accessibility service if prompted"
        )
    )

    /**
     * Returns manufacturer-specific battery whitelist instructions.
     *
     * @param manufacturer The device manufacturer name (case-insensitive).
     * @return BatteryInstructions for the manufacturer or GENERIC fallback.
     */
    fun getInstructions(manufacturer: String): BatteryInstructions {
        val normalized = manufacturer.lowercase()
        return MANUFACTURER_SPECIFIC[normalized] ?: GENERIC
    }

    /**
     * Opens the appropriate battery settings intent for the current device.
     * Tries OEM-specific intent if available, falls back to generic Android battery settings.
     *
     * NOTE: Only Xiaomi has a dedicated OEM intent; Samsung, OnePlus, and Huawei fall back
     * to the generic battery optimization screen, which may not exactly match the
     * OEM-specific steps shown to the user. Revisit if this causes user confusion.
     *
     * @return true if an appropriate intent was launched, false if no matching intent found.
     */
    fun openBatterySettings(context: Context): Boolean {
        // Try OEM-specific intents based on manufacturer
        val manufacturer = Build.MANUFACTURER.lowercase()

        return when (manufacturer) {
            "xiaomi" -> launchXiaomiAutostart(context)
            else -> openGenericBatterySettings(context)
        }
    }

    private fun launchXiaomiAutostart(context: Context): Boolean {
        try {
            // Xiaomi's autostart settings
            val intent = Intent("miui.intent.action.INTENT_SETTINGS")
            intent.addCategory(Intent.CATEGORY_DEFAULT)
            intent.data = android.net.Uri.parse("app://settings/miui/autostart")
            intent.setPackage("com.miui.securitycenter")
            if (context.packageManager.resolveActivity(intent, 0) != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return true
            } else {
                // Fallback to generic battery settings for Xiaomi
                return openGenericBatterySettings(context)
            }
        } catch (e: Exception) {
            return openGenericBatterySettings(context)
        }
    }

    /**
     * Opens the generic Android battery optimization settings screen.
     */
    private fun openGenericBatterySettings(context: Context): Boolean {
        try {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return true
        } catch (e: Exception) {
            // Fallback to the most generic battery settings if the above fails
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = android.net.Uri.parse("package:" + context.packageName)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return true
            } catch (e2: Exception) {
                return false
            }
        }
    }
}