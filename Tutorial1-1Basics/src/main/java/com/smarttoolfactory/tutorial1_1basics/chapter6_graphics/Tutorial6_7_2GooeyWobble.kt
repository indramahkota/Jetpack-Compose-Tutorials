@file:OptIn(ExperimentalComposeUiApi::class)

package com.smarttoolfactory.tutorial1_1basics.chapter6_graphics

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

@Composable
fun GooeyStretchAndSnapSample(
    modifier: Modifier = Modifier
) {
    val pathDynamic = remember { Path() }
    val pathStatic = remember { Path() }
    val bridgePath = remember { Path() }
    val unionPath = remember { Path() }
    val tmpUnion = remember { Path() }

    var currentPosition by remember { mutableStateOf(Offset.Unspecified) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    val paint = remember {
        Paint().apply {
            style = PaintingStyle.Stroke
            strokeWidth = 4f
        }
    }
    var isPaintSetUp by remember { mutableStateOf(false) }

    // Event-driven stretch that springs back after detach
    val stretchAnim = remember { Animatable(0f) }

    // --- Attachment hysteresis ---
    var attached by remember { mutableStateOf(true) }
    val detachThreshold = 2f   // px
    val attachThreshold = -2f  // px

    // --- Stable "pulling apart" detection (EMA + hysteresis) ---
    var prevGap by remember { mutableFloatStateOf(Float.NaN) }
    var gapVelEma by remember { mutableFloatStateOf(0f) }
    var pullingApart by remember { mutableStateOf(false) }

    // --- Stable angles (avoid atan2 noise when centers almost coincide) ---
    var lastAngleDyn by remember { mutableFloatStateOf(0f) }
    var lastAngleSta by remember { mutableFloatStateOf(Math.PI.toFloat()) }

    // --- Geometry in composition ---
    val center = remember(canvasSize) { Offset(canvasSize.width / 2f, canvasSize.height / 2f) }
    val dynamicCenter = if (currentPosition == Offset.Unspecified) center else currentPosition
    val staticCenter = center

    val rDynamic = 150f
    val rStatic = 150f
    val rSum = rDynamic + rStatic

    val delta = dynamicCenter - staticCenter
    val d = delta.getDistance()
    val gap = d - rSum // <=0 attached/touching, >0 detached

    // Update attached state with hysteresis
    LaunchedEffect(gap) {
        attached = when {
            attached && gap > detachThreshold -> false
            !attached && gap < attachThreshold -> true
            else -> attached
        }
    }

    // Update pullingApart with EMA velocity + hysteresis
    LaunchedEffect(gap) {
        val pg = prevGap
        if (!pg.isNaN()) {
            val gapDelta = gap - pg // positive => moving apart (toward detachment)
            // EMA to remove slow jitter/flicker
            val alpha = 0.25f
            gapVelEma = gapVelEma + (gapDelta - gapVelEma) * alpha
        }
        prevGap = gap

        // Hysteresis thresholds (px/frame-ish)
        val on = 0.20f
        val off = -0.20f

        pullingApart = when {
            pullingApart && gapVelEma < off -> false
            !pullingApart && gapVelEma > on -> true
            else -> pullingApart
        }
    }

    // Stable angles: only update when distance is meaningful
    val angleDyn: Float
    val angleSta: Float
    if (d > 6f) {
        // dynamic -> static
        val vDynToSta = staticCenter - dynamicCenter
        val vStaToDyn = dynamicCenter - staticCenter
        lastAngleDyn = atan2(vDynToSta.y, vDynToSta.x)
        lastAngleSta = atan2(vStaToDyn.y, vStaToDyn.x)
        angleDyn = lastAngleDyn
        angleSta = lastAngleSta
    } else {
        angleDyn = lastAngleDyn
        angleSta = lastAngleSta
    }

    fun smoothstep(x: Float) = x * x * (3f - 2f * x)

    /**
     * Stretch/bridge should only exist while ATTACHED, only when PULLING APART,
     * and only when gap is close to 0 but still negative.
     */
    val stretchBandPx = 90f
    val bridgeBandPx = 28f

    val targetStretch =
        if (attached && pullingApart) {
            // gap: [-stretchBand..0] -> [0..1]
            val t = ((gap + stretchBandPx) / stretchBandPx).coerceIn(0f, 1f)
            smoothstep(t)
        } else 0f

    val bridgeStrength =
        if (attached && pullingApart) {
            // Additional hard gate: bridge ONLY when gap is near 0 (still negative)
            // Prevent any bridge deeper inside overlap
            if (gap < 0f && gap > -bridgeBandPx) {
                val t = ((gap + bridgeBandPx) / bridgeBandPx).coerceIn(0f, 1f)
                smoothstep(t)
            } else 0f
        } else 0f

    // Drive stretch:
    // - while attached: snap-follow
    // - when detached: spring back
    LaunchedEffect(targetStretch, attached) {
        if (attached) {
            stretchAnim.snapTo(targetStretch)
        } else {
            stretchAnim.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = 0.35f,
                    stiffness = 320f
                )
            )
        }
    }

    val stretchPx = 60f * stretchAnim.value
    val neckTightness = 6.5f

    Canvas(
        modifier = modifier
            .onSizeChanged { canvasSize = it }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { currentPosition = it }
                ) { change, _ ->
                    currentPosition = change.position
                    change.consume()
                }
            }
    ) {
        pathDynamic.reset()
        buildStretchedBlobPath(
            out = pathDynamic,
            center = dynamicCenter,
            radius = rDynamic,
            points = 140,
            stretchPx = stretchPx,
            dirAngle = angleDyn,
            focusPower = neckTightness,
            stretchBias = 1.0f
        )

        pathStatic.reset()
        buildStretchedBlobPath(
            out = pathStatic,
            center = staticCenter,
            radius = rStatic,
            points = 160,
            stretchPx = stretchPx * 0.8f,
            dirAngle = angleSta,
            focusPower = neckTightness,
            stretchBias = 0.9f
        )

        bridgePath.reset()
        if (bridgeStrength > 0f) {
            buildBridgePath(
                out = bridgePath,
                c1 = dynamicCenter,
                r1 = rDynamic,
                a1 = angleDyn,
                c2 = staticCenter,
                r2 = rStatic,
                a2 = angleSta,
                strength = bridgeStrength
            )
        }

        if (!isPaintSetUp) {
            paint.shader = LinearGradientShader(
                from = Offset.Zero,
                to = Offset(size.width, size.height),
                colors = listOf(Color(0xffFFEB3B), Color(0xffE91E63)),
                tileMode = TileMode.Clamp
            )
            isPaintSetUp = true
        }

        // Union: (dynamic ∪ static) ∪ bridge
        unionPath.reset()
        unionPath.op(pathDynamic, pathStatic, PathOperation.Union)

        if (!bridgePath.isEmpty) {
            tmpUnion.reset()
            tmpUnion.op(unionPath, bridgePath, PathOperation.Union)
            unionPath.reset()
            unionPath.addPath(tmpUnion)
        }

        drawIntoCanvas { canvas ->
            canvas.drawPath(unionPath, paint)
        }
    }
}

