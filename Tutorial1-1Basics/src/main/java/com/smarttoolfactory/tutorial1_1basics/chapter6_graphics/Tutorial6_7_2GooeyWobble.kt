@file:OptIn(ExperimentalComposeUiApi::class)

package com.smarttoolfactory.tutorial1_1basics.chapter6_graphics

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
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
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
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
    // Dynamic (dragged) blob, static (center) blob, bridge ligament, and union result
    val dynamicBlobPath = remember { Path() }
    val staticBlobPath = remember { Path() }
    val bridgeLigamentPath = remember { Path() }
    val unionPath = remember { Path() }
    val tmpUnionPath = remember { Path() }

    // Pointer position (dynamic center) and canvas size (static center is canvas center)
    var pointerPosition by remember { mutableStateOf(Offset.Unspecified) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    // Stroke paint + gradient shader setup
    val strokePaint = remember {
        Paint().apply {
            style = PaintingStyle.Stroke
            strokeWidth = 4f
        }
    }
    var isShaderInitialized by remember { mutableStateOf(false) }

    /**
     * Stretch amount in [0..1] is driven by "near detach while pulling apart".
     * When detached, it springs back to 0 (no stretch).
     */
    val stretchAmountAnim = remember { Animatable(0f) }

    // --- Attachment hysteresis (prevents flicker at the contact boundary) ---
    var isAttached by remember { mutableStateOf(true) }
    val detachGapThresholdPx = 2f   // gap > this => detached
    val attachGapThresholdPx = -2f  // gap < this => attached again

    // --- Pulling-apart detection (EMA + hysteresis on gap velocity) ---
    var previousGapPx by remember { mutableFloatStateOf(Float.NaN) }
    var gapVelocityEma by remember { mutableFloatStateOf(0f) }
    var isPullingApart by remember { mutableStateOf(false) }

    // --- Stable angles (avoid atan2 noise when centers almost coincide) ---
    var lastDynamicFacingAngleRad by remember { mutableFloatStateOf(0f) }
    var lastStaticFacingAngleRad by remember { mutableFloatStateOf(Math.PI.toFloat()) }

    // --- Geometry ---
    val staticCenter = remember(canvasSize) { Offset(canvasSize.width / 2f, canvasSize.height / 2f) }
    val dynamicCenter = if (pointerPosition == Offset.Unspecified) staticCenter else pointerPosition

    val dynamicRadiusPx = 150f
    val staticRadiusPx = 150f
    val sumRadiiPx = dynamicRadiusPx + staticRadiusPx

    val centerDelta = dynamicCenter - staticCenter
    val centersDistancePx = centerDelta.getDistance()

    /**
     * gapPx:
     *  <= 0  -> circles overlap / attached
     *  >  0  -> separated / detached
     */
    val gapPx = centersDistancePx - sumRadiiPx

    // Update attached state with hysteresis
    LaunchedEffect(gapPx) {
        isAttached = when {
            isAttached && gapPx > detachGapThresholdPx -> false
            !isAttached && gapPx < attachGapThresholdPx -> true
            else -> isAttached
        }
    }

    // Update pulling-apart with EMA velocity + hysteresis
    LaunchedEffect(gapPx) {
        val oldGapPx = previousGapPx
        if (!oldGapPx.isNaN()) {
            // Positive => moving apart (toward detach)
            val gapDeltaPx = gapPx - oldGapPx

            // EMA smoothing to avoid jitter
            val emaAlpha = 0.25f
            gapVelocityEma = gapVelocityEma + (gapDeltaPx - gapVelocityEma) * emaAlpha
        }
        previousGapPx = gapPx

        // Hysteresis thresholds for "pulling apart" state
        val pullingOnThreshold = 0.20f
        val pullingOffThreshold = -0.20f

        isPullingApart = when {
            isPullingApart && gapVelocityEma < pullingOffThreshold -> false
            !isPullingApart && gapVelocityEma > pullingOnThreshold -> true
            else -> isPullingApart
        }
    }

    // Facing angles: only update when distance is meaningful
    val dynamicFacingAngleRad: Float
    val staticFacingAngleRad: Float
    if (centersDistancePx > 6f) {
        // Dynamic blob faces towards static blob
        val dynamicToStatic = staticCenter - dynamicCenter
        // Static blob faces towards dynamic blob
        val staticToDynamic = dynamicCenter - staticCenter

        lastDynamicFacingAngleRad = atan2(dynamicToStatic.y, dynamicToStatic.x)
        lastStaticFacingAngleRad = atan2(staticToDynamic.y, staticToDynamic.x)

        dynamicFacingAngleRad = lastDynamicFacingAngleRad
        staticFacingAngleRad = lastStaticFacingAngleRad
    } else {
        dynamicFacingAngleRad = lastDynamicFacingAngleRad
        staticFacingAngleRad = lastStaticFacingAngleRad
    }

    fun smoothstep(x: Float): Float = x * x * (3f - 2f * x)

    /**
     * Stretch/bridge should only exist while:
     * - attached
     * - pulling apart
     * - near the detach boundary (gap close to 0 but still negative)
     */
    val stretchBandPx = 90f   // how deep inside overlap we start stretching
    val bridgeBandPx = 28f    // how close to 0 gap we allow the ligament

    val targetStretch01 =
        if (isAttached && isPullingApart) {
            // gapPx: [-stretchBandPx..0] -> [0..1]
            val normalized = ((gapPx + stretchBandPx) / stretchBandPx).coerceIn(0f, 1f)
            smoothstep(normalized)
        } else 0f

    val bridgeStrength01 =
        if (isAttached && isPullingApart) {
            // Bridge only when very close to detaching (still overlapping but near 0)
            if (gapPx < 0f && gapPx > -bridgeBandPx) {
                val normalized = ((gapPx + bridgeBandPx) / bridgeBandPx).coerceIn(0f, 1f)
                smoothstep(normalized)
            } else 0f
        } else 0f

    // Drive stretch:
    // - while attached: snap-follow targetStretch01
    // - when detached: spring back to 0
    LaunchedEffect(targetStretch01, isAttached) {
        if (isAttached) {
            stretchAmountAnim.snapTo(targetStretch01)
        } else {
            stretchAmountAnim.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = 0.35f,
                    stiffness = 320f
                )
            )
        }
    }

    /**
     * Actual stretch amount (px) and "neck" focus:
     * - stretchPx controls how much the facing side inflates and the back side compresses.
     * - neckFocusPower controls how tightly the deformation concentrates towards the facing direction.
     */
    val stretchPx = 60f * stretchAmountAnim.value
    val neckFocusPower = 6.5f

    Canvas(
        modifier = modifier
            .onSizeChanged { canvasSize = it }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { startOffset -> pointerPosition = startOffset }
                ) { change, _ ->
                    pointerPosition = change.position
                    change.consume()
                }
            }
    ) {
        // --- Build stretched blobs ---
        dynamicBlobPath.reset()
        buildStretchedBlobPath(
            outPath = dynamicBlobPath,
            center = dynamicCenter,
            baseRadiusPx = dynamicRadiusPx,
            samplePointCount = 140,
            stretchPx = stretchPx,
            facingAngleRad = dynamicFacingAngleRad,
            neckFocusPower = neckFocusPower,
            stretchBias = 1.0f
        )

        staticBlobPath.reset()
        buildStretchedBlobPath(
            outPath = staticBlobPath,
            center = staticCenter,
            baseRadiusPx = staticRadiusPx,
            samplePointCount = 160,
            stretchPx = stretchPx * 0.8f,
            facingAngleRad = staticFacingAngleRad,
            neckFocusPower = neckFocusPower,
            stretchBias = 0.9f
        )

        // --- Build bridge ligament (THINNER than circles) ---
        bridgeLigamentPath.reset()
        if (bridgeStrength01 > 0f) {
            buildBridgePathThin(
                outPath = bridgeLigamentPath,
                firstCenter = dynamicCenter,
                firstRadiusPx = dynamicRadiusPx,
                firstFacingAngleRad = dynamicFacingAngleRad,
                secondCenter = staticCenter,
                secondRadiusPx = staticRadiusPx,
                secondFacingAngleRad = staticFacingAngleRad,
                strength01 = bridgeStrength01,

                // Smaller ligament radius than circles (tune these)
                bridgeThicknessMaxPx = 14f,
                bridgeThicknessMinPx = 2.5f
            )
        }

        // Shader once
        if (!isShaderInitialized) {
            strokePaint.shader = LinearGradientShader(
                from = Offset.Zero,
                to = Offset(size.width, size.height),
                colors = listOf(Color(0xffFFEB3B), Color(0xffE91E63)),
                tileMode = TileMode.Clamp
            )
            isShaderInitialized = true
        }

        // --- Union: (dynamic ∪ static) ∪ bridge ---
        unionPath.reset()
        unionPath.op(dynamicBlobPath, staticBlobPath, PathOperation.Union)

        if (!bridgeLigamentPath.isEmpty) {
            tmpUnionPath.reset()
            tmpUnionPath.op(unionPath, bridgeLigamentPath, PathOperation.Union)
            unionPath.reset()
            unionPath.addPath(tmpUnionPath)
        }

        drawIntoCanvas { canvas ->
            canvas.drawPath(unionPath, strokePaint)
        }
    }
}

