package studio.gooduse.shell

import org.json.JSONObject

enum class GoodUseShellMode { FOCUSED_TASK, MULTI_DESTINATION, LIST_DETAIL }
enum class GoodUseAppearance { SYSTEM, LIGHT, DARK }
enum class GoodUseDepth { FLAT, LOW, MEDIUM, FOCAL }
enum class GoodUseMotion { NONE, MINIMAL, STANDARD }
enum class GoodUseDensity { COMPACT, COMFORTABLE, SPACIOUS }
enum class GoodUsePrimaryActionPlacement { INLINE, BOTTOM_SAFE, TOP_TRAILING, NONE }
enum class GoodUseWideMode { SAME_COLUMN, TWO_COLUMN, LIST_DETAIL, NAV_RAIL }
enum class GoodUseScreenKind { DASHBOARD, LIST, DETAIL, FORM, WORKBENCH, SETTINGS, LEGAL, ONBOARDING, EMPTY, ERROR }
enum class GoodUseComposition { STACK, FORM, LIST, GRID, BOARD, TIMELINE, LIST_DETAIL }
enum class GoodUseScrollMode { NONE, VERTICAL, LIST }
enum class GoodUseAccentRole { PRIMARY, SUCCESS, WARNING, ERROR, NEUTRAL }
enum class GoodUseSlot { HEADER, STATUS, SUMMARY, PRIMARY_CONTENT, SECONDARY_CONTENT, PRIMARY_ACTION, SECONDARY_ACTIONS, FOOTER }
enum class GoodUsePresentation { PLAIN, SURFACE, RAISED, FULL_BLEED }
enum class GoodUseFontWeight { REGULAR, MEDIUM, SEMIBOLD, BOLD }

data class GoodUsePalette(
    val canvas: String,
    val surface: String,
    val surfaceRaised: String,
    val primary: String,
    val onPrimary: String,
    val textPrimary: String,
    val textSecondary: String,
    val border: String,
    val success: String,
    val warning: String,
    val error: String,
)

data class GoodUseSpacing(
    val xxs: Float,
    val xs: Float,
    val small: Float,
    val medium: Float,
    val large: Float,
    val xl: Float,
)

data class GoodUseRadius(val small: Float, val medium: Float, val large: Float)

data class GoodUseTextRole(
    val size: Float,
    val lineHeight: Float,
    val weight: GoodUseFontWeight,
)

data class GoodUseTypography(
    val display: GoodUseTextRole,
    val title: GoodUseTextRole,
    val body: GoodUseTextRole,
    val label: GoodUseTextRole,
    val metric: GoodUseTextRole,
)

data class GoodUseThemeConfig(
    val light: GoodUsePalette,
    val dark: GoodUsePalette,
    val defaultAppearance: GoodUseAppearance,
    val spacing: GoodUseSpacing,
    val radius: GoodUseRadius,
    val typography: GoodUseTypography,
    val depth: GoodUseDepth,
    val motion: GoodUseMotion,
)

data class GoodUseLayoutConfig(
    val compactGutter: Float,
    val regularGutter: Float,
    val maxContentWidth: Float,
    val density: GoodUseDensity,
    val primaryActionPlacement: GoodUsePrimaryActionPlacement,
    val wideMode: GoodUseWideMode,
)

data class GoodUseDestination(
    val route: String,
    val labelKey: String,
    val iconKey: String,
)

data class GoodUseNavigationConfig(
    val startRoute: String,
    val destinations: List<GoodUseDestination>,
)

data class GoodUseAdRailConfig(
    val enabled: Boolean,
    val reserveSpaceWhenEmpty: Boolean,
)

data class GoodUseSectionConfig(
    val slot: GoodUseSlot,
    val presentation: GoodUsePresentation,
)

data class GoodUseScreenConfig(
    val id: String,
    val route: String,
    val kind: GoodUseScreenKind,
    val composition: GoodUseComposition,
    val scroll: GoodUseScrollMode,
    val maxColumns: Int,
    val accentRole: GoodUseAccentRole,
    val sections: List<GoodUseSectionConfig>,
)

data class GoodUseShellConfig(
    val contractVersion: String,
    val appId: String,
    val shellMode: GoodUseShellMode,
    val theme: GoodUseThemeConfig,
    val layout: GoodUseLayoutConfig,
    val navigation: GoodUseNavigationConfig,
    val adRail: GoodUseAdRailConfig,
    val screens: List<GoodUseScreenConfig>,
) {
    init {
        require(contractVersion == "1.0.0") { "Unsupported APP_UI_SHELL_CONFIG version: $contractVersion" }
        require(appId.isNotBlank()) { "appId must not be blank" }
        require(navigation.destinations.isNotEmpty()) { "At least one navigation destination is required" }
        require(navigation.destinations.all { it.route.isNotBlank() && it.labelKey.isNotBlank() && it.iconKey.isNotBlank() }) {
            "navigation destinations require non-blank route, labelKey and iconKey"
        }
        require(navigation.destinations.any { it.route == navigation.startRoute }) {
            "navigation.startRoute must exist in navigation.destinations"
        }
        require(navigation.destinations.map { it.route }.distinct().size == navigation.destinations.size) {
            "navigation destination routes must be unique"
        }
        require(screens.isNotEmpty()) { "At least one screen contract is required" }
        require(screens.all { it.id.isNotBlank() && it.route.isNotBlank() }) { "screen ids and routes must not be blank" }
        require(screens.map { it.id }.distinct().size == screens.size) { "screen ids must be unique" }
        require(screens.all { it.maxColumns in 1..6 }) { "screen maxColumns must be between 1 and 6" }
        require(screens.all { screen -> screen.sections.map { it.slot }.distinct().size == screen.sections.size }) {
            "screen section slots must be unique within each screen state"
        }
    }

    fun screenFor(route: String, screenId: String? = null): GoodUseScreenConfig? =
        if (screenId != null) {
            screens.firstOrNull { it.id == screenId && it.route == route }
        } else {
            screens.firstOrNull { it.route == route }
        }
}

