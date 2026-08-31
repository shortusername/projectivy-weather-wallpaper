package tv.projectivy.plugin.wallpaperprovider.weather

import android.os.Bundle
import android.text.InputType
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import androidx.leanback.app.GuidedStepSupportFragment
import androidx.leanback.widget.GuidanceStylist.Guidance
import androidx.leanback.widget.GuidedAction
import tv.projectivy.plugin.wallpaperprovider.api.WallpaperProviderContract

class SettingsFragment : GuidedStepSupportFragment() {

    companion object {
        private const val ACTION_ID_LATITUDE = 1L
        private const val ACTION_ID_LONGITUDE = 2L
        private const val ACTION_ID_PLACE = 3L
        private const val ACTION_ID_UNITS = 4L
        private const val ACTION_ID_REFRESH = 5L
        private const val ACTION_ID_BACKGROUND = 6L
        private const val ACTION_ID_UNSPLASH = 7L
        private const val ACTION_ID_RADAR_ZOOM = 8L
        private const val ACTION_ID_HOURLY = 9L
        private const val ACTION_ID_DAILY = 10L
        private const val ACTION_ID_STATS = 11L
        private const val ACTION_ID_SUN = 12L

        // Sub-action ids for the background picker, offset so they can't clash
        // with the top-level ids above.
        private const val SUB_SCENE = 99L
        private const val SUB_GRADIENT = 100L
        private const val SUB_LOCAL = 101L
        private const val SUB_STOCK = 102L
        private const val SUB_RADAR = 103L
    }

    override fun onCreateGuidance(savedInstanceState: Bundle?): Guidance {
        return Guidance(
            getString(R.string.plugin_name),
            "v${BuildConfig.VERSION_NAME}\n\n${getString(R.string.plugin_description)}",
            getString(R.string.settings),
            AppCompatResources.getDrawable(requireActivity(), R.drawable.ic_plugin)
        )
    }

    override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        PreferencesManager.init(requireContext())

        // Signed decimals: the numeric-only TV keyboard hides the minus sign on some
        // devices, so allow a general text field with a numeric-signed-decimal hint.
        val coordInput = InputType.TYPE_CLASS_NUMBER or
                InputType.TYPE_NUMBER_FLAG_DECIMAL or
                InputType.TYPE_NUMBER_FLAG_SIGNED

        val lat = PreferencesManager.latitude.toString()
        actions.add(
            GuidedAction.Builder(context)
                .id(ACTION_ID_LATITUDE)
                .title(R.string.setting_latitude_title)
                .description(lat)
                .editDescription(lat)
                .descriptionEditable(true)
                .descriptionEditInputType(coordInput)
                .build()
        )

        val lon = PreferencesManager.longitude.toString()
        actions.add(
            GuidedAction.Builder(context)
                .id(ACTION_ID_LONGITUDE)
                .title(R.string.setting_longitude_title)
                .description(lon)
                .editDescription(lon)
                .descriptionEditable(true)
                .descriptionEditInputType(coordInput)
                .build()
        )

        val place = PreferencesManager.placeLabel
        actions.add(
            GuidedAction.Builder(context)
                .id(ACTION_ID_PLACE)
                .title(R.string.setting_place_title)
                .description(place)
                .editDescription(place)
                .descriptionEditable(true)
                .build()
        )

        actions.add(
            GuidedAction.Builder(context)
                .id(ACTION_ID_UNITS)
                .title(R.string.setting_units_title)
                .description(unitsLabel())
                .checkSetId(GuidedAction.CHECKBOX_CHECK_SET_ID)
                .checked(PreferencesManager.useMetric)
                .build()
        )

        actions.add(
            GuidedAction.Builder(context)
                .id(ACTION_ID_BACKGROUND)
                .title(R.string.setting_background_title)
                .description(backgroundLabel())
                .subActions(
                    listOf(
                        subAction(SUB_SCENE, R.string.background_scene),
                        subAction(SUB_GRADIENT, R.string.background_gradient),
                        subAction(SUB_LOCAL, R.string.background_local),
                        subAction(SUB_STOCK, R.string.background_stock),
                        subAction(SUB_RADAR, R.string.background_radar)
                    )
                )
                .build()
        )

