package studio.gooduse.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Renders the approved screen-section contract without app-specific layout glue.
 * App code supplies semantic slot content; this host owns section order,
 * presentation wrappers, vertical scroll ownership and list/detail reflow.
 */
@Composable
fun GoodUseScreenHost(
    modifier: Modifier = Modifier,
    screen: GoodUseScreenConfig = LocalGoodUseShell.current.screen
        ?: error("Current route/screen state has no GoodUseScreenConfig"),
    compactShowDetail: Boolean = false,
    slotContent: @Composable (GoodUseSlot) -> Unit,
) {
    val runtime = LocalGoodUseShell.current
    val spacing = runtime.config.theme.spacing
    val scrollModifier = if (screen.scroll == GoodUseScrollMode.VERTICAL) {
        Modifier.verticalScroll(rememberScrollState())
    } else {
        Modifier
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .then(scrollModifier),
        verticalArrangement = Arrangement.spacedBy(spacing.small.dp),
    ) {
        if (screen.composition == GoodUseComposition.LIST_DETAIL) {
            val primary = screen.sections.firstOrNull { it.slot == GoodUseSlot.PRIMARY_CONTENT }
            val secondary = screen.sections.firstOrNull { it.slot == GoodUseSlot.SECONDARY_CONTENT }
            var pairRendered = false

            screen.sections.forEach { section ->
                val isPane = section.slot == GoodUseSlot.PRIMARY_CONTENT || section.slot == GoodUseSlot.SECONDARY_CONTENT
                if (isPane && primary != null && secondary != null) {
                    if (!pairRendered) {
                        GoodUseAdaptiveListDetail(
                            modifier = Modifier.fillMaxWidth(),
                            compactShowDetail = compactShowDetail,
                            listPane = { renderSection(primary, slotContent) },
                            detailPane = { renderSection(secondary, slotContent) },
                        )
                        pairRendered = true
                    }
                } else if (!isPane) {
                    renderSection(section, slotContent)
                } else {
                    renderSection(section, slotContent)
                }
            }
        } else {
            screen.sections.forEach { section -> renderSection(section, slotContent) }
        }
    }
}

@Composable
private fun renderSection(
    section: GoodUseSectionConfig,
    slotContent: @Composable (GoodUseSlot) -> Unit,
) {
    GoodUseSurface(
        presentation = section.presentation,
        modifier = Modifier.fillMaxWidth(),
    ) {
        slotContent(section.slot)
    }
}

