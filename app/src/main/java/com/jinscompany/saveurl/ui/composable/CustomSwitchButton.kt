package com.jinscompany.saveurl.ui.composable

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jinscompany.saveurl.ui.theme.Brown

@Composable
fun CustomSwitchButton(
    switchPadding: Dp = 3.dp,
    buttonWidth: Dp = 50.dp,
    buttonHeight: Dp = 30.dp,
    value: Boolean,
    onClick: (Boolean) -> Unit
) {
    val switchSize = buttonHeight - switchPadding * 2

    val interactionSource = remember { MutableInteractionSource() }

    val padding = if (value) buttonWidth - switchSize - switchPadding * 2 else 0.dp

    val animateSize by animateDpAsState(
        targetValue = padding,
        tween(
            durationMillis = 700,
            easing = LinearOutSlowInEasing
        )
    )

    Box(
        modifier = Modifier
            .width(buttonWidth)
            .height(buttonHeight)
            .clip(CircleShape)
            .background(if (value) Brown else Color.LightGray)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                onClick(!value)
            }
    ) {
        Row(modifier = Modifier.fillMaxSize().padding(switchPadding)) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(animateSize)
                    .background(Color.Transparent)
            )
            Box(
                modifier = Modifier
                    .size(switchSize)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
    }
}