/**
 * Builds a "stretched blob":
 * - Starts as a circle
 * - Adds an angle-dependent radius term concentrated around [facingAngleRad]
 * - Slightly compresses the opposite side to preserve volume-ish feel
 */
private fun buildStretchedBlobPath(
    outPath: Path,
    center: Offset,
    baseRadiusPx: Float,
    samplePointCount: Int,
    stretchPx: Float,
    facingAngleRad: Float,
    neckFocusPower: Float,
    stretchBias: Float
) {
    if (samplePointCount < 16) return

    val angleStepRad = (2.0 * Math.PI / samplePointCount).toFloat()

    fun smoothstep01(x: Float): Float = x * x * (3f - 2f * x)

    /**
     * Returns [0..1] mask that is highest when theta faces [facingAngleRad].
     * - cos(theta - facingAngle) gives [-1..1]
     * - remap to [0..1] then apply smoothstep + power for tight neck
     */
    fun neckMask(thetaRad: Float): Float {
        val facingCos = cos(thetaRad - facingAngleRad).coerceIn(-1f, 1f)
        val facing = (facingCos + 1f) * 0.5f
        val smoothed = smoothstep01(facing)
        return smoothed.pow(neckFocusPower)
    }

    fun radiusAtAnglePx(thetaRad: Float): Float {
        val neckMask = neckMask(thetaRad)

        // Inflate the facing side
        val neckInflationPx = (stretchPx * stretchBias) * neckMask

        // Compress the far side (where neckMask ~ 0)
        val farMask = 1f - neckMask
        val farCompressionPx = -0.22f * stretchPx * farMask

        return baseRadiusPx + neckInflationPx + farCompressionPx
    }

    var angleRad = 0f
    var radiusPx = radiusAtAnglePx(angleRad)

    outPath.moveTo(
        center.x + radiusPx * cos(angleRad),
        center.y + radiusPx * sin(angleRad)
    )

    for (pointIndex in 1..samplePointCount) {
        angleRad = pointIndex * angleStepRad
        radiusPx = radiusAtAnglePx(angleRad)
        outPath.lineTo(
            center.x + radiusPx * cos(angleRad),
            center.y + radiusPx * sin(angleRad)
        )
    }

    outPath.close()
}

