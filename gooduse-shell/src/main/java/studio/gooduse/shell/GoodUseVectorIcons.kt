package studio.gooduse.shell

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.min

@Immutable
data class GoodUseVectorRegistry(
    val registryVersion: String,
    val viewBox: Float,
    val strokeWidth: Float,
    val icons: Map<String, List<GoodUseVectorPrimitive>>,
    val osOwnedExceptions: Set<String>,
)

sealed interface GoodUseVectorPrimitive {
    data class Line(val x1: Float, val y1: Float, val x2: Float, val y2: Float) : GoodUseVectorPrimitive
    data class Circle(val cx: Float, val cy: Float, val r: Float, val fill: Boolean) : GoodUseVectorPrimitive
    data class Rect(val x: Float, val y: Float, val w: Float, val h: Float, val fill: Boolean) : GoodUseVectorPrimitive
    data class PathShape(val commands: List<GoodUsePathCommand>, val fill: Boolean) : GoodUseVectorPrimitive
}

sealed interface GoodUsePathCommand {
    data class Move(val x: Float, val y: Float) : GoodUsePathCommand
    data class LineTo(val x: Float, val y: Float) : GoodUsePathCommand
    data object Close : GoodUsePathCommand
}

object GoodUseVectorRegistryLoader {
    fun bundled(context: Context): GoodUseVectorRegistry =
        context.assets.open("gooduse-icon-registry-v1.json").bufferedReader().use { fromJson(it.readText()) }

    fun fromJson(json: String): GoodUseVectorRegistry {
        val root = JSONObject(json)
        val viewBox = root.getDouble("viewBox").toFloat()
        val strokeWidth = root.getDouble("strokeWidth").toFloat()
        require(viewBox > 0f && strokeWidth > 0f) { "GoodUse icon registry metrics must be positive" }
        require(root.getString("lineCap") == "ROUND" && root.getString("lineJoin") == "ROUND") {
            "GOODUSE_ICON_REGISTRY 1.0.0 requires ROUND lineCap and lineJoin"
        }

        val iconsJson = root.getJSONObject("icons")
        val icons = buildMap {
            iconsJson.keys().forEach { key ->
                put(key, primitives(iconsJson.getJSONArray(key)))
            }
        }
        val exceptions = root.getJSONArray("osOwnedExceptions").let { array ->
            buildSet { repeat(array.length()) { add(array.getString(it)) } }
        }
        return GoodUseVectorRegistry(
            registryVersion = root.getString("registryVersion"),
            viewBox = viewBox,
            strokeWidth = strokeWidth,
            icons = icons,
            osOwnedExceptions = exceptions,
        )
    }

    private fun primitives(array: JSONArray): List<GoodUseVectorPrimitive> =
        List(array.length()) { index ->
            val item = array.getJSONObject(index)
            val fill = item.optBoolean("fill", false)
            when (item.getString("type")) {
                "line" -> GoodUseVectorPrimitive.Line(
                    item.float("x1"), item.float("y1"), item.float("x2"), item.float("y2"),
                )
                "circle" -> GoodUseVectorPrimitive.Circle(
                    item.float("cx"), item.float("cy"), item.float("r").also { require(it >= 0f) }, fill,
                )
                "rect" -> GoodUseVectorPrimitive.Rect(
                    item.float("x"), item.float("y"),
                    item.float("w").also { require(it >= 0f) },
                    item.float("h").also { require(it >= 0f) },
                    fill,
                )
                "path" -> GoodUseVectorPrimitive.PathShape(
                    commands = item.getJSONArray("commands").also { require(it.length() > 0) }.let { commands ->
                        List(commands.length()) { commandIndex ->
                            command(commands.getJSONObject(commandIndex))
                        }
                    },
                    fill = fill,
                )
                else -> error("Unsupported GoodUse icon primitive: ${item.getString("type")}")
            }
        }

    private fun command(json: JSONObject): GoodUsePathCommand {
        val values = json.getJSONArray("v")
        return when (json.getString("op")) {
            "M" -> {
                require(values.length() >= 2) { "M command requires two coordinates" }
                GoodUsePathCommand.Move(values.double(0), values.double(1))
            }
            "L" -> {
                require(values.length() >= 2) { "L command requires two coordinates" }
                GoodUsePathCommand.LineTo(values.double(0), values.double(1))
            }
            "Z" -> GoodUsePathCommand.Close
            else -> error("Unsupported GoodUse icon path command: ${json.getString("op")}")
        }
    }

    private fun JSONObject.float(key: String): Float = getDouble(key).toFloat()
    private fun JSONArray.double(index: Int): Float = getDouble(index).toFloat()
}

/**
 * Renders an app-owned semantic icon from the shared normalized vector master.
 * Android and iOS consume the same registry geometry. OS-owned exceptions such
 * as Back/Forward/Share should use native platform symbols instead.
 */
@Composable
fun GoodUseVectorIcon(
    iconKey: String,
    registry: GoodUseVectorRegistry,
    modifier: Modifier = Modifier,
    color: Color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
) {
    val primitives = registry.icons[iconKey] ?: error("Unknown GoodUse icon key: $iconKey")
    Canvas(modifier = modifier) {
        val scale = min(size.width, size.height) / registry.viewBox
        val dx = (size.width - registry.viewBox * scale) / 2f
        val dy = (size.height - registry.viewBox * scale) / 2f
        val stroke = Stroke(
            width = registry.strokeWidth * scale,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        primitives.forEach { primitive ->
            drawPrimitive(primitive, scale, dx, dy, color, stroke)
        }
    }
}

private fun DrawScope.drawPrimitive(
    primitive: GoodUseVectorPrimitive,
    scale: Float,
    dx: Float,
    dy: Float,
    color: Color,
    stroke: Stroke,
) {
    fun p(x: Float, y: Float) = Offset(dx + x * scale, dy + y * scale)
    when (primitive) {
        is GoodUseVectorPrimitive.Line -> drawLine(
            color = color,
            start = p(primitive.x1, primitive.y1),
            end = p(primitive.x2, primitive.y2),
            strokeWidth = stroke.width,
            cap = StrokeCap.Round,
        )
        is GoodUseVectorPrimitive.Circle -> {
            if (primitive.fill) {
                drawCircle(color = color, radius = primitive.r * scale, center = p(primitive.cx, primitive.cy))
            } else {
                drawCircle(color = color, radius = primitive.r * scale, center = p(primitive.cx, primitive.cy), style = stroke)
            }
        }
        is GoodUseVectorPrimitive.Rect -> {
            val topLeft = p(primitive.x, primitive.y)
            val rectSize = Size(primitive.w * scale, primitive.h * scale)
            if (primitive.fill) drawRect(color, topLeft, rectSize) else drawRect(color, topLeft, rectSize, style = stroke)
        }
        is GoodUseVectorPrimitive.PathShape -> {
            val path = Path()
            primitive.commands.forEach { command ->
                when (command) {
                    is GoodUsePathCommand.Move -> p(command.x, command.y).let { path.moveTo(it.x, it.y) }
                    is GoodUsePathCommand.LineTo -> p(command.x, command.y).let { path.lineTo(it.x, it.y) }
                    GoodUsePathCommand.Close -> path.close()
                }
            }
            if (primitive.fill) drawPath(path, color) else drawPath(path, color, style = stroke)
        }
    }
}

