package com.scrolla.device

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log

/**
 * OEM steps for keeping the tracking service alive, and one-tap ways to reach them
 * (PREMIUM_CHECKLIST P2.15).
 *
 * **Rewritten 2026-09-06 after the root cause was actually identified.** The old
 * version described *battery optimisation*, which is not what kills Scrolla. A
 * 22-hour log capture on the Xiaomi showed the process being killed 16 times by
 * MIUI's own cleaners — `LockScreenClean` ×6 (the app dies when the screen locks),
 * `camera boost` ×5, `OneKeyClean` ×2 (once at `adj 50`, a live foreground service,
 * with the FGS notification torn down as a consequence) and `lowmemorykiller` ×3.
 * Scrolla was on the AOSP battery whitelist and in standby bucket 5 (EXEMPTED)
 * throughout: those govern Doze and App Standby, and OEM cleaners sit outside them.
 *
 * So the steps below target **process killing**, and the step that actually stops
 * the cleaners — locking the app in Recents — is marked [WhitelistStep.critical].
 * It is the only one that cannot be launched, because it is a gesture in the
 * recents UI rather than a settings screen. Everything else is one tap.
 *
 * Old copy also said "Scroll" throughout, so users were told to look for an app
 * that is not in the list, and every step carried a "1. " prefix that the UI then
 * numbered again.
 */
class BatteryWhitelistHelper {

    /** A settings destination we can open directly on the user's behalf. */
    enum class StepAction {
        /** OEM autostart / auto-launch manager. Without it nothing restarts the service. */
        AUTOSTART,

        /** Per-app battery restriction. */
        BATTERY_UNRESTRICTED,

        /** This app's system settings page — the universal fallback. */
        APP_DETAILS,

        /** System accessibility list, to re-enable tracking. */
        ACCESSIBILITY
    }

    data class WhitelistStep(
        val text: String,
        /** Non-null when the step can be opened directly. */
        val action: StepAction? = null,
        val actionLabel: String? = null,
        /**
         * The step that actually stops this OEM's cleaners. Rendered prominently,
         * because a user who does only one of these should do this one.
         */
        val critical: Boolean = false
    )

    data class BatteryInstructions(
        val manufacturer: String,
        val title: String,
        val steps: List<WhitelistStep>,
        /** True for OEMs that kill foreground services regardless of whitelisting. */
        val isAggressive: Boolean = false
    )

    private val lockInRecents = WhitelistStep(
        text = "In Recents, swipe down on the Scrolla card (or long-press it) and tap the " +
            "padlock. This is the one step that stops the phone's cleaner from closing " +
            "Scrolla when you lock the screen or open the camera.",
        critical = true
    )

