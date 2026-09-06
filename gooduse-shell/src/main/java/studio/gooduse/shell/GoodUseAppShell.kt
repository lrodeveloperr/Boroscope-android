package studio.gooduse.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class GoodUseViewport { COMPACT, MEDIUM, EXPANDED }

@Immutable
data class GoodUseShellRuntime(
    val config: GoodUseShellConfig,
    val viewport: GoodUseViewport,
    val isDark: Boolean,
    val horizontalGutter: Dp,
    val maxContentWidth: Dp,
    val screen: GoodUseScreenConfig?,
)

val LocalGoodUseShell = staticCompositionLocalOf<GoodUseShellRuntime> {
    error("GoodUseAppShell must wrap app-owned UI")
}

@Composable
fun GoodUseAppShell(
    config: GoodUseShellConfig,
    currentRoute: String,
    currentScreenId: String? = null,
    onNavigate: (String) -> Unit,
    label: (String) -> String,
    icon: @Composable (iconKey: String, selected: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    adContent: (@Composable () -> Unit)? = null,
    reservedAdHeight: Dp = 0.dp,
    bottomPrimaryAction: (@Composable () -> Unit)? = null,
    content: @Composable (GoodUseShellRuntime) -> Unit,
) {
    if (config.adRail.enabled && config.adRail.reserveSpaceWhenEmpty && adContent == null) {
        require(reservedAdHeight > 0.dp) {
            "reserveSpaceWhenEmpty=true requires a positive adaptive reservedAdHeight when adContent is absent"
        }
    }

    val systemDark = isSystemInDarkTheme()
    val useDark = when (config.theme.defaultAppearance) {
        GoodUseAppearance.SYSTEM -> systemDark
        GoodUseAppearance.LIGHT -> false
        GoodUseAppearance.DARK -> true
    }
    val palette = if (useDark) config.theme.dark else config.theme.light

    MaterialTheme(
        colorScheme = if (useDark) palette.toDarkScheme() else palette.toLightScheme(),
        typography = config.theme.typography.toMaterialTypography(),
    ) {
        BoxWithConstraints(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            val viewport = when {
                maxWidth < 600.dp -> GoodUseViewport.COMPACT
                maxWidth < 840.dp -> GoodUseViewport.MEDIUM
                else -> GoodUseViewport.EXPANDED
            }
            val gutter = if (viewport == GoodUseViewport.COMPACT) {
                config.layout.compactGutter.dp
            } else {
                config.layout.regularGutter.dp
            }
            val runtime = GoodUseShellRuntime(
                config = config,
                viewport = viewport,
                isDark = useDark,
                horizontalGutter = gutter,
                maxContentWidth = config.layout.maxContentWidth.dp,
                screen = config.screenFor(currentRoute, currentScreenId),
            )

            val useRail = config.shellMode == GoodUseShellMode.MULTI_DESTINATION &&
                viewport == GoodUseViewport.EXPANDED &&
                config.layout.wideMode == GoodUseWideMode.NAV_RAIL
            val useBottomNavigation = config.shellMode == GoodUseShellMode.MULTI_DESTINATION && !useRail
            val bottomActionInShell = config.layout.primaryActionPlacement == GoodUsePrimaryActionPlacement.BOTTOM_SAFE
            val showAdRail = config.adRail.enabled && (adContent != null || config.adRail.reserveSpaceWhenEmpty)
            val hasBottomChrome = useBottomNavigation || showAdRail || (bottomActionInShell && bottomPrimaryAction != null)

            CompositionLocalProvider(LocalGoodUseShell provides runtime) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background,
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    bottomBar = {
                        if (hasBottomChrome) {
                            GoodUseBottomChrome(
                                config = config,
                                currentRoute = currentRoute,
                                onNavigate = onNavigate,
                                label = label,
                                icon = icon,
                                showNavigation = useBottomNavigation,
                                showAdRail = showAdRail,
                                adContent = adContent,
                                reservedAdHeight = reservedAdHeight,
                                primaryAction = if (bottomActionInShell) bottomPrimaryAction else null,
                            )
                        }
                    },
                ) { innerPadding ->
                    val bodyInsets = if (hasBottomChrome) {
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
                    } else {
                        WindowInsets.safeDrawing
                    }

                    if (useRail) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                                .windowInsetsPadding(bodyInsets)
                                .imePadding(),
                        ) {
                            GoodUseRail(
                                config = config,
                                currentRoute = currentRoute,
                                onNavigate = onNavigate,
                                label = label,
                                icon = icon,
                            )
                            GoodUseContentFrame(
                                config = config,
                                gutter = gutter,
                                modifier = Modifier.weight(1f),
                            ) { content(runtime) }
                        }
                    } else {
                        GoodUseContentFrame(
                            config = config,
                            gutter = gutter,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                                .windowInsetsPadding(bodyInsets)
                                .imePadding(),
                        ) { content(runtime) }
                    }
                }
            }
        }
    }
}

@Composable
private fun GoodUseContentFrame(
    config: GoodUseShellConfig,
    gutter: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier, contentAlignment = Alignment.TopCenter) {
        Box(
            modifier = Modifier
                .widthIn(max = config.layout.maxContentWidth.dp)
                .fillMaxWidth()
                .padding(horizontal = gutter),
        ) { content() }
    }
}

