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
                .id(ACTION_ID_REFRESH)
                .title(R.string.setting_refresh_title)
                .description(R.string.setting_refresh_desc)
                .build()
        )
    }

    override fun onGuidedActionClicked(action: GuidedAction) {
        when (action.id) {
            ACTION_ID_UNITS -> {
                PreferencesManager.useMetric = action.isChecked
                action.description = unitsLabel()
                notifyActionChanged(findActionPositionById(ACTION_ID_UNITS))
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