    private val MANUFACTURER_SPECIFIC = mapOf(
        "xiaomi" to BatteryInstructions(
            manufacturer = "Xiaomi",
            isAggressive = true,
            title = "Keep Scrolla running",
            steps = listOf(
                WhitelistStep(
                    text = "Turn on Autostart for Scrolla. Without it, nothing can restart " +
                        "tracking after the phone closes it.",
                    action = StepAction.AUTOSTART,
                    actionLabel = "Open Autostart"
                ),
                lockInRecents,
                WhitelistStep(
                    text = "Set Scrolla's battery saver to \"No restrictions\".",
                    action = StepAction.BATTERY_UNRESTRICTED,
                    actionLabel = "Open battery settings"
                ),
                WhitelistStep(
                    text = "In the Security app, under Permissions → Background restrictions, " +
                        "set Scrolla to \"No restrictions\".",
                    action = StepAction.APP_DETAILS,
                    actionLabel = "Open app settings"
                )
            )
        ),
        "poco" to BatteryInstructions(
            manufacturer = "POCO",
            isAggressive = true,
            title = "Keep Scrolla running",
            steps = listOf(
                WhitelistStep(
                    text = "Turn on Autostart for Scrolla.",
                    action = StepAction.AUTOSTART,
                    actionLabel = "Open Autostart"
                ),
                lockInRecents,
                WhitelistStep(
                    text = "Set Scrolla's battery saver to \"No restrictions\".",
                    action = StepAction.BATTERY_UNRESTRICTED,
                    actionLabel = "Open battery settings"
                )
            )
        ),
        "redmi" to BatteryInstructions(
            manufacturer = "Redmi",
            isAggressive = true,
            title = "Keep Scrolla running",
            steps = listOf(
                WhitelistStep(
                    text = "Turn on Autostart for Scrolla.",
                    action = StepAction.AUTOSTART,
                    actionLabel = "Open Autostart"
                ),
                lockInRecents,
                WhitelistStep(
                    text = "Set Scrolla's battery saver to \"No restrictions\".",
                    action = StepAction.BATTERY_UNRESTRICTED,
                    actionLabel = "Open battery settings"
                )
            )
        ),
        "samsung" to BatteryInstructions(
            manufacturer = "Samsung",
            isAggressive = true,
            title = "Keep Scrolla running",
            steps = listOf(
                WhitelistStep(
                    text = "Set Scrolla's battery usage to \"Unrestricted\".",
                    action = StepAction.BATTERY_UNRESTRICTED,
                    actionLabel = "Open battery settings"
                ),
                WhitelistStep(
                    text = "In Settings → Battery → Background usage limits, make sure Scrolla " +
                        "is NOT in \"Sleeping apps\" or \"Deep sleeping apps\", and add it to " +
                        "\"Never sleeping apps\".",
                    action = StepAction.APP_DETAILS,
                    actionLabel = "Open app settings",
                    critical = true
                ),
                WhitelistStep(
                    text = "Turn Adaptive battery off, or exempt Scrolla from it."
                )
            )
        ),
        "oppo" to BatteryInstructions(
            manufacturer = "OPPO",
            isAggressive = true,
            title = "Keep Scrolla running",
            steps = listOf(
                WhitelistStep(
                    text = "Allow Scrolla to auto-launch and to run in the background.",
                    action = StepAction.AUTOSTART,
                    actionLabel = "Open startup manager"
                ),
                lockInRecents,
                WhitelistStep(
                    text = "Set battery optimisation for Scrolla to \"Don't optimise\", and turn " +
                        "on \"Allow background activity\".",
                    action = StepAction.BATTERY_UNRESTRICTED,
                    actionLabel = "Open battery settings"
                )
            )
        ),
        "realme" to BatteryInstructions(
            manufacturer = "realme",
            isAggressive = true,
            title = "Keep Scrolla running",
            steps = listOf(
                WhitelistStep(
                    text = "Allow Scrolla to auto-launch and to run in the background.",
                    action = StepAction.AUTOSTART,
                    actionLabel = "Open startup manager"
                ),
                lockInRecents,
                WhitelistStep(
                    text = "Set battery optimisation for Scrolla to \"Don't optimise\".",
                    action = StepAction.BATTERY_UNRESTRICTED,
                    actionLabel = "Open battery settings"
                )
            )
        ),
        "oneplus" to BatteryInstructions(
            manufacturer = "OnePlus",
            isAggressive = true,
            title = "Keep Scrolla running",
            steps = listOf(
                WhitelistStep(
                    text = "Set battery optimisation for Scrolla to \"Don't optimise\".",
                    action = StepAction.BATTERY_UNRESTRICTED,
                    actionLabel = "Open battery settings"
                ),
                lockInRecents,
                WhitelistStep(
                    text = "In Settings → Battery → Advanced, turn off \"Deep optimisation\" and " +
                        "\"Sleep standby optimisation\" if present."
                )
            )
        ),
        "vivo" to BatteryInstructions(
            manufacturer = "vivo",
            isAggressive = true,
            title = "Keep Scrolla running",
            steps = listOf(
                WhitelistStep(
                    text = "Allow Scrolla to auto-start and to run in the background.",
                    action = StepAction.AUTOSTART,
                    actionLabel = "Open auto-start"
                ),
                lockInRecents,
                WhitelistStep(
                    text = "Set battery usage for Scrolla to allow background power consumption.",
                    action = StepAction.BATTERY_UNRESTRICTED,
                    actionLabel = "Open battery settings"
                )
            )
        ),
        "huawei" to BatteryInstructions(
            manufacturer = "Huawei",
            isAggressive = true,
            title = "Keep Scrolla running",
            steps = listOf(
                WhitelistStep(
                    text = "In Settings → Battery → App launch, switch Scrolla to \"Manage " +
                        "manually\" and turn on all three: Auto-launch, Secondary launch, and " +
                        "Run in background.",
                    action = StepAction.AUTOSTART,
                    actionLabel = "Open app launch",
                    critical = true
                ),
                lockInRecents,
                WhitelistStep(
                    text = "In Phone Manager, add Scrolla to Protected apps.",
                    action = StepAction.APP_DETAILS,
                    actionLabel = "Open app settings"
                )
            )
        ),
        "honor" to BatteryInstructions(
            manufacturer = "Honor",
            isAggressive = true,
            title = "Keep Scrolla running",
            steps = listOf(
                WhitelistStep(
                    text = "In Settings → Battery → App launch, switch Scrolla to \"Manage " +
                        "manually\" and turn on all three options.",
                    action = StepAction.AUTOSTART,
                    actionLabel = "Open app launch",
                    critical = true
                ),
                lockInRecents
            )
        )
    )

