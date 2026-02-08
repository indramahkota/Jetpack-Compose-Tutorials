@file:OptIn(ExperimentalMaterial3Api::class)

package com.smarttoolfactory.tutorial4_1chatbot.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.tanh

@Composable
fun FoxTopTextFieldBottomScreen() {
    var value by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue("", TextRange(0)))
    }

    val animatedIconState = rememberAnimatedIconState(
        textFieldValue = value,
        maxRotationDeg = 20f,
        distanceNormalizerPx = 450.0
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .onGloballyPositioned(animatedIconState::onIconPositioned)
                    .background(Color(0xFFF2F2F2), RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0x33000000), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                AnimatedIcon(
                    modifier = Modifier.size(48.dp),
                    state = animatedIconState
                )
            }
        }

        BlinkingBasicTextField(
            value = value,
            onValueChange = { nextValue ->
                val safeEnd = nextValue.selection.end.coerceIn(0, nextValue.text.length)
                value = nextValue.copy(selection = TextRange(safeEnd))
            },
            blinkEveryChars = 5,
            onBlinkSignal = animatedIconState::requestBlink,
            textStyle = TextStyle(fontSize = 32.sp, color = Color.Black),
            cursorBrush = SolidColor(Color.Black),
            onTextLayout = animatedIconState::onTextLayout,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0x33000000), RoundedCornerShape(18.dp))
                .background(Color.White, RoundedCornerShape(18.dp))
                .padding(18.dp),
            decorationBox = { inner ->
                Box(Modifier.onGloballyPositioned(animatedIconState::onTextHostPositioned)) {
                    if (value.text.isEmpty()) {
                        BasicText(
                            text = "Type…",
                            style = TextStyle(fontSize = 32.sp, color = Color(0x66000000))
                        )
                    }
                    inner()
                }
            }
        )
    }
}

@Stable
class AnimatedIconState internal constructor(
    private val maxRotationDeg: Float,
    private val distanceNormalizerPx: Double
) {
    // External inputs
    var textFieldValue: TextFieldValue by mutableStateOf(TextFieldValue("", TextRange(0)))

    // Layout inputs
    var textLayout: TextLayoutResult? by mutableStateOf(null)
        private set

    var textHostCoordinates: LayoutCoordinates? by mutableStateOf(null)
        private set

    var iconCoordinates: LayoutCoordinates? by mutableStateOf(null)
        private set

    // Outputs
    var blinkSignal: Int by mutableIntStateOf(0)
        private set

    // Head rotation is animated and exposed as a float (degrees)
    private val headRotationAnim = Animatable(0f)
    val headRotationDeg: Float get() = headRotationAnim.value

    fun requestBlink() {
        blinkSignal += 1
    }

    fun onTextLayout(result: TextLayoutResult) {
        textLayout = result
    }

    fun onTextHostPositioned(coords: LayoutCoordinates) {
        textHostCoordinates = coords
    }

    fun onIconPositioned(coords: LayoutCoordinates) {
        iconCoordinates = coords
    }

    fun updateValue(newValue: TextFieldValue) {
        textFieldValue = newValue
    }

    fun computeTargetHeadRotationDeg(): Float {
        val currentText = textFieldValue.text
        if (currentText.isBlank()) return 0f

        val layout = textLayout ?: return 0f
        val host = textHostCoordinates ?: return 0f
        val icon = iconCoordinates ?: return 0f

        val caretIndex = textFieldValue.selection.end.coerceIn(0, currentText.length)
        if (currentText.isEmpty()) return 0f

        val caretRect: Rect = runCatching { layout.getCursorRect(caretIndex) }.getOrElse { return 0f }
        val caretInRoot: Offset = host.localToRoot(caretRect.center)

        val iconCenterInRoot = icon.localToRoot(
            Offset(icon.size.width / 2f, icon.size.height / 2f)
        )

        val dx = caretInRoot.x - iconCenterInRoot.x
        val dy = caretInRoot.y - iconCenterInRoot.y

        val angleDeg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        val deltaFromDown = angleDeg - 90f

        val dist = hypot(dx.toDouble(), dy.toDouble())
        val strength = tanh(dist / distanceNormalizerPx).toFloat()

        return (deltaFromDown.coerceIn(-maxRotationDeg, maxRotationDeg)) * strength
    }

    suspend fun animateHeadRotationTo(targetDeg: Float) {
        headRotationAnim.animateTo(
            targetValue = targetDeg,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = 0.86f)
        )
    }
}