/**
 * Stretch model: base circle + directional radius term concentrated near dirAngle ("neck")
 */
private fun buildStretchedBlobPath(
    out: Path,
    center: Offset,
    radius: Float,
    points: Int,
    stretchPx: Float,
    dirAngle: Float,
    focusPower: Float,
    stretchBias: Float
) {
    if (points < 16) return

    val step = (2.0 * Math.PI / points).toFloat()
    fun smoothstep(x: Float) = x * x * (3f - 2f * x)

    fun neckMask(theta: Float): Float {
        val facing = cos(theta - dirAngle).coerceIn(-1f, 1f)
        val focused = smoothstep(((facing + 1f) * 0.5f))
        return focused.pow(focusPower)
    }

    fun radial(theta: Float): Float {
        val m = neckMask(theta)

        val neckPull = (stretchPx * stretchBias) * m
        val far = 1f - m
        val compress = -0.22f * stretchPx * far

        return radius + neckPull + compress
    }

    var theta = 0f
    var r = radial(theta)
    out.moveTo(center.x + r * cos(theta), center.y + r * sin(theta))

    for (i in 1..points) {
        theta = i * step
        r = radial(theta)
        out.lineTo(center.x + r * cos(theta), center.y + r * sin(theta))
    }

    out.close()
}