    private val GENERIC = BatteryInstructions(
        manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() },
        isAggressive = false,
        title = "Keep Scrolla running",
        steps = listOf(
            WhitelistStep(
                text = "Set Scrolla's battery usage to \"Unrestricted\", so the system doesn't " +
                    "stop it in the background.",
                action = StepAction.BATTERY_UNRESTRICTED,
                actionLabel = "Allow background use"
            ),
            WhitelistStep(
                text = "If your phone has a memory cleaner or a \"lock\" option in Recents, lock " +
                    "Scrolla there too."
            )
        )
    )

    /** Instructions for [manufacturer] (case-insensitive), or a generic fallback. */
    fun getInstructions(manufacturer: String): BatteryInstructions =
        MANUFACTURER_SPECIFIC[manufacturer.lowercase().trim()] ?: GENERIC

    /** True on OEMs known to kill foreground services regardless of whitelisting. */
    fun isAggressiveOem(manufacturer: String = Build.MANUFACTURER): Boolean =
        getInstructions(manufacturer).isAggressive

    /**
     * Opens the settings screen for [action], falling back through progressively more
     * generic destinations. Returns false only if even the app's own settings page
     * could not be opened.
     *
     * Every candidate is checked with `resolveActivity` before launching: OEM
     * component names differ between skin versions, and an unresolved explicit
     * component throws `ActivityNotFoundException` rather than failing quietly.
     */
    fun launch(context: Context, action: StepAction): Boolean {
        val candidates = when (action) {
            StepAction.AUTOSTART -> autostartIntents()
            StepAction.BATTERY_UNRESTRICTED -> batteryIntents(context)
            StepAction.APP_DETAILS -> emptyList()
            StepAction.ACCESSIBILITY -> listOf(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        for (intent in candidates) {
            if (tryStart(context, intent)) return true
        }
        // Universal fallback: the app's own settings page always exists, and every
        // OEM's per-app battery and background controls are reachable from it.
        return tryStart(context, appDetailsIntent(context))
    }

    /** Kept for existing call sites; opens the most relevant screen for this device. */
    fun openBatterySettings(context: Context): Boolean =
        launch(
            context,
            if (isAggressiveOem()) StepAction.AUTOSTART else StepAction.BATTERY_UNRESTRICTED
        )

    private fun autostartIntents(): List<Intent> = listOf(
        // MIUI / HyperOS
        explicit("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
        Intent("miui.intent.action.OP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT),
        Intent("miui.intent.action.INTENT_SETTINGS").apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            data = Uri.parse("app://settings/miui/autostart")
            setPackage("com.miui.securitycenter")
        },
        // ColorOS (OPPO / realme)
        explicit("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
        explicit("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
        explicit("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
        // FuntouchOS (vivo)
        explicit("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
        explicit("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
        // EMUI / MagicUI (Huawei / Honor)
        explicit("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
        explicit("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")
    )

    private fun batteryIntents(context: Context): List<Intent> = buildList {
        // Direct system dialog — one tap, no navigation. Requires the
        // REQUEST_IGNORE_BATTERY_OPTIMIZATIONS permission, declared in the manifest.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            add(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.parse("package:${context.packageName}"))
            )
        }
        // MIUI's per-app battery saver screen.
        add(
            explicit("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity").apply {
                putExtra("package_name", context.packageName)
                putExtra("package_label", "Scrolla")
            }
        )
        add(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }

    private fun explicit(pkg: String, cls: String) =
        Intent().setComponent(ComponentName(pkg, cls))

    private fun appDetailsIntent(context: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.parse("package:${context.packageName}"))

    private fun tryStart(context: Context, intent: Intent): Boolean {
        return try {
            if (context.packageManager.resolveActivity(intent, 0) == null) return false
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: Exception) {
            // Cascade to the next candidate rather than surfacing anything: a user
            // on this screen has no meaningful "retry" other than the next fallback.
            Log.w(TAG, "could not launch ${intent.component ?: intent.action}", e)
            false
        }
    }

    private companion object {
        const val TAG = "BatteryWhitelistHelper"
    }
}