@Composable
fun rememberAnimatedIconState(
    textFieldValue: TextFieldValue,
    maxRotationDeg: Float = 20f,
    distanceNormalizerPx: Double = 450.0
): AnimatedIconState {
    val state = remember(maxRotationDeg, distanceNormalizerPx) {
        AnimatedIconState(
            maxRotationDeg = maxRotationDeg,
            distanceNormalizerPx = distanceNormalizerPx
        )
    }

    // Keep the latest TextFieldValue inside the state (single source of truth for calculations)
    SideEffect {
        state.updateValue(textFieldValue)
    }

    // Drive head rotation animation from computed target, inside state.
    LaunchedEffect(state) {
        snapshotFlow { state.computeTargetHeadRotationDeg() }
            .distinctUntilChanged()
            .collect { targetDeg ->
                state.animateHeadRotationTo(targetDeg)
            }
    }

    return state
}

@Composable
private fun BlinkingBasicTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    blinkEveryChars: Int,
    onBlinkSignal: () -> Unit,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = TextStyle.Default,
    cursorBrush: SolidColor = SolidColor(Color.Black),
    onTextLayout: (TextLayoutResult) -> Unit = {},
    decorationBox: @Composable (innerTextField: @Composable () -> Unit) -> Unit
) {
    var previousTextLength by remember(value.text) { mutableIntStateOf(value.text.length) }

    BasicTextField(
        value = value,
        onValueChange = { nextValue ->
            val newTextLength = nextValue.text.length
            val addedCharacters = newTextLength > previousTextLength

            onValueChange(nextValue)

            if (
                blinkEveryChars > 0 &&
                addedCharacters &&
                newTextLength > 0 &&
                (newTextLength % blinkEveryChars == 0)
            ) {
                onBlinkSignal()
            }

            previousTextLength = newTextLength
        },
        textStyle = textStyle,
        cursorBrush = cursorBrush,
        onTextLayout = onTextLayout,
        modifier = modifier,
        decorationBox = decorationBox
    )
}

