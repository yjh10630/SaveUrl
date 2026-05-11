package com.jinscompany.saveurl.ui.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jinscompany.saveurl.ui.theme.AppPrimary
import com.jinscompany.saveurl.ui.theme.AppTextPrimary
import com.jinscompany.saveurl.ui.theme.AppTextSecondary

@Composable
fun MainHeaderSection(
    searchIconClick: () -> Unit,
    appSettingClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "SaveURL",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = AppTextPrimary,
            letterSpacing = (-0.5).sp
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = singleClick { searchIconClick.invoke() }) {
                Icon(
                    modifier = Modifier.size(24.dp),
                    imageVector = Icons.Filled.Search,
                    contentDescription = "search",
                    tint = AppTextSecondary
                )
            }
            IconButton(onClick = singleClick { appSettingClick.invoke() }) {
                Icon(
                    modifier = Modifier.size(24.dp),
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "setting",
                    tint = AppTextSecondary
                )
            }
        }
    }
}

@Composable
@Preview(showBackground = true, backgroundColor = 0xFF0F0F0F)
private fun HeaderSectionPreview() {
    MainHeaderSection({}, {})
}