        val key = PreferencesManager.unsplashKey
        actions.add(
            GuidedAction.Builder(context)
                .id(ACTION_ID_UNSPLASH)
                .title(R.string.setting_unsplash_title)
                .description(if (key.isBlank()) getString(R.string.unsplash_unset) else maskKey(key))
                .editDescription(key)
                .descriptionEditable(true)
                .build()
        )

        val zoom = PreferencesManager.radarZoom.toString()
        actions.add(
            GuidedAction.Builder(context)
                .id(ACTION_ID_RADAR_ZOOM)
                .title(R.string.setting_radar_zoom_title)
                .description(getString(R.string.setting_radar_zoom_desc, zoom))
                .editDescription(zoom)
                .descriptionEditable(true)
                .descriptionEditInputType(InputType.TYPE_CLASS_NUMBER)
                .build()
        )

        actions.add(checkbox(ACTION_ID_HOURLY, R.string.setting_hourly_title,
            R.string.setting_hourly_desc, PreferencesManager.showHourly))
        actions.add(checkbox(ACTION_ID_DAILY, R.string.setting_daily_title,
            R.string.setting_daily_desc, PreferencesManager.showDaily))
        actions.add(checkbox(ACTION_ID_STATS, R.string.setting_stats_title,
            R.string.setting_stats_desc, PreferencesManager.showStats))
        actions.add(checkbox(ACTION_ID_SUN, R.string.setting_sun_title,
            R.string.setting_sun_desc, PreferencesManager.showSun))

