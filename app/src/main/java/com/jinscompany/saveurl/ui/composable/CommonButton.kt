package com.jinscompany.saveurl.ui.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jinscompany.saveurl.ui.theme.Brown

@Composable
fun CommonPositiveButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    text: String,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .height(50.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Brown)
    ) {
        Text(
            text,
            fontSize = 16.sp,
            color = Color.White,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
@Preview(showBackground = true, backgroundColor = 0xFF444444)
fun PositiveButtonPreview() {
    Column {
        CommonPositiveButton(
            onClick = {},
            text = "저장",
        )
        CommonPositiveButton(
            onClick = {},
            text = "저장",
            enabled = false
        )
    }
}