package com.smarttoolfactory.tutorial4_1chatbot.ui.component.message

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.smarttoolfactory.tutorial4_1chatbot.ui.component.indicator.BouncingDotProgressIndicator

@Composable
fun LoadingRow(
    modifier: Modifier = Modifier
){
    Box(modifier = modifier.fillMaxWidth()){
        BouncingDotProgressIndicator(
            modifier = Modifier.size(48.dp, 24.dp),
            animatedColor = Color.Black,
            initialColor = Color.Black.copy(alpha = .5f)
        )
    }
}
