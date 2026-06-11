package montafra.beam

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

// Vendor ROMs kill foreground services and block boot receivers unless the user
// enables per-app toggles ("Autostart", "No battery restrictions") that have no
// manifest-permission equivalent. This table maps each known-aggressive vendor to
// the settings screens where those toggles live; component names vary between ROM
// versions, so candidates are tried in order.
data class VendorBatterySettings(
    val matchKeys: List<String>,
    val vendorLabel: String,
    val dontKillSlug: String,
    val promptOnFirstLaunch: Boolean,
    val intents: (packageName: String) -> List<Intent>,
)

object VendorBatteryHints {

    private fun component(pkg: String, cls: String) = Intent().setComponent(ComponentName(pkg, cls))

    private val coloros = listOf(
        component("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
        component("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
        component("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
    )

    private val huaweiIntents = listOf(
        component("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
        component("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"),
        component("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
    )

    private val vendors = listOf(
        VendorBatterySettings(
            matchKeys = listOf("xiaomi", "redmi", "poco"),
            vendorLabel = "MIUI / HyperOS",
            dontKillSlug = "xiaomi",
            promptOnFirstLaunch = true,
            intents = {
                listOf(
                    component("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
                    Intent("miui.intent.action.OP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT),
                )
            },
        ),
        VendorBatterySettings(
            matchKeys = listOf("huawei"),
            vendorLabel = "EMUI",
            dontKillSlug = "huawei",
            promptOnFirstLaunch = true,
            intents = { huaweiIntents },
        ),
        VendorBatterySettings(
            matchKeys = listOf("honor"),
            vendorLabel = "Magic UI",
            dontKillSlug = "huawei",
            promptOnFirstLaunch = true,
            intents = { huaweiIntents },
        ),
        VendorBatterySettings(
            matchKeys = listOf("oppo", "realme"),
            vendorLabel = "ColorOS",
            dontKillSlug = "oppo",
            promptOnFirstLaunch = true,
            intents = { coloros },
        ),
        VendorBatterySettings(
            matchKeys = listOf("vivo", "iqoo"),
            vendorLabel = "Funtouch OS / OriginOS",
            dontKillSlug = "vivo",
            promptOnFirstLaunch = true,
            intents = {
                listOf(
                    component("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
                    component("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
                    component("com.iqoo.secure", "com.iqoo.secure.safeguard.PurviewTabActivity"),
                )
            },
        ),
        VendorBatterySettings(
            matchKeys = listOf("meizu"),
            vendorLabel = "Flyme",
            dontKillSlug = "meizu",
            promptOnFirstLaunch = true,
            intents = { pkg ->
                listOf(
                    Intent("com.meizu.safe.security.SHOW_APPSEC")
                        .addCategory(Intent.CATEGORY_DEFAULT)
                        .putExtra("packageName", pkg),
                    component("com.meizu.safe", "com.meizu.safe.permission.SmartBGActivity"),
                )
            },
        ),
        VendorBatterySettings(
            matchKeys = listOf("oneplus"),
            vendorLabel = "OxygenOS",
            dontKillSlug = "oneplus",
            promptOnFirstLaunch = true,
            intents = {
                listOf(
                    component("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"),
                ) + coloros // OxygenOS 12+ is ColorOS-based
            },
        ),
        VendorBatterySettings(
            matchKeys = listOf("samsung"),
            vendorLabel = "One UI",
            dontKillSlug = "samsung",
            promptOnFirstLaunch = false,
            intents = {
                listOf(
                    component("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"),
                    component("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"),
                )
            },
        ),
        VendorBatterySettings(
            matchKeys = listOf("asus"),
            vendorLabel = "ZenUI",
            dontKillSlug = "asus",
            promptOnFirstLaunch = false,
            intents = {
                listOf(
                    component("com.asus.mobilemanager", "com.asus.mobilemanager.powersaver.PowerSaverSettings"),
                    component("com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutoStartActivity"),
                )
            },
        ),
    )

    val current: VendorBatterySettings? = run {
        val manufacturer = Build.MANUFACTURER.lowercase().trim()
        val brand = Build.BRAND.lowercase().trim()
        vendors.firstOrNull { v ->
            v.matchKeys.any { manufacturer.contains(it) || brand.contains(it) }
        }
    }

    private fun appDetailsIntent(context: Context) = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null),
    )

    // Vendor activities may be missing (ActivityNotFoundException) or exported but
    // permission-guarded (SecurityException), so each candidate is blind-tried.
    fun openVendorSettings(context: Context) {
        for (intent in current?.intents?.invoke(context.packageName).orEmpty()) {
            try {
                context.startActivity(intent)
                return
            } catch (_: Exception) {
            }
        }
        try {
            context.startActivity(appDetailsIntent(context))
        } catch (_: Exception) {
        }
    }

    fun dontKillUrl() = "https://dontkillmyapp.com/" + (current?.dontKillSlug ?: "")
}
