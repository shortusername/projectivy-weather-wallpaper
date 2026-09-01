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

    /** Held between a search and the user picking one of its results. */
    private var searchResults: List<GeocodingClient.Place> = emptyList()


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
        private const val ACTION_ID_THEME = 22L
        private const val ACTION_ID_UPDATE = 23L
        private const val ACTION_ID_REPORT = 24L
        private const val ACTION_ID_WORLD = 25L
        private const val ACTION_ID_ADD_LOCATION = 26L
        private const val ACTION_ID_LOCATIONS = 27L
        private const val ACTION_ID_CYCLE = 28L

        private const val SUB_CYCLE_OFF = 150L
        private const val SUB_CYCLE_EVERY = 151L
        private const val SUB_CYCLE_ALT = 152L

        /** Geocoding matches occupy their own id range. */
        private const val SUB_SEARCH_BASE = 2000L
        /** Saved locations, selected to remove. */
        private const val SUB_SAVED_BASE = 3000L

        private const val SUB_WORLD_OFF = 140L
        private const val SUB_WORLD_OCC = 141L
        private const val SUB_WORLD_FREQ = 142L

        private const val SUB_THEME_AUTO = 130L
        private const val SUB_THEME_DAY = 131L
        private const val SUB_THEME_NIGHT = 132L

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
                .id(ACTION_ID_ADD_LOCATION)
                .title(R.string.setting_add_location_title)
                .description(R.string.setting_add_location_desc)
                .editDescription("")
                .descriptionEditable(true)
                .build()
        )

        val extras = PreferencesManager.savedLocations
        actions.add(
            GuidedAction.Builder(context)
                .id(ACTION_ID_LOCATIONS)
                .title(R.string.setting_locations_title)
                .description(
                    if (extras.isEmpty()) getString(R.string.locations_none)
                    else getString(R.string.locations_count, extras.size)
                )
                .subActions(
                    if (extras.isEmpty()) {
                        listOf(subAction(SUB_SAVED_BASE - 1, R.string.locations_none))
                    } else {
                        extras.mapIndexed { i, loc ->
                            GuidedAction.Builder(context)
                                .id(SUB_SAVED_BASE + i)
                                .title(loc.label)
                                .description(R.string.locations_remove_hint)
                                .build()
                        }
                    }
                )
                .build()
        )

        actions.add(
            GuidedAction.Builder(context)
                .id(ACTION_ID_CYCLE)
                .title(R.string.setting_cycle_title)
                .description(cycleLabel())
                .subActions(
                    listOf(
                        subAction(SUB_CYCLE_OFF, R.string.cycle_off),
                        subAction(SUB_CYCLE_ALT, R.string.cycle_alternate),
                        subAction(SUB_CYCLE_EVERY, R.string.cycle_every)
                    )
                )
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
                .id(ACTION_ID_WORLD)
                .title(R.string.setting_world_title)
                .description(worldLabel())
                .subActions(
                    listOf(
                        subAction(SUB_WORLD_OFF, R.string.world_off),
                        subAction(SUB_WORLD_OCC, R.string.world_occasional),
                        subAction(SUB_WORLD_FREQ, R.string.world_frequent)
                    )
                )
                .build()
        )

        actions.add(
            GuidedAction.Builder(context)
                .id(ACTION_ID_THEME)
                .title(R.string.setting_theme_title)
                .description(themeLabel())
                .subActions(
                    listOf(
                        subAction(SUB_THEME_AUTO, R.string.theme_auto),
                        subAction(SUB_THEME_DAY, R.string.theme_day),
                        subAction(SUB_THEME_NIGHT, R.string.theme_night)
                    )
                )
                .build()
        )

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

        actions.add(
            GuidedAction.Builder(context)
                .id(ACTION_ID_REPORT)
                .title(R.string.setting_report_title)
                .description(R.string.setting_report_desc)
                .build()
        )

        actions.add(
            GuidedAction.Builder(context)
                .id(ACTION_ID_UPDATE)
                .title(R.string.setting_update_title)
                .description(
                    getString(R.string.setting_update_desc, BuildConfig.VERSION_NAME)
                )
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

        if (action.id >= SUB_SAVED_BASE) {
            val index = (action.id - SUB_SAVED_BASE).toInt()
            val current = PreferencesManager.savedLocations
            current.getOrNull(index)?.let { removed ->
                PreferencesManager.savedLocations =
                    current.filterIndexed { i, _ -> i != index }
                toast(getString(R.string.locations_removed, removed.label))
                rebuild()
                pushUpdate(WallpaperProviderContract.UpdateReason.PREFS_CHANGED)
            }
            return true
        }

        if (action.id >= SUB_SEARCH_BASE) {
            val index = (action.id - SUB_SEARCH_BASE).toInt()
            searchResults.getOrNull(index)?.let { addPlace(it) }
            return true
        }

        if (action.id in SUB_CYCLE_OFF..SUB_CYCLE_ALT) {
            PreferencesManager.cycleMode = when (action.id) {
                SUB_CYCLE_EVERY -> PreferencesManager.CYCLE_EVERY
                SUB_CYCLE_ALT -> PreferencesManager.CYCLE_ALTERNATE
                else -> PreferencesManager.CYCLE_OFF
            }
            findActionById(ACTION_ID_CYCLE)?.description = cycleLabel()
            notifyActionChanged(findActionPositionById(ACTION_ID_CYCLE))
            pushUpdate(WallpaperProviderContract.UpdateReason.PREFS_CHANGED)
            return true
        }

        if (action.id in SUB_WORLD_OFF..SUB_WORLD_FREQ) {
            PreferencesManager.worldWatch = when (action.id) {
                SUB_WORLD_OCC -> PreferencesManager.WORLD_OCCASIONAL
                SUB_WORLD_FREQ -> PreferencesManager.WORLD_FREQUENT
                else -> PreferencesManager.WORLD_OFF
            }
            findActionById(ACTION_ID_WORLD)?.description = worldLabel()
            notifyActionChanged(findActionPositionById(ACTION_ID_WORLD))
            pushUpdate(WallpaperProviderContract.UpdateReason.PREFS_CHANGED)
            return true
        }

        if (action.id in SUB_THEME_AUTO..SUB_THEME_NIGHT) {
            PreferencesManager.themeMode = when (action.id) {
                SUB_THEME_DAY -> PreferencesManager.THEME_DAY
                SUB_THEME_NIGHT -> PreferencesManager.THEME_NIGHT
                else -> PreferencesManager.THEME_AUTO
            }
            findActionById(ACTION_ID_THEME)?.description = themeLabel()
            notifyActionChanged(findActionPositionById(ACTION_ID_THEME))
            pushUpdate(WallpaperProviderContract.UpdateReason.PREFS_CHANGED)
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
            ACTION_ID_REPORT -> createReport()
            ACTION_ID_UPDATE -> checkForUpdate()
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
            ACTION_ID_ADD_LOCATION -> {
                searchLocation(value, action)
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

    /**
     * Writes a diagnostic report and offers to share it.
     *
     * Runs off the UI thread: it reads logcat and stats the pack cache, neither
     * of which is instant. The report is deliberately written somewhere the user
     * can reach without root or a permission grant.
     */
    private fun createReport() {
        val action = findActionById(ACTION_ID_REPORT) ?: return
        action.description = getString(R.string.report_working)
        notifyActionChanged(findActionPositionById(ACTION_ID_REPORT))

        Thread {
            val ctx = context ?: return@Thread
            val file = Diagnostics.write(ctx)
            activity?.runOnUiThread {
                if (!isAdded) return@runOnUiThread
                if (file == null) {
                    action.description = getString(R.string.report_failed)
                } else {
                    action.description = getString(
                        R.string.report_written, file.parentFile?.name ?: "files"
                    )
                    shareReport(file)
                }
                notifyActionChanged(findActionPositionById(ACTION_ID_REPORT))
            }
        }.start()
    }

    /**
     * Offers the report to any share target. TV boxes often have none, which is
     * why the file path is shown regardless — sharing is the convenience, the
     * file on disk is the guarantee.
     */
    private fun shareReport(file: java.io.File) {
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                requireContext(), "${requireContext().packageName}.fileprovider", file
            )
            val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                putExtra(
                    android.content.Intent.EXTRA_SUBJECT,
                    "Weather Wallpaper v${BuildConfig.VERSION_NAME} report"
                )
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(
                android.content.Intent.createChooser(
                    send, getString(R.string.report_share_title)
                )
            )
        } catch (t: Throwable) {
            toast(getString(R.string.report_no_share_target))
        }
    }

    /**
     * Fetches, downloads and installs in one action, on a worker thread.
     *
     * Two taps total: this, then the system installer's confirmation. Any
     * failure reports itself rather than failing silently, because a broken
     * update path is worse than no update path.
     */
    private fun checkForUpdate() {
        val action = findActionById(ACTION_ID_UPDATE) ?: return
        action.description = getString(R.string.update_checking)
        notifyActionChanged(findActionPositionById(ACTION_ID_UPDATE))

        Thread {
            val release = UpdateChecker.fetchLatest()
            val ctx = context ?: return@Thread

            if (release == null) {
                post(action, getString(R.string.update_check_failed))
                return@Thread
            }
            if (!UpdateChecker.isNewer(release.version, BuildConfig.VERSION_NAME)) {
                post(action, getString(R.string.update_up_to_date, BuildConfig.VERSION_NAME))
                return@Thread
            }

            post(action, getString(R.string.update_downloading, release.version))
            val apk = UpdateChecker.download(ctx, release)
            if (apk == null) {
                post(action, getString(R.string.update_download_failed))
                return@Thread
            }

            post(action, getString(R.string.update_ready, release.version))
            activity?.runOnUiThread {
                if (!isAdded) return@runOnUiThread
                if (!UpdateChecker.install(requireContext(), apk)) {
                    toast(getString(R.string.update_install_failed))
                }
            }
        }.start()
    }

    private fun post(action: GuidedAction, text: String) {
        activity?.runOnUiThread {
            if (!isAdded) return@runOnUiThread
            action.description = text
            notifyActionChanged(findActionPositionById(ACTION_ID_UPDATE))
        }
    }

    /**
     * Searches by name on a worker thread.
     *
     * One match is added straight away. Several become sub-actions for the user
     * to choose from, because a bare name is often ambiguous — "Springfield"
     * returns four US cities, and silently picking the largest would be wrong
     * about as often as it was right.
     */
    private fun searchLocation(query: String, action: GuidedAction) {
        if (query.isBlank()) return
        action.description = getString(R.string.locations_searching)
        notifyActionChanged(findActionPositionById(ACTION_ID_ADD_LOCATION))

        Thread {
            val results = GeocodingClient.search(query)
            activity?.runOnUiThread {
                if (!isAdded) return@runOnUiThread
                searchResults = results
                when {
                    results.isEmpty() -> {
                        action.description = getString(R.string.locations_no_match, query)
                        notifyActionChanged(findActionPositionById(ACTION_ID_ADD_LOCATION))
                    }
                    results.size == 1 -> addPlace(results.first())
                    else -> {
                        action.subActions = results.mapIndexed { i, place ->
                            GuidedAction.Builder(context)
                                .id(SUB_SEARCH_BASE + i)
                                .title(place.label)
                                .build()
                        }
                        action.description =
                            getString(R.string.locations_pick_match, results.size)
                        notifyActionChanged(findActionPositionById(ACTION_ID_ADD_LOCATION))
                        toast(getString(R.string.locations_pick_match, results.size))
                    }
                }
            }
        }.start()
    }

    private fun addPlace(place: GeocodingClient.Place) {
        val existing = PreferencesManager.savedLocations
        // Guard against duplicates: the same place added twice would just show
        // twice in the rotation with no indication why.
        if (existing.any {
                Math.abs(it.latitude - place.latitude) < 0.01 &&
                        Math.abs(it.longitude - place.longitude) < 0.01
            }) {
            toast(getString(R.string.locations_duplicate, place.shortLabel))
            return
        }
        PreferencesManager.savedLocations = existing + PreferencesManager.SavedLocation(
            place.shortLabel, place.latitude, place.longitude
        )
        toast(getString(R.string.locations_added, place.label))
        rebuild()
        pushUpdate(WallpaperProviderContract.UpdateReason.PREFS_CHANGED)
    }

    /** Rebuilds the action list so counts and sub-action lists stay accurate. */
    private fun rebuild() {
        val actions = mutableListOf<GuidedAction>()
        onCreateActions(actions, null)
        setActions(actions)
    }

    private fun cycleLabel(): String = getString(
        when (PreferencesManager.cycleMode) {
            PreferencesManager.CYCLE_EVERY -> R.string.cycle_every
            PreferencesManager.CYCLE_ALTERNATE -> R.string.cycle_alternate
            else -> R.string.cycle_off
        }
    )

    private fun worldLabel(): String = getString(
        when (PreferencesManager.worldWatch) {
            PreferencesManager.WORLD_OCCASIONAL -> R.string.world_occasional
            PreferencesManager.WORLD_FREQUENT -> R.string.world_frequent
            else -> R.string.world_off
        }
    )

    private fun themeLabel(): String = getString(
        when (PreferencesManager.themeMode) {
            PreferencesManager.THEME_DAY -> R.string.theme_day
            PreferencesManager.THEME_NIGHT -> R.string.theme_night
            else -> R.string.theme_auto
        }
    )

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