@Composable
private fun GoodUseBottomChrome(
    config: GoodUseShellConfig,
    currentRoute: String,
    onNavigate: (String) -> Unit,
    label: (String) -> String,
    icon: @Composable (String, Boolean) -> Unit,
    showNavigation: Boolean,
    showAdRail: Boolean,
    adContent: (@Composable () -> Unit)?,
    reservedAdHeight: Dp,
    primaryAction: (@Composable () -> Unit)?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
            ),
    ) {
        if (primaryAction != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = config.layout.compactGutter.dp,
                        vertical = config.theme.spacing.xs.dp,
                    ),
            ) { primaryAction() }
        }

        if (showNavigation) {
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = config.theme.spacing.small.dp,
                        vertical = config.theme.spacing.xs.dp,
                    ),
                horizontalArrangement = Arrangement.spacedBy(config.theme.spacing.xs.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                config.navigation.destinations.forEach { destination ->
                    val selected = destination.route == currentRoute
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .selectable(
                                selected = selected,
                                onClick = { if (!selected) onNavigate(destination.route) },
                                role = Role.Tab,
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Box(modifier = Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                            icon(destination.iconKey, selected)
                        }
                        Text(
                            text = label(destination.labelKey),
                            style = config.theme.typography.label.toTextStyle(),
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        if (showAdRail) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (adContent == null) Modifier.height(reservedAdHeight) else Modifier),
                contentAlignment = Alignment.Center,
            ) { adContent?.invoke() }
        }
    }
}

@Composable
private fun GoodUseRail(
    config: GoodUseShellConfig,
    currentRoute: String,
    onNavigate: (String) -> Unit,
    label: (String) -> String,
    icon: @Composable (String, Boolean) -> Unit,
) {
    val outline = MaterialTheme.colorScheme.outline
    val railWidth = (84f + 2f * config.theme.spacing.xs).dp
    Column(
        modifier = Modifier
            .width(railWidth)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .drawBehind {
                drawLine(
                    color = outline,
                    start = androidx.compose.ui.geometry.Offset(size.width - 0.5.dp.toPx(), 0f),
                    end = androidx.compose.ui.geometry.Offset(size.width - 0.5.dp.toPx(), size.height),
                    strokeWidth = 0.5.dp.toPx(),
                )
            }
            .padding(
                horizontal = config.theme.spacing.xs.dp,
                vertical = config.theme.spacing.small.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(config.theme.spacing.small.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        config.navigation.destinations.forEach { destination ->
            val selected = destination.route == currentRoute
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .selectable(
                        selected = selected,
                        onClick = { if (!selected) onNavigate(destination.route) },
                        role = Role.Tab,
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(modifier = Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                    icon(destination.iconKey, selected)
                }
                Text(
                    text = label(destination.labelKey),
                    style = config.theme.typography.label.toTextStyle(),
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun GoodUseTypography.toMaterialTypography() = Typography(
    displayMedium = display.toTextStyle(),
    headlineLarge = display.toTextStyle(),
    headlineMedium = title.toTextStyle(),
    headlineSmall = title.toTextStyle(),
    titleLarge = title.toTextStyle(),
    titleMedium = title.toTextStyle(),
    bodyLarge = body.toTextStyle(),
    bodyMedium = body.toTextStyle(),
    labelLarge = label.toTextStyle(),
    labelMedium = label.toTextStyle(),
)

internal fun GoodUseTextRole.toTextStyle() = TextStyle(
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = when (weight) {
        GoodUseFontWeight.REGULAR -> FontWeight.Normal
        GoodUseFontWeight.MEDIUM -> FontWeight.Medium
        GoodUseFontWeight.SEMIBOLD -> FontWeight.SemiBold
        GoodUseFontWeight.BOLD -> FontWeight.Bold
    },
)

private fun GoodUsePalette.toLightScheme() = lightColorScheme(
    primary = primary.toComposeColor(),
    onPrimary = onPrimary.toComposeColor(),
    background = canvas.toComposeColor(),
    surface = surface.toComposeColor(),
    surfaceVariant = surfaceRaised.toComposeColor(),
    onBackground = textPrimary.toComposeColor(),
    onSurface = textPrimary.toComposeColor(),
    onSurfaceVariant = textSecondary.toComposeColor(),
    outline = border.toComposeColor(),
    error = error.toComposeColor(),
)

private fun GoodUsePalette.toDarkScheme() = darkColorScheme(
    primary = primary.toComposeColor(),
    onPrimary = onPrimary.toComposeColor(),
    background = canvas.toComposeColor(),
    surface = surface.toComposeColor(),
    surfaceVariant = surfaceRaised.toComposeColor(),
    onBackground = textPrimary.toComposeColor(),
    onSurface = textPrimary.toComposeColor(),
    onSurfaceVariant = textSecondary.toComposeColor(),
    outline = border.toComposeColor(),
    error = error.toComposeColor(),
)

internal fun String.toComposeColor(): Color {
    val value = removePrefix("#")
    val argb = when (value.length) {
        6 -> 0xFF000000L or value.toLong(16)
        8 -> value.toLong(16)
        else -> error("Expected #RRGGBB or #AARRGGBB, got $this")
    }
    return Color(argb)
}