/**
 * Dependency-light Android JSON loader. CI should validate the file against
 * contract/app-ui-shell-config.schema.json before it reaches this runtime loader.
 */
object GoodUseShellConfigLoader {
    fun fromJson(json: String): GoodUseShellConfig {
        val root = JSONObject(json)
        val theme = root.getJSONObject("theme")
        val layout = root.getJSONObject("layout")
        val navigation = root.getJSONObject("navigation")
        val adRail = root.getJSONObject("adRail")

        return GoodUseShellConfig(
            contractVersion = root.getString("contractVersion"),
            appId = root.getString("appId"),
            shellMode = GoodUseShellMode.valueOf(root.getString("shellMode")),
            theme = GoodUseThemeConfig(
                light = palette(theme.getJSONObject("light")),
                dark = palette(theme.getJSONObject("dark")),
                defaultAppearance = GoodUseAppearance.valueOf(theme.getString("defaultAppearance")),
                spacing = theme.getJSONObject("spacing").let {
                    GoodUseSpacing(
                        xxs = it.getDouble("xxs").toFloat(),
                        xs = it.getDouble("xs").toFloat(),
                        small = it.getDouble("small").toFloat(),
                        medium = it.getDouble("medium").toFloat(),
                        large = it.getDouble("large").toFloat(),
                        xl = it.getDouble("xl").toFloat(),
                    )
                },
                radius = theme.getJSONObject("radius").let {
                    GoodUseRadius(
                        small = it.getDouble("small").toFloat(),
                        medium = it.getDouble("medium").toFloat(),
                        large = it.getDouble("large").toFloat(),
                    )
                },
                typography = typography(theme.getJSONObject("typography")),
                depth = GoodUseDepth.valueOf(theme.getString("depth")),
                motion = GoodUseMotion.valueOf(theme.getString("motion")),
            ),
            layout = GoodUseLayoutConfig(
                compactGutter = layout.getDouble("compactGutter").toFloat(),
                regularGutter = layout.getDouble("regularGutter").toFloat(),
                maxContentWidth = layout.getDouble("maxContentWidth").toFloat(),
                density = GoodUseDensity.valueOf(layout.getString("density")),
                primaryActionPlacement = GoodUsePrimaryActionPlacement.valueOf(layout.getString("primaryActionPlacement")),
                wideMode = GoodUseWideMode.valueOf(layout.getString("wideMode")),
            ),
            navigation = GoodUseNavigationConfig(
                startRoute = navigation.getString("startRoute"),
                destinations = navigation.getJSONArray("destinations").let { array ->
                    List(array.length()) { index ->
                        array.getJSONObject(index).let {
                            GoodUseDestination(
                                route = it.getString("route"),
                                labelKey = it.getString("labelKey"),
                                iconKey = it.getString("iconKey"),
                            )
                        }
                    }
                },
            ),
            adRail = GoodUseAdRailConfig(
                enabled = adRail.getBoolean("enabled"),
                reserveSpaceWhenEmpty = adRail.getBoolean("reserveSpaceWhenEmpty"),
            ),
            screens = root.getJSONArray("screens").let { array ->
                List(array.length()) { index ->
                    array.getJSONObject(index).let { screen ->
                        GoodUseScreenConfig(
                            id = screen.getString("id"),
                            route = screen.getString("route"),
                            kind = GoodUseScreenKind.valueOf(screen.getString("kind")),
                            composition = GoodUseComposition.valueOf(screen.getString("composition")),
                            scroll = GoodUseScrollMode.valueOf(screen.getString("scroll")),
                            maxColumns = screen.optInt("maxColumns", 1).coerceIn(1, 6),
                            accentRole = GoodUseAccentRole.valueOf(screen.optString("accentRole", "PRIMARY")),
                            sections = screen.getJSONArray("sections").let { sections ->
                                List(sections.length()) { sectionIndex ->
                                    sections.getJSONObject(sectionIndex).let { section ->
                                        GoodUseSectionConfig(
                                            slot = GoodUseSlot.valueOf(section.getString("slot")),
                                            presentation = GoodUsePresentation.valueOf(section.getString("presentation")),
                                        )
                                    }
                                }
                            },
                        )
                    }
                }
            },
        )
    }

    private fun palette(json: JSONObject) = GoodUsePalette(
        canvas = json.getString("canvas"),
        surface = json.getString("surface"),
        surfaceRaised = json.getString("surfaceRaised"),
        primary = json.getString("primary"),
        onPrimary = json.getString("onPrimary"),
        textPrimary = json.getString("textPrimary"),
        textSecondary = json.getString("textSecondary"),
        border = json.getString("border"),
        success = json.getString("success"),
        warning = json.getString("warning"),
        error = json.getString("error"),
    )

    private fun typography(json: JSONObject) = GoodUseTypography(
        display = textRole(json.getJSONObject("display")),
        title = textRole(json.getJSONObject("title")),
        body = textRole(json.getJSONObject("body")),
        label = textRole(json.getJSONObject("label")),
        metric = textRole(json.getJSONObject("metric")),
    )

    private fun textRole(json: JSONObject) = GoodUseTextRole(
        size = json.getDouble("size").toFloat(),
        lineHeight = json.getDouble("lineHeight").toFloat(),
        weight = GoodUseFontWeight.valueOf(json.getString("weight")),
    )
}

