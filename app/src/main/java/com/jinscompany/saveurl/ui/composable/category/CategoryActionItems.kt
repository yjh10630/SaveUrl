package com.jinscompany.saveurl.ui.composable.category

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun LazyItemScope.ConfirmCancelItem(
    checkOnClick: () -> Unit,
    cancelOnClick: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        VerticalDivider(
            modifier = Modifier
                .height(30.dp)
                .padding(start = 12.dp, end = 12.dp),
            color = Color.LightGray,
            thickness = 2.dp
        )
        IconButton(onClick = checkOnClick) {
            Icon(
                modifier = Modifier.size(24.dp),
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "check",
                tint = Color.White
            )
        }
        IconButton(onClick = cancelOnClick) {
            Icon(
                modifier = Modifier.size(24.dp),
                imageVector = Icons.Default.Cancel,
                contentDescription = "cancel",
                tint = Color.White
            )
        }
    }
}

@Composable
@Preview(showBackground = true, backgroundColor = 0xFF000000)
private fun ConfirmCancelItemPreview() {
    LazyRow {
        item { CategoryItem(text = "키워드", onClick = {}, isEditMode = true) }
        item { ConfirmCancelItem(checkOnClick = {}, cancelOnClick = {}) }
    }
}