/**
 * Separate "bridge" path (thin ligament) connecting facing arcs.
 * strength in [0..1]:
 * - shrinks angular span (thin string near detach)
 * - reduces handle length (tighter near detach)
 */
private fun buildBridgePath(
    out: Path,
    c1: Offset,
    r1: Float,
    a1: Float,
    c2: Offset,
    r2: Float,
    a2: Float,
    strength: Float
) {
    fun smoothstep(x: Float) = x * x * (3f - 2f * x)
    fun clamp01(x: Float) = x.coerceIn(0f, 1f)

    val t = smoothstep(clamp01(strength))

    // Ligament thickness in px:
    // - when t small (far from detach): thicker
    // - when t→1 (near detach): very thin string
    val thickMax = 26f
    val thickMin = 4f
    val thickness = thickMax + (thickMin - thickMax) * t

    // Convert thickness to angle span per-circle: alpha = asin(thickness / r)
    // Clamp to avoid NaN if thickness > r.
    fun alphaFor(r: Float): Float {
        val x = (thickness / max(1f, r)).coerceIn(0f, 0.95f)
        return asin(x)
    }

    val alpha1 = alphaFor(r1)
    val alpha2 = alphaFor(r2)

    fun point(center: Offset, r: Float, ang: Float): Offset =
        Offset(center.x + r * cos(ang), center.y + r * sin(ang))

    fun tangentUnit(ang: Float): Offset =
        Offset(-sin(ang), cos(ang))

    // Connection points (top/bottom around facing axis)
    val p1Top = point(c1, r1, a1 + alpha1)
    val p1Bot = point(c1, r1, a1 - alpha1)

    // Opposite orientation for the other circle
    val p2Top = point(c2, r2, a2 - alpha2)
    val p2Bot = point(c2, r2, a2 + alpha2)

    // Handle length should be based on span between endpoints to avoid outward bulge
    val spanTop = (p2Top - p1Top).getDistance()
    val spanBot = (p1Bot - p2Bot).getDistance()
    val span = min(spanTop, spanBot)

    // Keep handles modest: too large => outward bow => bulge after union
    val handle = (span * 0.35f).coerceIn(6f, min(r1, r2) * 0.35f)

    val t1Top = tangentUnit(a1 + alpha1)
    val t1Bot = tangentUnit(a1 - alpha1)
    val t2Top = tangentUnit(a2 - alpha2)
    val t2Bot = tangentUnit(a2 + alpha2)

    val c1Top = p1Top + t1Top * handle
    val c2Top = p2Top - t2Top * handle

    val c1Bot = p2Bot + t2Bot * handle
    val c2Bot = p1Bot - t1Bot * handle

    out.moveTo(p1Top.x, p1Top.y)
    out.cubicTo(c1Top.x, c1Top.y, c2Top.x, c2Top.y, p2Top.x, p2Top.y)
    out.lineTo(p2Bot.x, p2Bot.y)
    out.cubicTo(c1Bot.x, c1Bot.y, c2Bot.x, c2Bot.y, p1Bot.x, p1Bot.y)
    out.close()
}


@Preview(showBackground = true, widthDp = 420, heightDp = 720)
@Composable
private fun GooeyStretchAndSnapSample_Preview() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0F14))
    ) {
        GooeyStretchAndSnapSample(modifier = Modifier.fillMaxSize())
    }
}
