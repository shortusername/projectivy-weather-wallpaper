package tv.projectivy.plugin.wallpaperprovider.weather

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.leanback.app.GuidedStepSupportFragment
import tv.projectivy.plugin.wallpaperprovider.api.WallpaperProviderContract

class SettingsActivity : FragmentActivity() {

    companion object {
        private const val PROJECTIVY_PACKAGE_ID = "com.spocky.projengmenu"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!packageManager.isApplicationInstalled(PROJECTIVY_PACKAGE_ID)) {
            Toast.makeText(this, R.string.projectivy_not_installed, Toast.LENGTH_LONG).show()
        }

        if (savedInstanceState == null) {
            GuidedStepSupportFragment.addAsRoot(this, SettingsFragment(), android.R.id.content)
        }

        // Lets you seed settings over adb without touching the on-screen keyboard:
        //   adb shell am start -n <appId>/.SettingsActivity \
        //     --es latitude "41.157" --es longitude "-73.862" \
        //     --es placeLabel "Ossining" --ez useMetric false --ez close true
        handleIntentExtras(intent)
    }

    private fun handleIntentExtras(intent: Intent?) {
        val extras = intent?.extras ?: return
        PreferencesManager.init(this)

        var changed = false
        extras.getString("latitude")?.toDoubleOrNull()?.let {
            PreferencesManager.latitude = it; changed = true
        }
        extras.getString("longitude")?.toDoubleOrNull()?.let {
            PreferencesManager.longitude = it; changed = true
        }
        extras.getString("placeLabel")?.let {
            PreferencesManager.placeLabel = it; changed = true
        }
        if (extras.containsKey("useMetric")) {
            PreferencesManager.useMetric = extras.getBoolean("useMetric")
            changed = true
        }

        if (changed) requestWallpaperUpdate(WallpaperProviderContract.UpdateReason.PREFS_CHANGED)
        if (extras.getBoolean("close", false)) finish()
    }

    /**
     * Tell Projectivy to re-request wallpapers. Not needed when the user reached
     * these settings from Projectivy's own Appearance > Wallpaper screen — the
     * launcher already refreshes on return from there.
     */
    fun requestWallpaperUpdate(reason: Int = WallpaperProviderContract.UpdateReason.PREFS_CHANGED) {
        val intent = Intent(WallpaperProviderContract.ACTION_WALLPAPER_PROVIDER_UPDATED).apply {
            `package` = PROJECTIVY_PACKAGE_ID
            // Note: the upstream sample passes the resource ID here rather than the
            // resolved string. Projectivy matches on the UUID value, so send the string.
            putExtra(WallpaperProviderContract.EXTRA_PROVIDER_ID, getString(R.string.plugin_uuid))
            putExtra(WallpaperProviderContract.EXTRA_UPDATE_REASON, reason)
        }
        sendBroadcast(intent)
    }

    private fun PackageManager.isApplicationInstalled(packageName: String): Boolean = try {
        getApplicationInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
}