        actions.add(
            GuidedAction.Builder(context)
                .id(ACTION_ID_REFRESH)
                .title(R.string.setting_refresh_title)
                .description(R.string.setting_refresh_desc)
                .build()
        )
    }

    private fun checkbox(id: Long, titleRes: Int, descRes: Int, checked: Boolean): GuidedAction =
        GuidedAction.Builder(context)
            .id(id)
            .title(titleRes)
            .description(descRes)
            .checkSetId(GuidedAction.CHECKBOX_CHECK_SET_ID)
            .checked(checked)
            .build()

    private fun subAction(id: Long, titleRes: Int): GuidedAction =
        GuidedAction.Builder(context).id(id).title(titleRes).build()

    override fun onSubGuidedActionClicked(action: GuidedAction): Boolean {
        val source = when (action.id) {
            SUB_GRADIENT -> Backgrounds.SOURCE_GRADIENT
            SUB_LOCAL -> Backgrounds.SOURCE_LOCAL
            SUB_STOCK -> Backgrounds.SOURCE_STOCK
            SUB_RADAR -> Backgrounds.SOURCE_RADAR
            else -> Backgrounds.SOURCE_SCENE
        }
        PreferencesManager.backgroundSource = source

        findActionById(ACTION_ID_BACKGROUND)?.description = backgroundLabel()
        notifyActionChanged(findActionPositionById(ACTION_ID_BACKGROUND))

        when (source) {
            Backgrounds.SOURCE_LOCAL -> toast(
                getString(
                    R.string.toast_local_folder,
                    Backgrounds.localFolder(requireContext()).absolutePath
                )
            )
            Backgrounds.SOURCE_STOCK ->
                if (PreferencesManager.unsplashKey.isBlank()) {
                    toast(getString(R.string.toast_needs_unsplash_key))
                }
        }

        pushUpdate(WallpaperProviderContract.UpdateReason.PREFS_CHANGED)
        return true   // collapse the sub-action list
    }

    override fun onGuidedActionClicked(action: GuidedAction) {
        when (action.id) {
            ACTION_ID_UNITS -> {
                PreferencesManager.useMetric = action.isChecked
                action.description = unitsLabel()
                notifyActionChanged(findActionPositionById(ACTION_ID_UNITS))
                pushUpdate(WallpaperProviderContract.UpdateReason.PREFS_CHANGED)
            }
            ACTION_ID_HOURLY -> {
                PreferencesManager.showHourly = action.isChecked
                pushUpdate(WallpaperProviderContract.UpdateReason.PREFS_CHANGED)
            }
            ACTION_ID_DAILY -> {
                PreferencesManager.showDaily = action.isChecked
                pushUpdate(WallpaperProviderContract.UpdateReason.PREFS_CHANGED)
            }
            ACTION_ID_STATS -> {
                PreferencesManager.showStats = action.isChecked
                pushUpdate(WallpaperProviderContract.UpdateReason.PREFS_CHANGED)
            }
            ACTION_ID_SUN -> {
                PreferencesManager.showSun = action.isChecked
                pushUpdate(WallpaperProviderContract.UpdateReason.PREFS_CHANGED)
            }
            ACTION_ID_REFRESH -> {
                pushUpdate(WallpaperProviderContract.UpdateReason.DATA_CHANGED)
                toast(getString(R.string.toast_refresh_requested))
            }
        }
    }

    override fun onGuidedActionEditedAndProceed(action: GuidedAction): Long {
        val value = action.editDescription?.toString()?.trim().orEmpty()

        when (action.id) {
            ACTION_ID_LATITUDE -> {
                val parsed = value.toDoubleOrNull()
                if (parsed == null || parsed < -90.0 || parsed > 90.0) {
                    toast(getString(R.string.toast_bad_latitude))
                    action.description = PreferencesManager.latitude.toString()
                    action.editDescription = PreferencesManager.latitude.toString()
                } else {
                    PreferencesManager.latitude = parsed
                    action.description = parsed.toString()
                }
            }
            ACTION_ID_LONGITUDE -> {
                val parsed = value.toDoubleOrNull()
                if (parsed == null || parsed < -180.0 || parsed > 180.0) {
                    toast(getString(R.string.toast_bad_longitude))
                    action.description = PreferencesManager.longitude.toString()
                    action.editDescription = PreferencesManager.longitude.toString()
                } else {
                    PreferencesManager.longitude = parsed
                    action.description = parsed.toString()
                }
            }
            ACTION_ID_UNSPLASH -> {
                PreferencesManager.unsplashKey = value
                action.description =
                    if (value.isBlank()) getString(R.string.unsplash_unset) else maskKey(value)
                action.editDescription = value
            }
            ACTION_ID_RADAR_ZOOM -> {
                val parsed = value.toIntOrNull()
                if (parsed == null || parsed < 4 || parsed > 9) {
                    toast(getString(R.string.toast_bad_zoom))
                    action.editDescription = PreferencesManager.radarZoom.toString()
                } else {
                    PreferencesManager.radarZoom = parsed
                }
                action.description = getString(
                    R.string.setting_radar_zoom_desc, PreferencesManager.radarZoom.toString()
                )
            }
            ACTION_ID_PLACE -> {
                val label = value.ifEmpty { getString(R.string.default_place_label) }
                PreferencesManager.placeLabel = label
                action.description = label
                action.editDescription = label
            }
        }

        notifyActionChanged(findActionPositionById(action.id))
        pushUpdate(WallpaperProviderContract.UpdateReason.PREFS_CHANGED)
        return GuidedAction.ACTION_ID_CURRENT
    }

    private fun backgroundLabel(): String = getString(
        when (PreferencesManager.backgroundSource) {
            Backgrounds.SOURCE_LOCAL -> R.string.background_local
            Backgrounds.SOURCE_STOCK -> R.string.background_stock
            Backgrounds.SOURCE_RADAR -> R.string.background_radar
            Backgrounds.SOURCE_GRADIENT -> R.string.background_gradient
            else -> R.string.background_scene
        }
    )

    /** Never show a full API key on a screen someone might be casting. */
    private fun maskKey(key: String): String =
        if (key.length <= 6) "••••••" else "••••••" + key.takeLast(4)

    private fun unitsLabel(): String = getString(
        if (PreferencesManager.useMetric) R.string.units_metric else R.string.units_imperial
    )

    private fun pushUpdate(reason: Int) {
        (activity as? SettingsActivity)?.requestWallpaperUpdate(reason)
    }

    private fun toast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }
}