@Composable
fun AnimatedIcon(
    modifier: Modifier = Modifier,
    state: AnimatedIconState,
    minSlitScaleY: Float = 0.12f
) {
    val outerLeftEar = rememberSvgPath(pathData = "M13.431 9.802c.658 2.638-8.673 10.489-11.244 4.098C.696 10.197-.606 2.434.874 2.065c1.48-.368 11.9 5.098 12.557 7.737z")
    val innerLeftEar = rememberSvgPath(pathData = "M11.437 10.355c.96 1.538-1.831 4.561-3.368 5.522c-1.538.961-2.899-.552-4.414-4.414c-.662-1.689-1.666-6.27-1.103-6.622c.562-.351 7.924 3.976 8.885 5.514z")
    val outerRightEar = rememberSvgPath(pathData = "M22.557 9.802C21.9 12.441 31.23 20.291 33.802 13.9c1.49-3.703 2.792-11.466 1.312-11.835c-1.48-.368-11.899 5.098-12.557 7.737z")
    val innerRightEar = rememberSvgPath(pathData = "M24.552 10.355c-.96 1.538 1.831 4.561 3.368 5.522c1.537.961 2.898-.552 4.413-4.414c.662-1.688 1.666-6.269 1.104-6.621c-.563-.352-7.924 3.975-8.885 5.513z")
    val headShape = rememberSvgPath(pathData = "M32.347 26.912c0-.454-.188-1.091-.407-1.687c.585.028 1.519.191 2.77.817a4.003 4.003 0 0 0-.273-1.393c.041.02.075.034.116.055c-1.103-3.31-3.309-5.517-3.309-5.517h2.206c-2.331-4.663-4.965-8.015-8.075-9.559c-1.39-.873-3.688-1.338-7.373-1.339h-.003c-3.695 0-5.996.468-7.385 1.346c-3.104 1.547-5.734 4.896-8.061 9.552H4.76s-2.207 2.206-3.311 5.517c.03-.015.055-.025.084-.04a2.685 2.685 0 0 0-.282 1.377c1.263-.632 2.217-.792 2.813-.818c-.189.513-.343 1.044-.386 1.475a3.146 3.146 0 0 0-.135 1.343C6.75 26.584 8.25 26.792 10 27.667C11.213 31.29 14.206 34 18.001 34c3.793 0 6.746-2.794 7.958-6.416c1.458-1.25 3.708-.875 6.416.416a2.843 2.843 0 0 0-.036-1.093l.008.005z")
    val muzzle = rememberSvgPath(pathData = "M31.243 23.601c.006 0 1.108.003 3.309 1.103c-1.249-2.839-7.525-4.07-9.931-3.291c-1.171 1.954-1.281 5.003-3.383 6.622c-1.741 1.431-4.713 1.458-6.479 0c-2.345-1.924-2.559-5.813-3.382-6.622c-2.407-.781-8.681.454-9.931 3.291c2.201-1.101 3.304-1.103 3.309-1.103c0 .001-1.103 2.208-1.103 3.311l.001-.001v.001c2.398-1.573 5.116-2.271 7.429-.452c1.666 7.921 12.293 7.545 13.833 0c2.314-1.818 5.03-1.122 7.429.452v-.001l.001.001c.002-1.103-1.101-3.311-1.102-3.311z")
    val leftEye = rememberSvgPath(pathData = "M11 17s0-1.5 1.5-1.5S14 17 14 17v1.5s0 1.5-1.5 1.5s-1.5-1.5-1.5-1.5V17z")
    val rightEye = rememberSvgPath(pathData = "M22 17s0-1.5 1.5-1.5S25 17 25 17v1.5s0 1.5-1.5 1.5s-1.5-1.5-1.5-1.5V17z")
    val nose = rememberSvgPath(pathData = "M14.939 27.808c-1.021.208 2.041 3.968 3.062 3.968c1.02 0 4.082-3.76 3.062-3.968c-1.021-.208-5.103-.208-6.124 0z")

    val eyeScaleY = remember { Animatable(1f) }

    LaunchedEffect(state.blinkSignal) {
        if (state.blinkSignal > 0) {
            eyeScaleY.animateTo(
                targetValue = 1f,
                animationSpec = keyframes {
                    durationMillis = 170
                    minSlitScaleY at 55 with LinearEasing
                    minSlitScaleY at 85 with LinearEasing
                    1.03f at 140 with FastOutSlowInEasing
                    1f at 170 with LinearEasing
                }
            )
        }
    }

    Canvas(modifier = modifier) {
        val viewBox = 36f
        val scaleFactor = min(size.width / viewBox, size.height / viewBox)
        val offsetX = (size.width - viewBox * scaleFactor) / 2f
        val offsetY = (size.height - viewBox * scaleFactor) / 2f

        translate(offsetX, offsetY) {
            scale(scaleFactor, scaleFactor, pivot = Offset.Zero) {
                rotate(degrees = state.headRotationDeg, pivot = Offset(18f, 18.2f)) {
                    drawPath(path = outerLeftEar, color = Color(0xFFF4900C), style = Fill)
                    drawPath(path = innerLeftEar, color = Color(0xFFA0041E), style = Fill)
                    drawPath(path = outerRightEar, color = Color(0xFFF4900C), style = Fill)
                    drawPath(path = innerRightEar, color = Color(0xFFA0041E), style = Fill)
                    drawPath(path = headShape, color = Color(0xFFF18F26), style = Fill)

                    drawPath(path = muzzle, color = Color(0xFFFFD983), style = Fill)

                    val black = Color(0xFF272B2B)
                    val leftPivot = Offset(12.5f, 18.5f)
                    val rightPivot = Offset(23.5f, 18.5f)

                    scale(scaleX = 1f, scaleY = eyeScaleY.value, pivot = leftPivot) {
                        drawPath(path = leftEye, color = black, style = Fill)
                    }
                    scale(scaleX = 1f, scaleY = eyeScaleY.value, pivot = rightPivot) {
                        drawPath(path = rightEye, color = black, style = Fill)
                    }

                    drawPath(path = nose, color = black, style = Fill)
                }
            }
        }
    }
}

@Composable
private fun rememberSvgPath(pathData: String): Path = remember(pathData) {
    PathParser().parsePathString(pathData).toPath()
}

@Preview(showBackground = true, widthDp = 900, heightDp = 720)
@Composable
fun FoxTopTextFieldBottomPreview() {
    FoxTopTextFieldBottomScreen()
}
