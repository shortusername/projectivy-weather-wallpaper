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

    /**
     * Which settings category this screen shows, or null for the root screen
     * that just lists the categories. Read from the fragment's own arguments,
     * the standard way to parameterize a Fragment instance in Android — set
     * once via newInstance() and never changed for the life of this instance.
     */
    private val category: String? get() = arguments?.getString(ARG_CATEGORY)

    companion object {
        private const val ARG_CATEGORY = "category"

        private const val CAT_LOCATION = "location"
        private const val CAT_APPEARANCE = "appearance"
        private const val CAT_WEATHER = "weather"
        private const val CAT_CLOCK = "clock"
        private const val CAT_SCREEN = "screen"
        private const val CAT_EXPERIMENTAL = "experimental"
        private const val CAT_DEMO = "demo"
        private const val CAT_HOUSEKEEPING = "housekeeping"

        /**
         * Builds the sub-screen for one category. Pushed onto the fragment back
         * stack via GuidedStepSupportFragment.add(), which is what gives this
         * the standard Leanback wizard behaviour: the remote's back button
         * returns to the category list automatically, with no extra code
         * needed here for that.
         */
        fun newInstance(category: String): SettingsFragment = SettingsFragment().apply {
            arguments = Bundle().apply { putString(ARG_CATEGORY, category) }
        }

        // IDs for the 8 category entries on the root screen. Placed in the
        // 50-98 gap: every existing top-level ACTION_ID runs 1-49 with no
        // gaps, and 99 is already SUB_SCENE, so this range is guaranteed
        // clear of everything already in this file.
        private const val ACTION_ID_CAT_LOCATION = 60L
        private const val ACTION_ID_CAT_APPEARANCE = 61L
        private const val ACTION_ID_CAT_WEATHER = 62L
        private const val ACTION_ID_CAT_CLOCK = 63L
        private const val ACTION_ID_CAT_SCREEN = 64L
        private const val ACTION_ID_CAT_EXPERIMENTAL = 65L
        private const val ACTION_ID_CAT_DEMO = 66L
        private const val ACTION_ID_CAT_HOUSEKEEPING = 67L

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
        private const val SUB_SATELLITE = 105L
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
        private const val ACTION_ID_HOLIDAY = 29L
        private const val ACTION_ID_SCALE = 30L
        private const val ACTION_ID_ANIMATE_PRECIP = 31L
        private const val ACTION_ID_EXPERIMENTAL = 32L
        private const val ACTION_ID_NOWCAST = 33L
        private const val ACTION_ID_AURORA = 34L
        private const val ACTION_ID_YESTERDAY = 35L
        private const val ACTION_ID_SAFE_RADAR = 36L
        private const val ACTION_ID_AIR = 37L
        private const val ACTION_ID_ADVISORIES = 38L
        private const val ACTION_ID_CLOCK = 39L
        private const val ACTION_ID_CLOCK_DATE = 40L
        private const val ACTION_ID_CLOCK_POSITION = 41L
        private const val ACTION_ID_CLOCK_SIZE = 42L
        private const val ACTION_ID_CLOCK_STYLE = 43L
        private const val ACTION_ID_CLOCK_HOURS = 44L

        private const val SUB_CLOCK_POS_BASE = 5000L
        private const val SUB_CLOCK_SIZE_BASE = 6000L
        private const val SUB_CLOCK_STYLE_BASE = 7000L
        private const val SUB_CLOCK_HOURS_BASE = 8000L
        private const val SUB_SAFE_BASE = 9000L

        private const val ACTION_ID_SAFE_AREA = 45L
        private const val ACTION_ID_IDLE_FULL = 46L
        private const val ACTION_ID_UPDATE_INTERVAL = 48L
        private const val ACTION_ID_REDUCE_BURN_IN = 49L
        private const val SUB_UPDATE_INTERVAL_BASE = 11000L

        private val UPDATE_INTERVALS = listOf(
            PreferencesManager.UPDATE_NEVER to R.string.update_interval_never,
            PreferencesManager.UPDATE_WEEKLY to R.string.update_interval_weekly,
            PreferencesManager.UPDATE_FORTNIGHTLY to R.string.update_interval_fortnightly,
            PreferencesManager.UPDATE_MONTHLY to R.string.update_interval_monthly
        )

        /** Where the app row starts, as a percentage of screen height. */
        private val SAFE_OPTIONS = listOf(70, 74, 78, 82, 88)

        private val CLOCK_POSITIONS = listOf(
            PreferencesManager.CLOCK_TOP_RIGHT to R.string.clock_pos_top_right,
            PreferencesManager.CLOCK_TOP_CENTRE to R.string.clock_pos_top_centre,
            PreferencesManager.CLOCK_WITH_PANEL to R.string.clock_pos_with_panel
        )
        private val CLOCK_SIZES = listOf(
            PreferencesManager.CLOCK_SMALL to R.string.clock_size_small,
            PreferencesManager.CLOCK_MEDIUM to R.string.clock_size_medium,
            PreferencesManager.CLOCK_LARGE to R.string.clock_size_large
        )
        private val CLOCK_STYLES = listOf(
            PreferencesManager.CLOCK_DIGITAL_LIGHT to R.string.clock_style_light,
            PreferencesManager.CLOCK_DIGITAL_BOLD to R.string.clock_style_bold,
            PreferencesManager.CLOCK_ANALOGUE to R.string.clock_style_analogue
        )
        private val CLOCK_HOUR_MODES = listOf(
            PreferencesManager.CLOCK_HOURS_SYSTEM to R.string.clock_hours_system,
            PreferencesManager.CLOCK_HOURS_12 to R.string.clock_hours_12,
            PreferencesManager.CLOCK_HOURS_24 to R.string.clock_hours_24
        )

        /** Size options, offset so they don't collide with other sub-actions. */
        private const val SUB_SCALE_BASE = 4000L
        private val SCALE_OPTIONS = listOf(80, 90, 100, 110, 120)

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
        private const val ACTION_ID_MARINE = 50L

        /**
         * Sub-action id ranges, each 1000 wide.
         *
         * Tested with bounded ranges, never open-ended `>=`. An open-ended test
         * on the lowest base swallows every range above it — which is exactly
         * what made the location search results unusable: clicking one landed
         * in the pack handler, matched nothing, and silently did nothing.
         */
        private const val SUB_PACK_BASE = 1000L
        private const val SUB_RANGE = 1000L
    }

    /**
     * Which category a top-level action belongs to, for filtering the full
     * list down to what one category screen should show.
     *
     * Every ACTION_ID_* declared above must appear in exactly one branch here.
     * That's checked mechanically before each release — a name silently
     * missing from every branch would mean that setting quietly stops
     * appearing on any screen at all, which is exactly the kind of thing this
     * function existing is meant to prevent, so it would be ironic to get it
     * wrong here of all places.
     */
    private fun categoryOf(id: Long): String? = when (id) {
        ACTION_ID_LATITUDE, ACTION_ID_LONGITUDE, ACTION_ID_PLACE, ACTION_ID_UNITS,
        ACTION_ID_ADD_LOCATION, ACTION_ID_LOCATIONS, ACTION_ID_CYCLE -> CAT_LOCATION

        ACTION_ID_BACKGROUND, ACTION_ID_UNSPLASH, ACTION_ID_RADAR_ZOOM, ACTION_ID_PACK,
        ACTION_ID_PACK_REFRESH, ACTION_ID_BASEMAP, ACTION_ID_BASEMAP_ATTR, ACTION_ID_LABELS,
        ACTION_ID_THEME, ACTION_ID_HOLIDAY, ACTION_ID_SCALE, ACTION_ID_SAFE_RADAR ->
            CAT_APPEARANCE

        ACTION_ID_HOURLY, ACTION_ID_DAILY, ACTION_ID_STATS, ACTION_ID_SUN, ACTION_ID_ALERTS,
        ACTION_ID_WORLD, ACTION_ID_NOWCAST, ACTION_ID_AURORA, ACTION_ID_YESTERDAY,
        ACTION_ID_AIR, ACTION_ID_ADVISORIES, ACTION_ID_MARINE -> CAT_WEATHER

        ACTION_ID_CLOCK, ACTION_ID_CLOCK_DATE, ACTION_ID_CLOCK_POSITION,
        ACTION_ID_CLOCK_SIZE, ACTION_ID_CLOCK_STYLE, ACTION_ID_CLOCK_HOURS -> CAT_CLOCK

        ACTION_ID_SAFE_AREA, ACTION_ID_IDLE_FULL, ACTION_ID_REDUCE_BURN_IN -> CAT_SCREEN

        ACTION_ID_ANIMATE, ACTION_ID_ANIMATE_PRECIP, ACTION_ID_EXPERIMENTAL ->
            CAT_EXPERIMENTAL

        ACTION_ID_DEMO, ACTION_ID_DEMO_LABEL -> CAT_DEMO

        ACTION_ID_REFRESH, ACTION_ID_UPDATE, ACTION_ID_REPORT, ACTION_ID_UPDATE_INTERVAL ->
            CAT_HOUSEKEEPING

        else -> null
    }

    /** Title, icon and short blurb for each of the 8 category entries. */
    private fun rootCategoryActions(): List<GuidedAction> = listOf(
        GuidedAction.Builder(context)
            .id(ACTION_ID_CAT_LOCATION)
            .title(R.string.category_location)
            .description(R.string.category_location_desc)
            .build(),
        GuidedAction.Builder(context)
            .id(ACTION_ID_CAT_APPEARANCE)
            .title(R.string.category_appearance)
            .description(R.string.category_appearance_desc)
            .build(),
        GuidedAction.Builder(context)
            .id(ACTION_ID_CAT_WEATHER)
            .title(R.string.category_weather)
            .description(R.string.category_weather_desc)
            .build(),
        GuidedAction.Builder(context)
            .id(ACTION_ID_CAT_CLOCK)
            .title(R.string.category_clock)
            .description(R.string.category_clock_desc)
            .build(),
        GuidedAction.Builder(context)
            .id(ACTION_ID_CAT_SCREEN)
            .title(R.string.category_screen)
            .description(R.string.category_screen_desc)
            .build(),
        GuidedAction.Builder(context)
            .id(ACTION_ID_CAT_EXPERIMENTAL)
            .title(R.string.category_experimental)
            .description(R.string.category_experimental_desc)
            .build(),
        GuidedAction.Builder(context)
            .id(ACTION_ID_CAT_DEMO)
            .title(R.string.category_demo)
            .description(R.string.category_demo_desc)
            .build(),
        GuidedAction.Builder(context)
            .id(ACTION_ID_CAT_HOUSEKEEPING)
            .title(R.string.category_housekeeping)
            .description(R.string.category_housekeeping_desc)
            .build()
    )

    /** Pushes a category screen, leaving this one on the back stack. */
    private fun pushCategory(category: String) {
        GuidedStepSupportFragment.add(parentFragmentManager, newInstance(category))
    }

    override fun onCreateGuidance(savedInstanceState: Bundle?): Guidance {
        val title = when (category) {
            CAT_LOCATION -> getString(R.string.category_location)
            CAT_APPEARANCE -> getString(R.string.category_appearance)
            CAT_WEATHER -> getString(R.string.category_weather)
            CAT_CLOCK -> getString(R.string.category_clock)
            CAT_SCREEN -> getString(R.string.category_screen)
            CAT_EXPERIMENTAL -> getString(R.string.category_experimental)
            CAT_DEMO -> getString(R.string.category_demo)
            CAT_HOUSEKEEPING -> getString(R.string.category_housekeeping)
            else -> getString(R.string.plugin_name)
        }
        // The version/description blurb only makes sense on the root screen —
        // a category screen already has a specific, self-explanatory title.
        val description = if (category == null) {
            "v${BuildConfig.VERSION_NAME}\n\n${getString(R.string.plugin_description)}"
        } else {
            ""
        }
        return Guidance(
            title,
            description,
            getString(R.string.settings),
            AppCompatResources.getDrawable(requireActivity(), R.drawable.ic_plugin)
        )
    }

    override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        PreferencesManager.init(requireContext())

        // The root screen (category == null) shows only the 8 category
        // entries. Every other screen builds the full set of settings exactly
        // as before into a temporary list, then keeps only the ones belonging
        // to its own category — nothing about how any individual setting is
        // built has changed, only which subset ends up on which screen.
        if (category == null) {
            actions.addAll(rootCategoryActions())
            return
        }

        val allActions = mutableListOf<GuidedAction>()

        // Signed decimals: the numeric-only TV keyboard hides the minus sign on some
        // devices, so allow a general text field with a numeric-signed-decimal hint.
        val coordInput = InputType.TYPE_CLASS_NUMBER or
                InputType.TYPE_NUMBER_FLAG_DECIMAL or
                InputType.TYPE_NUMBER_FLAG_SIGNED

        val lat = PreferencesManager.latitude.toString()
        allActions.add(
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
        allActions.add(
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
        allActions.add(
            GuidedAction.Builder(context)
                .id(ACTION_ID_PLACE)
                .title(R.string.setting_place_title)
                .description(place)
                .editDescription(place)
                .descriptionEditable(true)
                .build()
        )

        // When a search returned several matches, they're attached here so the
        // action is bound with them present and can actually be opened.
        val pending = searchResults
        val addBuilder = GuidedAction.Builder(context)
            .id(ACTION_ID_ADD_LOCATION)
            .title(R.string.setting_add_location_title)
            .editDescription("")
            .descriptionEditable(true)
        if (pending.size > 1) {
            addBuilder
                .description(getString(R.string.locations_pick_match, pending.size))
                .subActions(
                    pending.mapIndexed { i, place ->
                        GuidedAction.Builder(context)
                            .id(SUB_SEARCH_BASE + i)
                            .title(place.label)
                            .build()
                    }
                )
        } else {
            addBuilder.description(R.string.setting_add_location_desc)
        }
        allActions.add(addBuilder.build())

        val extras = PreferencesManager.savedLocations
        allActions.add(
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

        allActions.add(
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

        allActions.add(
            GuidedAction.Builder(context)
                .id(ACTION_ID_UNITS)
                .title(R.string.setting_units_title)
                .description(unitsLabel())
                .checkSetId(GuidedAction.CHECKBOX_CHECK_SET_ID)
                .checked(PreferencesManager.useMetric)
                .build()
        )

        allActions.add(
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
                        subAction(SUB_PACK, R.string.background_pack),
                        subAction(SUB_SATELLITE, R.string.background_satellite)
                    )
                )
                .build()
        )

        val key = PreferencesManager.unsplashKey
        allActions.add(
            GuidedAction.Builder(context)
                .id(ACTION_ID_UNSPLASH)
                .title(R.string.setting_unsplash_title)
                .description(if (key.isBlank()) getString(R.string.unsplash_unset) else maskKey(key))
                .editDescription(key)
                .descriptionEditable(true)
                .build()
        )

        allActions.add(
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
        allActions.add(
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

        allActions.add(
            GuidedAction.Builder(context)
                .id(ACTION_ID_PACK_REFRESH)
                .title(R.string.setting_pack_refresh_title)
                .description(R.string.setting_pack_refresh_desc)
                .build()
        )

        allActions.add(checkbox(ACTION_ID_REDUCE_BURN_IN, R.string.setting_burn_in_title,
            R.string.setting_burn_in_desc, PreferencesManager.reduceBurnIn))
        allActions.add(checkbox(ACTION_ID_NOWCAST, R.string.setting_nowcast_title,
            R.string.setting_nowcast_desc, PreferencesManager.showNowcast))
        allActions.add(checkbox(ACTION_ID_ADVISORIES, R.string.setting_advisories_title,
            R.string.setting_advisories_desc, PreferencesManager.showAdvisories))
        allActions.add(checkbox(ACTION_ID_YESTERDAY, R.string.setting_yesterday_title,
            R.string.setting_yesterday_desc, PreferencesManager.showYesterday))
        allActions.add(checkbox(ACTION_ID_AIR, R.string.setting_air_title,
            R.string.setting_air_desc, PreferencesManager.showAirQuality))
        allActions.add(checkbox(ACTION_ID_AURORA, R.string.setting_aurora_title,
            R.string.setting_aurora_desc, PreferencesManager.showAurora))
        allActions.add(checkbox(ACTION_ID_MARINE, R.string.setting_marine_title,
            R.string.setting_marine_desc, PreferencesManager.showMarine))
        allActions.add(checkbox(ACTION_ID_SAFE_RADAR, R.string.setting_safe_radar_title,
            R.string.setting_safe_radar_desc, PreferencesManager.safeRadarPalette))

        allActions.add(checkbox(ACTION_ID_ALERTS, R.string.setting_alerts_title,
            R.string.setting_alerts_desc, PreferencesManager.showAlerts))
        allActions.add(checkbox(ACTION_ID_EXPERIMENTAL, R.string.setting_experimental_title,
            R.string.setting_experimental_desc, PreferencesManager.experimentalFeatures))

        // The gated features are only listed when the gate is open. Showing
        // them greyed out invites people to wonder what they're missing;
        // hiding them keeps the list honest about what will actually do
        // something.
        if (PreferencesManager.experimentalFeatures) {
            allActions.add(checkbox(ACTION_ID_ANIMATE_PRECIP, R.string.setting_precip_title,
                R.string.setting_precip_desc, PreferencesManager.animatePrecipStored))
            allActions.add(checkbox(ACTION_ID_ANIMATE, R.string.setting_animate_title,
                R.string.setting_animate_desc, PreferencesManager.animateRadarStored))
        }

        allActions.add(checkbox(ACTION_ID_HOURLY, R.string.setting_hourly_title,
            R.string.setting_hourly_desc, PreferencesManager.showHourly))
        allActions.add(checkbox(ACTION_ID_DAILY, R.string.setting_daily_title,
            R.string.setting_daily_desc, PreferencesManager.showDaily))
        allActions.add(checkbox(ACTION_ID_STATS, R.string.setting_stats_title,
            R.string.setting_stats_desc, PreferencesManager.showStats))
        allActions.add(checkbox(ACTION_ID_SUN, R.string.setting_sun_title,
            R.string.setting_sun_desc, PreferencesManager.showSun))

        allActions.add(
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

        allActions.add(
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

        allActions.add(
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
        allActions.add(
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
        allActions.add(
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

        allActions.add(checkbox(ACTION_ID_CLOCK, R.string.setting_clock_title,
            R.string.setting_clock_desc, PreferencesManager.showClock))
        // Clock sub-options only when there's a clock to configure.
        if (PreferencesManager.showClock) {
            allActions.add(picker(ACTION_ID_CLOCK_POSITION, R.string.setting_clock_position_title,
                SUB_CLOCK_POS_BASE, CLOCK_POSITIONS, PreferencesManager.clockPosition))
            allActions.add(picker(ACTION_ID_CLOCK_SIZE, R.string.setting_clock_size_title,
                SUB_CLOCK_SIZE_BASE, CLOCK_SIZES, PreferencesManager.clockSize))
            allActions.add(picker(ACTION_ID_CLOCK_STYLE, R.string.setting_clock_style_title,
                SUB_CLOCK_STYLE_BASE, CLOCK_STYLES, PreferencesManager.clockStyle))
            allActions.add(picker(ACTION_ID_CLOCK_HOURS, R.string.setting_clock_hours_title,
                SUB_CLOCK_HOURS_BASE, CLOCK_HOUR_MODES, PreferencesManager.clockHours))
            allActions.add(checkbox(ACTION_ID_CLOCK_DATE, R.string.setting_clock_date_title,
                R.string.setting_clock_date_desc, PreferencesManager.showClockDate))
        }

        allActions.add(
            GuidedAction.Builder(context)
                .id(ACTION_ID_SAFE_AREA)
                .title(R.string.setting_safe_area_title)
                .description(
                    getString(R.string.setting_safe_area_desc,
                        PreferencesManager.safeBottomPercent)
                )
                .subActions(
                    SAFE_OPTIONS.mapIndexed { i, pct ->
                        GuidedAction.Builder(context)
                            .id(SUB_SAFE_BASE + i)
                            .title(getString(R.string.safe_area_option, pct))
                            .description(
                                when (pct) {
                                    70 -> getString(R.string.safe_area_hint_tall)
                                    78 -> getString(R.string.safe_area_hint_default)
                                    88 -> getString(R.string.safe_area_hint_short)
                                    else -> ""
                                }
                            )
                            .build()
                    }
                )
                .build()
        )

        allActions.add(checkbox(ACTION_ID_IDLE_FULL, R.string.setting_idle_full_title,
            R.string.setting_idle_full_desc, PreferencesManager.idleFullFrame))

        allActions.add(
            GuidedAction.Builder(context)
                .id(ACTION_ID_SCALE)
                .title(R.string.setting_scale_title)
                .description(
                    getString(R.string.setting_scale_desc, PreferencesManager.panelScale)
                )
                .subActions(
                    SCALE_OPTIONS.mapIndexed { i, pct ->
                        GuidedAction.Builder(context)
                            .id(SUB_SCALE_BASE + i)
                            .title(getString(R.string.scale_option, pct))
                            .description(
                                when (pct) {
                                    80 -> getString(R.string.scale_hint_small)
                                    100 -> getString(R.string.scale_hint_default)
                                    120 -> getString(R.string.scale_hint_large)
                                    else -> ""
                                }
                            )
                            .build()
                    }
                )
                .build()
        )

        allActions.add(
            GuidedAction.Builder(context)
                .id(ACTION_ID_HOLIDAY)
                .title(R.string.setting_holiday_title)
                .description(holidayLabel())
                .checkSetId(GuidedAction.CHECKBOX_CHECK_SET_ID)
                .checked(PreferencesManager.holidayThemes)
                .build()
        )

        allActions.add(checkbox(ACTION_ID_DEMO, R.string.setting_demo_title,
            R.string.setting_demo_desc, PreferencesManager.demoMode))

        val demoLabel = PreferencesManager.demoLabel
        allActions.add(
            GuidedAction.Builder(context)
                .id(ACTION_ID_DEMO_LABEL)
                .title(R.string.setting_demo_label_title)
                .description(demoLabel)
                .editDescription(demoLabel)
                .descriptionEditable(true)
                .build()
        )

        allActions.add(
            GuidedAction.Builder(context)
                .id(ACTION_ID_REFRESH)
                .title(R.string.setting_refresh_title)
                .description(R.string.setting_refresh_desc)
                .build()
        )

        allActions.add(
            GuidedAction.Builder(context)
                .id(ACTION_ID_REPORT)
                .title(R.string.setting_report_title)
                .description(R.string.setting_report_desc)
                .build()
        )

        allActions.add(picker(ACTION_ID_UPDATE_INTERVAL, R.string.setting_update_interval_title,
            SUB_UPDATE_INTERVAL_BASE, UPDATE_INTERVALS, PreferencesManager.updateCheckInterval))

        allActions.add(
            GuidedAction.Builder(context)
                .id(ACTION_ID_UPDATE)
                .title(R.string.setting_update_title)
                .description(
                    UpdateChecker.pendingVersion(BuildConfig.VERSION_NAME)?.let {
                        getString(R.string.setting_update_pending, it)
                    } ?: getString(R.string.setting_update_desc, BuildConfig.VERSION_NAME)
                )
                .build()
        )

        // Keep only this screen's category, in the same relative order the
        // settings already had — no reordering logic needed beyond that.
        actions.addAll(allActions.filter { categoryOf(it.id) == category })
    }


    /**
     * A one-of-many picker built from a value/label list.
     *
     * The four clock options share this rather than repeating the same twenty
     * lines four times.
     */
    private fun picker(
        id: Long,
        titleRes: Int,
        subBase: Long,
        options: List<Pair<String, Int>>,
        current: String
    ): GuidedAction = GuidedAction.Builder(context)
        .id(id)
        .title(titleRes)
        .description(
            getString(options.firstOrNull { it.first == current }?.second ?: options[0].second)
        )
        .subActions(
            options.mapIndexed { i, (_, labelRes) ->
                GuidedAction.Builder(context).id(subBase + i).title(labelRes).build()
            }
        )
        .build()

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
        if (action.id in SUB_PACK_BASE until SUB_PACK_BASE + SUB_RANGE) {
            val packs = PackManager.cachedPacks(requireContext())
            val index = (action.id - SUB_PACK_BASE).toInt()
            packs.getOrNull(index)?.let { chosen ->
                PreferencesManager.selectedPack = chosen.id
                PreferencesManager.backgroundSource = Backgrounds.SOURCE_PACK

                findActionById(ACTION_ID_PACK)?.description = packLabel(packs)
                notifyActionChanged(findActionPositionById(ACTION_ID_PACK))
                findActionById(ACTION_ID_BACKGROUND)?.description = backgroundLabel()
                notifyActionChanged(findActionPositionById(ACTION_ID_BACKGROUND))

                // Both animated kinds trade the readout for motion, for the
                // same reason: the launcher won't draw our panel over them.
                when (chosen.kind) {
                    PackManager.KIND_VIDEO, PackManager.KIND_LOTTIE ->
                        toast(getString(R.string.toast_video_no_overlay))
                }
                pushUpdate(WallpaperProviderContract.UpdateReason.PREFS_CHANGED)
            }
            return true
        }

        if (action.id in SUB_UPDATE_INTERVAL_BASE until SUB_UPDATE_INTERVAL_BASE + SUB_RANGE) {
            val index = (action.id - SUB_UPDATE_INTERVAL_BASE).toInt()
            UPDATE_INTERVALS.getOrNull(index)?.let { (value, labelRes) ->
                PreferencesManager.updateCheckInterval = value
                findActionById(ACTION_ID_UPDATE_INTERVAL)?.description = getString(labelRes)
                notifyActionChanged(findActionPositionById(ACTION_ID_UPDATE_INTERVAL))
            }
            return true
        }

        // Clock pickers, each in its own bounded range.
        data class PickerSpec(
            val base: Long, val actionId: Long,
            val options: List<Pair<String, Int>>, val setter: (String) -> Unit
        )
        val clockPickers = listOf(
            PickerSpec(SUB_CLOCK_POS_BASE, ACTION_ID_CLOCK_POSITION, CLOCK_POSITIONS)
            { PreferencesManager.clockPosition = it },
            PickerSpec(SUB_CLOCK_SIZE_BASE, ACTION_ID_CLOCK_SIZE, CLOCK_SIZES)
            { PreferencesManager.clockSize = it },
            PickerSpec(SUB_CLOCK_STYLE_BASE, ACTION_ID_CLOCK_STYLE, CLOCK_STYLES)
            { PreferencesManager.clockStyle = it },
            PickerSpec(SUB_CLOCK_HOURS_BASE, ACTION_ID_CLOCK_HOURS, CLOCK_HOUR_MODES)
            { PreferencesManager.clockHours = it }
        )
        for (spec in clockPickers) {
            if (action.id in spec.base until spec.base + SUB_RANGE) {
                val index = (action.id - spec.base).toInt()
                spec.options.getOrNull(index)?.let { (value, labelRes) ->
                    spec.setter(value)
                    findActionById(spec.actionId)?.description = getString(labelRes)
                    notifyActionChanged(findActionPositionById(spec.actionId))
                    pushUpdate(WallpaperProviderContract.UpdateReason.PREFS_CHANGED)
                }
                return true
            }
        }

        if (action.id in SUB_SAFE_BASE until SUB_SAFE_BASE + SUB_RANGE) {
            val index = (action.id - SUB_SAFE_BASE).toInt()
            SAFE_OPTIONS.getOrNull(index)?.let { pct ->
                PreferencesManager.safeBottomPercent = pct
                findActionById(ACTION_ID_SAFE_AREA)?.description =
                    getString(R.string.setting_safe_area_desc, pct)
                notifyActionChanged(findActionPositionById(ACTION_ID_SAFE_AREA))
                pushUpdate(WallpaperProviderContract.UpdateReason.PREFS_CHANGED)
            }
            return true
        }

        if (action.id in SUB_SCALE_BASE until SUB_SCALE_BASE + SUB_RANGE) {
            val index = (action.id - SUB_SCALE_BASE).toInt()
            SCALE_OPTIONS.getOrNull(index)?.let { pct ->
                PreferencesManager.panelScale = pct
                findActionById(ACTION_ID_SCALE)?.description =
                    getString(R.string.setting_scale_desc, pct)
                notifyActionChanged(findActionPositionById(ACTION_ID_SCALE))
                pushUpdate(WallpaperProviderContract.UpdateReason.PREFS_CHANGED)
            }
            return true
        }

        if (action.id in SUB_SAVED_BASE until SUB_SAVED_BASE + SUB_RANGE) {
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

        if (action.id in SUB_SEARCH_BASE until SUB_SEARCH_BASE + SUB_RANGE) {
            val index = (action.id - SUB_SEARCH_BASE).toInt()
            val chosen = searchResults.getOrNull(index)
            searchResults = emptyList()   // collapse the pick-list afterwards
            chosen?.let { addPlace(it) } ?: rebuild()
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
            SUB_SATELLITE -> Backgrounds.SOURCE_SATELLITE
            else -> Backgrounds.SOURCE_SCENE
        }
        PreferencesManager.backgroundSource = source

        // Real cloud imagery only reaches the Americas, Atlantic and Pacific —
        // there's no free, keyless source for the gap in between (roughly the
        // Middle East through China and Southeast Asia). Worth saying so
        // immediately rather than have someone wonder why the background
        // never changed.
        if (source == Backgrounds.SOURCE_SATELLITE &&
            !SatelliteClient.isAvailable(
                PreferencesManager.currentLatitude, PreferencesManager.currentLongitude
            )
        ) {
            toast(getString(R.string.toast_satellite_unavailable))
        }

        findActionById(ACTION_ID_BACKGROUND)?.description = backgroundLabel()
        notifyActionChanged(findActionPositionById(ACTION_ID_BACKGROUND))

        when (source) {
            Backgrounds.SOURCE_LOCAL -> {
                toast(
                    getString(
                        R.string.toast_local_folder,
                        Backgrounds.localFolder(requireContext()).absolutePath
                    )
                )
                // Worth saying up front: a video in that folder wins over the
                // stills, and takes the readout with it.
                toast(getString(R.string.toast_local_video_note))
            }
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
            ACTION_ID_CAT_LOCATION -> pushCategory(CAT_LOCATION)
            ACTION_ID_CAT_APPEARANCE -> pushCategory(CAT_APPEARANCE)
            ACTION_ID_CAT_WEATHER -> pushCategory(CAT_WEATHER)
            ACTION_ID_CAT_CLOCK -> pushCategory(CAT_CLOCK)
            ACTION_ID_CAT_SCREEN -> pushCategory(CAT_SCREEN)
            ACTION_ID_CAT_EXPERIMENTAL -> pushCategory(CAT_EXPERIMENTAL)
            ACTION_ID_CAT_DEMO -> pushCategory(CAT_DEMO)
            ACTION_ID_CAT_HOUSEKEEPING -> pushCategory(CAT_HOUSEKEEPING)
            ACTION_ID_UNITS -> {
                PreferencesManager.useMetric = action.isChecked
                action.description = unitsLabel()
                notifyActionChanged(findActionPositionById(ACTION_ID_UNITS))
                pushUpdate(WallpaperProviderContract.UpdateReason.PREFS_CHANGED)
            }
            ACTION_ID_REDUCE_BURN_IN -> {
                PreferencesManager.reduceBurnIn = action.isChecked
                pushUpdate(WallpaperProviderContract.UpdateReason.PREFS_CHANGED)
            }
            ACTION_ID_NOWCAST -> {
                PreferencesManager.showNowcast = action.isChecked
                pushUpdate(WallpaperProviderContract.UpdateReason.PREFS_CHANGED)
            }
            ACTION_ID_IDLE_FULL -> {
                PreferencesManager.idleFullFrame = action.isChecked
                pushUpdate(WallpaperProviderContract.UpdateReason.PREFS_CHANGED)
            }
            ACTION_ID_CLOCK -> {
                PreferencesManager.showClock = action.isChecked
                if (action.isChecked) toast(getString(R.string.toast_clock_hint))
                // Reveals or hides the date sub-option.
                rebuild()
                pushUpdate(WallpaperProviderContract.UpdateReason.PREFS_CHANGED)
            }
            ACTION_ID_CLOCK_DATE -> {
                PreferencesManager.showClockDate = action.isChecked
                pushUpdate(WallpaperProviderContract.UpdateReason.PREFS_CHANGED)
            }
            ACTION_ID_ADVISORIES -> {
                PreferencesManager.showAdvisories = action.isChecked
                pushUpdate(WallpaperProviderContract.UpdateReason.PREFS_CHANGED)
            }
            ACTION_ID_YESTERDAY -> {
                PreferencesManager.showYesterday = action.isChecked
                pushUpdate(WallpaperProviderContract.UpdateReason.PREFS_CHANGED)
            }
            ACTION_ID_AIR -> {
                PreferencesManager.showAirQuality = action.isChecked
                pushUpdate(WallpaperProviderContract.UpdateReason.PREFS_CHANGED)
            }
            ACTION_ID_AURORA -> {
                PreferencesManager.showAurora = action.isChecked
                pushUpdate(WallpaperProviderContract.UpdateReason.PREFS_CHANGED)
            }
            ACTION_ID_MARINE -> {
                PreferencesManager.showMarine = action.isChecked
                pushUpdate(WallpaperProviderContract.UpdateReason.PREFS_CHANGED)
            }
            ACTION_ID_SAFE_RADAR -> {
                PreferencesManager.safeRadarPalette = action.isChecked
                RadarPalette.clearCache()
                pushUpdate(WallpaperProviderContract.UpdateReason.PREFS_CHANGED)
            }
            ACTION_ID_ALERTS -> {
                PreferencesManager.showAlerts = action.isChecked
                pushUpdate(WallpaperProviderContract.UpdateReason.PREFS_CHANGED)
            }
            ACTION_ID_EXPERIMENTAL -> {
                PreferencesManager.experimentalFeatures = action.isChecked
                // Rebuild so the gated toggles appear or disappear.
                rebuild()
                pushUpdate(WallpaperProviderContract.UpdateReason.PREFS_CHANGED)
            }
            ACTION_ID_ANIMATE_PRECIP -> {
                PreferencesManager.animatePrecipitation = action.isChecked
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
            ACTION_ID_HOLIDAY -> {
                PreferencesManager.holidayThemes = action.isChecked
                action.description = holidayLabel()
                notifyActionChanged(findActionPositionById(ACTION_ID_HOLIDAY))
                pushUpdate(WallpaperProviderContract.UpdateReason.PREFS_CHANGED)
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

                // Without this permission the install intent does nothing and
                // no prompt appears, which reads as the update being broken.
                if (!UpdateChecker.canInstall(requireContext())) {
                    post(action, getString(R.string.update_needs_permission))
                    val opened = UpdateChecker.openInstallPermissionSettings(requireContext())
                    toast(
                        if (opened != null) getString(R.string.update_grant_then_retry)
                        else getString(R.string.update_grant_manually)
                    )
                    return@runOnUiThread
                }

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
                        // Rebuild rather than assigning subActions to the bound
                        // action: Leanback decides an action's sub-action
                        // affordance when it binds, so a list attached
                        // afterwards opens but can't be operated.
                        rebuild()
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
        searchResults = emptyList()
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

    /** Names the theme that would apply right now, so the toggle isn't opaque. */
    private fun holidayLabel(): String {
        if (!PreferencesManager.holidayThemes) {
            return getString(R.string.holiday_off)
        }
        val theme = HolidayThemes.current()
        return if (theme == null) getString(R.string.holiday_none)
        else getString(R.string.holiday_active, theme.label)
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
            Backgrounds.SOURCE_SATELLITE -> R.string.background_satellite
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
