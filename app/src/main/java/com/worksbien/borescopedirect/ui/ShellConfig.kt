package com.worksbien.borescopedirect.ui

import studio.gooduse.shell.GoodUseAccentRole
import studio.gooduse.shell.GoodUseAdRailConfig
import studio.gooduse.shell.GoodUseAppearance
import studio.gooduse.shell.GoodUseComposition
import studio.gooduse.shell.GoodUseDensity
import studio.gooduse.shell.GoodUseDepth
import studio.gooduse.shell.GoodUseDestination
import studio.gooduse.shell.GoodUseFontWeight
import studio.gooduse.shell.GoodUseLayoutConfig
import studio.gooduse.shell.GoodUseMotion
import studio.gooduse.shell.GoodUseNavigationConfig
import studio.gooduse.shell.GoodUsePalette
import studio.gooduse.shell.GoodUsePrimaryActionPlacement
import studio.gooduse.shell.GoodUseRadius
import studio.gooduse.shell.GoodUseScreenConfig
import studio.gooduse.shell.GoodUseScreenKind
import studio.gooduse.shell.GoodUseScrollMode
import studio.gooduse.shell.GoodUseShellConfig
import studio.gooduse.shell.GoodUseShellMode
import studio.gooduse.shell.GoodUseSpacing
import studio.gooduse.shell.GoodUseTextRole
import studio.gooduse.shell.GoodUseThemeConfig
import studio.gooduse.shell.GoodUseTypography
import studio.gooduse.shell.GoodUseWideMode

fun borescopeShellConfig(cameraLabel: String) = GoodUseShellConfig(
    contractVersion = "1.0.0",
    appId = "borescope-direct",
    shellMode = GoodUseShellMode.FOCUSED_TASK,
    theme = GoodUseThemeConfig(
        light = GoodUsePalette(
            canvas = "#EFF7F6",
            surface = "#FFFFFF",
            surfaceRaised = "#E3F1EF",
            primary = "#006C5D",
            onPrimary = "#FFFFFF",
            textPrimary = "#10201E",
            textSecondary = "#465F5B",
            border = "#BDD2CE",
            success = "#167A52",
            warning = "#8B5C00",
            error = "#B3261E",
        ),
        dark = GoodUsePalette(
            canvas = "#071012",
            surface = "#102022",
            surfaceRaised = "#183033",
            primary = "#66E7D1",
            onPrimary = "#00372F",
            textPrimary = "#E3F4F1",
            textSecondary = "#A7C7C1",
            border = "#36504C",
            success = "#66D69B",
            warning = "#F4BF57",
            error = "#FFB4AB",
        ),
        defaultAppearance = GoodUseAppearance.DARK,
        spacing = GoodUseSpacing(4f, 8f, 12f, 16f, 24f, 32f),
        radius = GoodUseRadius(8f, 14f, 24f),
        typography = GoodUseTypography(
            display = GoodUseTextRole(32f, 38f, GoodUseFontWeight.BOLD),
            title = GoodUseTextRole(22f, 28f, GoodUseFontWeight.SEMIBOLD),
            body = GoodUseTextRole(16f, 23f, GoodUseFontWeight.REGULAR),
            label = GoodUseTextRole(13f, 18f, GoodUseFontWeight.MEDIUM),
            metric = GoodUseTextRole(24f, 30f, GoodUseFontWeight.BOLD),
        ),
        depth = GoodUseDepth.LOW,
        motion = GoodUseMotion.MINIMAL,
    ),
    layout = GoodUseLayoutConfig(
        compactGutter = 0f,
        regularGutter = 0f,
        maxContentWidth = 1600f,
        density = GoodUseDensity.COMFORTABLE,
        primaryActionPlacement = GoodUsePrimaryActionPlacement.NONE,
        wideMode = GoodUseWideMode.SAME_COLUMN,
    ),
    navigation = GoodUseNavigationConfig(
        startRoute = "camera",
        destinations = listOf(GoodUseDestination("camera", cameraLabel, "camera")),
    ),
    adRail = GoodUseAdRailConfig(enabled = false, reserveSpaceWhenEmpty = false),
    screens = listOf(
        GoodUseScreenConfig(
            id = "camera",
            route = "camera",
            kind = GoodUseScreenKind.WORKBENCH,
            composition = GoodUseComposition.STACK,
            scroll = GoodUseScrollMode.NONE,
            maxColumns = 1,
            accentRole = GoodUseAccentRole.PRIMARY,
            sections = emptyList(),
        ),
    ),
)