/**
 * Thin bridge ("ligament") connecting the two stretched blobs.
 *
 * The key change vs your original bridge:
 * - The ligament thickness is intentionally SMALL relative to circle radius
 *   (e.g., 2.5..14 px vs 150 px circle radius).
 */
private fun buildBridgePathThin(
    outPath: Path,
    firstCenter: Offset,
    firstRadiusPx: Float,
    firstFacingAngleRad: Float,
    secondCenter: Offset,
    secondRadiusPx: Float,
    secondFacingAngleRad: Float,
    strength01: Float,
    bridgeThicknessMaxPx: Float,
    bridgeThicknessMinPx: Float
) {
    fun smoothstep(x: Float): Float = x * x * (3f - 2f * x)
    fun clamp(x: Float): Float = x.coerceIn(0f, 1f)

    // strength01: 0 -> thicker ligament, 1 -> thinnest near detach
    val strengthT = smoothstep(clamp(strength01))

    val ligamentThicknessPx =
        bridgeThicknessMaxPx + (bridgeThicknessMinPx - bridgeThicknessMaxPx) * strengthT

    /**
     * Convert thickness (px) to half-angle span on each circle:
     * alpha = asin(thickness / radius)
     *
     * Clamp ratio to avoid NaN and to avoid too-wide spans.
     */
    fun angleSpanFor(radiusPx: Float): Float {
        val ratio = (ligamentThicknessPx / max(1f, radiusPx)).coerceIn(0f, 0.35f)
        return asin(ratio)
    }

    val firstAngleSpanRad = angleSpanFor(firstRadiusPx)
    val secondAngleSpanRad = angleSpanFor(secondRadiusPx)

    fun pointOnCircle(center: Offset, radiusPx: Float, angleRad: Float): Offset =
        Offset(center.x + radiusPx * cos(angleRad), center.y + radiusPx * sin(angleRad))

    fun tangentUnit(angleRad: Float): Offset =
        Offset(-sin(angleRad), cos(angleRad))

    // Facing arc endpoints around facing direction
    val firstTop = pointOnCircle(firstCenter, firstRadiusPx, firstFacingAngleRad + firstAngleSpanRad)
    val firstBottom = pointOnCircle(firstCenter, firstRadiusPx, firstFacingAngleRad - firstAngleSpanRad)

    // Opposite orientation for the other circle's facing direction
    val secondTop = pointOnCircle(secondCenter, secondRadiusPx, secondFacingAngleRad - secondAngleSpanRad)
    val secondBottom = pointOnCircle(secondCenter, secondRadiusPx, secondFacingAngleRad + secondAngleSpanRad)

    // Handle length based on endpoint span (keep it modest to avoid outward bulge)
    val topSpanPx = (secondTop - firstTop).getDistance()
    val bottomSpanPx = (firstBottom - secondBottom).getDistance()
    val minSpanPx = min(topSpanPx, bottomSpanPx)

    val maxHandlePx = min(firstRadiusPx, secondRadiusPx) * 0.25f
    val handleLengthPx = (minSpanPx * 0.30f).coerceIn(4f, maxHandlePx)

    val firstTopTangent = tangentUnit(firstFacingAngleRad + firstAngleSpanRad)
    val firstBottomTangent = tangentUnit(firstFacingAngleRad - firstAngleSpanRad)

    val secondTopTangent = tangentUnit(secondFacingAngleRad - secondAngleSpanRad)
    val secondBottomTangent = tangentUnit(secondFacingAngleRad + secondAngleSpanRad)

    val firstTopCtrl = firstTop + firstTopTangent * handleLengthPx
    val secondTopCtrl = secondTop - secondTopTangent * handleLengthPx

    val secondBottomCtrl = secondBottom + secondBottomTangent * handleLengthPx
    val firstBottomCtrl = firstBottom - firstBottomTangent * handleLengthPx

    outPath.moveTo(firstTop.x, firstTop.y)
    outPath.cubicTo(
        firstTopCtrl.x, firstTopCtrl.y,
        secondTopCtrl.x, secondTopCtrl.y,
        secondTop.x, secondTop.y
    )
    outPath.lineTo(secondBottom.x, secondBottom.y)
    outPath.cubicTo(
        secondBottomCtrl.x, secondBottomCtrl.y,
        firstBottomCtrl.x, firstBottomCtrl.y,
        firstBottom.x, firstBottom.y
    )
    outPath.close()
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
