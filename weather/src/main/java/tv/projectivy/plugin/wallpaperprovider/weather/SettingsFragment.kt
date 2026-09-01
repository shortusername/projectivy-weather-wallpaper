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
        private const val SUB_PACK = 104L
        private const val SUB_LBL_OFF = 110L
        private const val SUB_LBL_FEW = 111L
        private const val SUB_LBL_BAL = 112L
        private const val SUB_LBL_MANY = 113L

        private const val ACTION_ID_LABELS = 19L
        private const val ACTION_ID_ALERTS = 20L
        private const val ACTION_ID_ANIMATE = 21L

        private const val SUB_AREA_WIDE = 120L
        private const val SUB_AREA_REGIONAL = 121L
        private const val SUB_AREA_STATE = 122L
        private const val SUB_AREA_LOCAL = 123L

        private const val ACTION_ID_PACK = 13L
        private const val ACTION_ID_PACK_REFRESH = 14L
        private const val ACTION_ID_DEMO = 15L
        private const val ACTION_ID_DEMO_LABEL = 16L
        private const val ACTION_ID_BASEMAP = 17L
        private const val ACTION_ID_BASEMAP_ATTR = 18L

        /** Pack sub-actions start here, offset well clear of the fixed ids. */
        private const val SUB_PACK_BASE = 1000L
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
                        subAction(SUB_RADAR, R.string.background_radar),
                        subAction(SUB_PACK, R.string.background_pack)
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

        actions.add(
            GuidedAction.Builder(context)
                .id(ACTION_ID_RADAR_ZOOM)
                .title(R.string.setting_radar_area_title)
                .description(radarAreaLabel())
                .subActions(
                    listOf(
                        subAction(SUB_AREA_WIDE, R.string.radar_area_wide),
                        subAction(SUB_AREA_REGIONAL, R.string.radar_area_regional),
                        subAction(SUB_AREA_STATE, R.string.radar_area_state),
                        subAction(SUB_AREA_LOCAL, R.string.radar_area_local)
                    )
                )
                .build()
        )

        val cached = PackManager.cachedPacks(requireContext())
        actions.add(
            GuidedAction.Builder(context)
                .id(ACTION_ID_PACK)
                .title(R.string.setting_pack_title)
                .description(packLabel(cached))
                .subActions(
                    if (cached.isEmpty()) {
                        listOf(subAction(SUB_PACK_BASE - 1, R.string.pack_none_available))
                    } else {
                        cached.mapIndexed { i, p ->
                            GuidedAction.Builder(context)
                                .id(SUB_PACK_BASE + i)
                                .title("${p.name} · ${p.author}")
                                .description(packKindLabel(p))
                                .build()
                        }
                    }
                )
                .build()
        )

        actions.add(
            GuidedAction.Builder(context)
                .id(ACTION_ID_PACK_REFRESH)
                .title(R.string.setting_pack_refresh_title)
                .description(R.string.setting_pack_refresh_desc)
                .build()
        )

        actions.add(checkbox(ACTION_ID_ALERTS, R.string.setting_alerts_title,
            R.string.setting_alerts_desc, PreferencesManager.showAlerts))
        actions.add(checkbox(ACTION_ID_ANIMATE, R.string.setting_animate_title,
            R.string.setting_animate_desc, PreferencesManager.animateRadar))

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
                .id(ACTION_ID_LABELS)
                .title(R.string.setting_labels_title)
                .description(labelDensityLabel())
                .subActions(
                    listOf(
                        subAction(SUB_LBL_OFF, R.string.labels_off),
                        subAction(SUB_LBL_FEW, R.string.labels_few),
                        subAction(SUB_LBL_BAL, R.string.labels_balanced),
                        subAction(SUB_LBL_MANY, R.string.labels_many)
                    )
                )
                .build()
        )

        val basemap = PreferencesManager.basemapUrl
        actions.add(
            GuidedAction.Builder(context)
                .id(ACTION_ID_BASEMAP)
                .title(R.string.setting_basemap_title)
                .description(
                    if (basemap.isBlank()) getString(R.string.basemap_unset) else basemap
                )
                .editDescription(basemap)
                .descriptionEditable(true)
                .build()
        )

        val basemapAttr = PreferencesManager.basemapAttribution
        actions.add(
            GuidedAction.Builder(context)
                .id(ACTION_ID_BASEMAP_ATTR)
                .title(R.string.setting_basemap_attr_title)
                .description(
                    if (basemapAttr.isBlank()) getString(R.string.basemap_attr_unset)
                    else basemapAttr
                )
                .editDescription(basemapAttr)
                .descriptionEditable(true)
                .build()
        )

        actions.add(checkbox(ACTION_ID_DEMO, R.string.setting_demo_title,
            R.string.setting_demo_desc, PreferencesManager.demoMode))

        val demoLabel = PreferencesManager.demoLabel
        actions.add(
            GuidedAction.Builder(context)
                .id(ACTION_ID_DEMO_LABEL)
                .title(R.string.setting_demo_label_title)
                .description(demoLabel)
                .editDescription(demoLabel)
                .descriptionEditable(true)
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
        // Pack selection is a separate id range from the background picker.
        if (action.id >= SUB_PACK_BASE) {
            val packs = PackManager.cachedPacks(requireContext())
            val index = (action.id - SUB_PACK_BASE).toInt()
            packs.getOrNull(index)?.let { chosen ->
                PreferencesManager.selectedPack = chosen.id
                PreferencesManager.backgroundSource = Backgrounds.SOURCE_PACK

                findActionById(ACTION_ID_PACK)?.description = packLabel(packs)
                notifyActionChanged(findActionPositionById(ACTION_ID_PACK))
                findActionById(ACTION_ID_BACKGROUND)?.description = backgroundLabel()
                notifyActionChanged(findActionPositionById(ACTION_ID_BACKGROUND))

                if (chosen.kind == PackManager.KIND_VIDEO) {
                    toast(getString(R.string.toast_video_no_overlay))
                }
                pushUpdate(WallpaperProviderContract.UpdateReason.PREFS_CHANGED)
            }
            return true
        }

        if (action.id in SUB_AREA_WIDE..SUB_AREA_LOCAL) {
            PreferencesManager.radarZoom = when (action.id) {
                SUB_AREA_WIDE -> 4
                SUB_AREA_REGIONAL -> 5
                SUB_AREA_STATE -> 6
                else -> 7
            }
            findActionById(ACTION_ID_RADAR_ZOOM)?.description = radarAreaLabel()
            notifyActionChanged(findActionPositionById(ACTION_ID_RADAR_ZOOM))
            pushUpdate(WallpaperProviderContract.UpdateReason.PREFS_CHANGED)
            return true
        }

        if (action.id in SUB_LBL_OFF..SUB_LBL_MANY) {
            PreferencesManager.labelDensity = when (action.id) {
                SUB_LBL_OFF -> PreferencesManager.LABELS_OFF
                SUB_LBL_FEW -> PreferencesManager.LABELS_FEW
                SUB_LBL_MANY -> PreferencesManager.LABELS_MANY
                else -> PreferencesManager.LABELS_BALANCED
            }
            findActionById(ACTION_ID_LABELS)?.description = labelDensityLabel()
            notifyActionChanged(findActionPositionById(ACTION_ID_LABELS))
            pushUpdate(WallpaperProviderContract.UpdateReason.PREFS_CHANGED)
            return true
        }

        val source = when (action.id) {
            SUB_GRADIENT -> Backgrounds.SOURCE_GRADIENT
            SUB_LOCAL -> Backgrounds.SOURCE_LOCAL
            SUB_STOCK -> Backgrounds.SOURCE_STOCK
            SUB_RADAR -> Backgrounds.SOURCE_RADAR
            SUB_PACK -> Backgrounds.SOURCE_PACK
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
            Backgrounds.SOURCE_PACK ->
                if (PreferencesManager.selectedPack.isBlank()) {
                    toast(getString(R.string.toast_pick_a_pack))
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
            ACTION_ID_ALERTS -> {
                PreferencesManager.showAlerts = action.isChecked
                pushUpdate(WallpaperProviderContract.UpdateReason.PREFS_CHANGED)
            }
            ACTION_ID_ANIMATE -> {
                PreferencesManager.animateRadar = action.isChecked
                if (action.isChecked &&
                    PreferencesManager.backgroundSource != Backgrounds.SOURCE_RADAR
                ) {
                    toast(getString(R.string.toast_animate_needs_radar))
                }
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
            ACTION_ID_PACK_REFRESH -> {
                toast(getString(R.string.toast_pack_refreshing))
                // Network must not run on the UI thread; rebuild the actions
                // afterwards so the new pack list appears.
                Thread {
                    val ok = PackManager.refresh(requireContext())
                    activity?.runOnUiThread {
                        if (!isAdded) return@runOnUiThread
                        toast(
                            getString(
                                if (ok) R.string.toast_pack_refreshed
                                else R.string.toast_pack_refresh_failed
                            )
                        )
                        val rebuilt = mutableListOf<GuidedAction>()
                        onCreateActions(rebuilt, null)
                        setActions(rebuilt)
                    }
                }.start()
            }
            ACTION_ID_DEMO -> {
                PreferencesManager.demoMode = action.isChecked
                if (action.isChecked &&
                    PreferencesManager.backgroundSource == Backgrounds.SOURCE_RADAR
                ) {
                    toast(getString(R.string.toast_demo_radar_warning))
                }
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
            ACTION_ID_BASEMAP -> {
                PreferencesManager.basemapUrl = value
                action.description =
                    if (value.isBlank()) getString(R.string.basemap_unset) else value
                action.editDescription = value
                if (value.contains("tile.openstreetmap.org")) {
                    toast(getString(R.string.toast_osm_not_permitted))
                }
            }
            ACTION_ID_BASEMAP_ATTR -> {
                PreferencesManager.basemapAttribution = value
                action.description =
                    if (value.isBlank()) getString(R.string.basemap_attr_unset) else value
                action.editDescription = value
            }
            ACTION_ID_DEMO_LABEL -> {
                val label = value.ifEmpty { getString(R.string.default_demo_label) }
                PreferencesManager.demoLabel = label
                action.description = label
                action.editDescription = label
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

    private fun radarAreaLabel(): String = getString(
        when (PreferencesManager.radarZoom) {
            4 -> R.string.radar_area_wide
            5 -> R.string.radar_area_regional
            6 -> R.string.radar_area_state
            else -> R.string.radar_area_local
        }
    )

    private fun labelDensityLabel(): String = getString(
        when (PreferencesManager.labelDensity) {
            PreferencesManager.LABELS_OFF -> R.string.labels_off
            PreferencesManager.LABELS_FEW -> R.string.labels_few
            PreferencesManager.LABELS_MANY -> R.string.labels_many
            else -> R.string.labels_balanced
        }
    )

    private fun packLabel(packs: List<PackManager.Pack>): String {
        val id = PreferencesManager.selectedPack
        if (id.isBlank()) return getString(R.string.pack_none_selected)
        val match = packs.firstOrNull { it.id == id }
        return match?.let { "${it.name} · ${it.author}" }
            ?: getString(R.string.pack_unknown, id)
    }

    private fun packKindLabel(p: PackManager.Pack): String = getString(
        when (p.kind) {
            PackManager.KIND_LOTTIE -> R.string.pack_kind_lottie
            PackManager.KIND_VIDEO -> R.string.pack_kind_video
            else -> R.string.pack_kind_static
        }
    )

    private fun backgroundLabel(): String = getString(
        when (PreferencesManager.backgroundSource) {
            Backgrounds.SOURCE_LOCAL -> R.string.background_local
            Backgrounds.SOURCE_STOCK -> R.string.background_stock
            Backgrounds.SOURCE_RADAR -> R.string.background_radar
            Backgrounds.SOURCE_GRADIENT -> R.string.background_gradient
            Backgrounds.SOURCE_PACK -> R.string.background_pack
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
