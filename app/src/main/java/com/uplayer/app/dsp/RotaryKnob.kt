package com.uplayer.app.dsp

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val KnobBlue = Color(0xFF315CFF)
private val KnobTrack = Color(0xFF202633)
private val KnobLabel = Color(0xFF8A91A3)

@Composable
fun RotaryKnob(
 label: String,
 valueText: String,
 value: Float,
 valueRange: ClosedFloatingPointRange<Float>,
 onValueChange: (Float) -> Unit,
 modifier: Modifier = Modifier,
 size: Dp = 72.dp
) {
 val currentValue = rememberUpdatedState(value)
 val currentValueChange = rememberUpdatedState(onValueChange)
 val fraction = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
 Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
  Box(Modifier.size(size), contentAlignment = Alignment.Center) {
   Canvas(
    Modifier
     .matchParentSize()
     .pointerInput(valueRange) {
      detectDragGestures { change, dragAmount ->
       change.consume()
       val span = valueRange.endInclusive - valueRange.start
       val delta = (dragAmount.x - dragAmount.y) / 220f * span
       currentValueChange.value((currentValue.value + delta).coerceIn(valueRange))
      }
     }
   ) {
    val stroke = 1.35.dp.toPx()
    val inset = 4.dp.toPx()
    val arcSize = Size(this.size.width - inset * 2f, this.size.height - inset * 2f)
    drawArc(
     color = KnobTrack,
     startAngle = 135f,
     sweepAngle = 270f,
     useCenter = false,
     topLeft = Offset(inset, inset),
     size = arcSize,
     style = Stroke(stroke, cap = StrokeCap.Round)
    )
    val sweep = 270f * fraction
    if (sweep > 0.5f) {
     drawArc(
      brush = Brush.sweepGradient(
       listOf(KnobBlue.copy(alpha = 0.08f), KnobBlue.copy(alpha = 0.45f), KnobBlue)
      ),
      startAngle = 135f,
      sweepAngle = sweep,
      useCenter = false,
      topLeft = Offset(inset, inset),
      size = arcSize,
      style = Stroke(stroke, cap = StrokeCap.Round)
     )
    }
    val angle = (135f + sweep) * PI.toFloat() / 180f
    val radius = arcSize.width / 2f
    val center = Offset(this.size.width / 2f, this.size.height / 2f)
    val handle = Offset(center.x + cos(angle) * radius, center.y + sin(angle) * radius)
    drawCircle(Color(0xFF69A1FF), radius = 2.2.dp.toPx(), center = handle)
   }
   Text(valueText, color = Color.White, fontSize = 10.sp, textAlign = TextAlign.Center)
  }
  Text(label, color = KnobLabel, fontSize = 8.sp, modifier = Modifier, textAlign = TextAlign.Center)
 }
}
