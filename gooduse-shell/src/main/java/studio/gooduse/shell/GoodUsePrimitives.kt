package studio.gooduse.shell

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

enum class GoodUseTextRoleName { DISPLAY, TITLE, BODY, LABEL, METRIC }

@Composable
fun GoodUseText(
    text: String,
    role: GoodUseTextRoleName,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onBackground,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val typography = LocalGoodUseShell.current.config.theme.typography
    val style = when (role) {
        GoodUseTextRoleName.DISPLAY -> typography.display
        GoodUseTextRoleName.TITLE -> typography.title
        GoodUseTextRoleName.BODY -> typography.body
        GoodUseTextRoleName.LABEL -> typography.label
        GoodUseTextRoleName.METRIC -> typography.metric
    }.toTextStyle()

    Text(
        text = text,
        modifier = modifier,
        color = color,
        style = style,
        maxLines = maxLines,
        overflow = overflow,
    )
}

@Composable
fun GoodUseStack(
    modifier: Modifier = Modifier,
    scrollable: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val spacing = LocalGoodUseShell.current.config.theme.spacing
    val scrollModifier = if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier

    Column(
        modifier = modifier
            .then(scrollModifier)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.small.dp),
        content = content,
    )
}

@Composable
fun GoodUseSurface(
    presentation: GoodUsePresentation,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val runtime = LocalGoodUseShell.current
    val theme = runtime.config.theme
    val radius = when (presentation) {
        GoodUsePresentation.PLAIN, GoodUsePresentation.FULL_BLEED -> 0.dp
        GoodUsePresentation.SURFACE -> theme.radius.medium.dp
        GoodUsePresentation.RAISED -> theme.radius.large.dp
    }
    val elevation = when {
        presentation != GoodUsePresentation.RAISED -> 0.dp
        theme.depth == GoodUseDepth.FLAT -> 0.dp
        theme.depth == GoodUseDepth.LOW -> 2.dp
        theme.depth == GoodUseDepth.MEDIUM -> 4.dp
        else -> 6.dp
    }
    val background = when (presentation) {
        GoodUsePresentation.PLAIN, GoodUsePresentation.FULL_BLEED -> Color.Transparent
        GoodUsePresentation.SURFACE -> MaterialTheme.colorScheme.surface
        GoodUsePresentation.RAISED -> MaterialTheme.colorScheme.surfaceVariant
    }

    Box(
        modifier = modifier
            .then(if (elevation > 0.dp) Modifier.shadow(elevation, RoundedCornerShape(radius)) else Modifier)
            .then(if (radius > 0.dp) Modifier.clip(RoundedCornerShape(radius)) else Modifier)
            .background(background)
            .then(
                if (presentation == GoodUsePresentation.SURFACE || presentation == GoodUsePresentation.RAISED) {
                    Modifier.border(
                        BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                        RoundedCornerShape(radius),
                    )
                } else {
                    Modifier
                },
            )
            .then(
                if (presentation == GoodUsePresentation.SURFACE || presentation == GoodUsePresentation.RAISED) {
                    Modifier.padding(theme.spacing.medium.dp)
                } else {
                    Modifier
                },
            ),
    ) {
        content()
    }
}

@Composable
fun GoodUsePrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val theme = LocalGoodUseShell.current.config.theme
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        shape = RoundedCornerShape(theme.radius.small.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
            disabledContentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        elevation = null,
        contentPadding = PaddingValues(horizontal = theme.spacing.small.dp, vertical = 0.dp),
    ) {
        GoodUseText(text = text, role = GoodUseTextRoleName.LABEL, color = MaterialTheme.colorScheme.onPrimary)
    }
}

@Composable
fun GoodUseSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val theme = LocalGoodUseShell.current.config.theme
    val contentColor = MaterialTheme.colorScheme.onSurface
    val outlineColor = MaterialTheme.colorScheme.outline
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        shape = RoundedCornerShape(theme.radius.small.dp),
        border = BorderStroke(1.dp, outlineColor.copy(alpha = if (enabled) 1f else 0.45f)),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = contentColor,
            disabledContentColor = contentColor.copy(alpha = 0.45f),
        ),
        contentPadding = PaddingValues(horizontal = theme.spacing.small.dp, vertical = 0.dp),
    ) {
        GoodUseText(
            text = text,
            role = GoodUseTextRoleName.LABEL,
            color = if (enabled) contentColor else contentColor.copy(alpha = 0.45f),
        )
    }
}

/**
 * Shared responsive two-pane primitive. The app owns list/detail data and
 * selection state; the shell owns the 40/60 reflow rule.
 */
@Composable
fun GoodUseAdaptiveListDetail(
    modifier: Modifier = Modifier,
    compactShowDetail: Boolean,
    listPane: @Composable () -> Unit,
    detailPane: @Composable () -> Unit,
) {
    val runtime = LocalGoodUseShell.current
    val spacing = runtime.config.theme.spacing
    val wide = runtime.viewport == GoodUseViewport.EXPANDED ||
        (runtime.viewport == GoodUseViewport.MEDIUM && runtime.config.layout.wideMode == GoodUseWideMode.LIST_DETAIL)

    if (wide) {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.medium.dp),
        ) {
            Box(modifier = Modifier.weight(0.40f)) { listPane() }
            Box(modifier = Modifier.weight(0.60f)) { detailPane() }
        }
    } else {
        Box(modifier = modifier.fillMaxWidth()) {
            if (compactShowDetail) detailPane() else listPane()
        }
    }
}

@Composable
fun GoodUseAdaptiveGrid(
    columns: Int,
    modifier: Modifier = Modifier,
    content: LazyGridScope.() -> Unit,
) {
    val runtime = LocalGoodUseShell.current
    val requested = columns.coerceIn(1, 6)
    val cap = when (runtime.viewport) {
        GoodUseViewport.COMPACT -> 1
        GoodUseViewport.MEDIUM -> requested.coerceAtMost(2)
        GoodUseViewport.EXPANDED -> requested
    }
    val spacing = runtime.config.theme.spacing.small.dp

    LazyVerticalGrid(
        columns = GridCells.Fixed(cap),
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content,
    )
}

@Composable
fun GoodUseActionRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val spacing = LocalGoodUseShell.current.config.theme.spacing
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.small.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

