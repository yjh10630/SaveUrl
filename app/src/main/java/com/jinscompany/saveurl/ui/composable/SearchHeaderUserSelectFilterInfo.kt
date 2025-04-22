package com.jinscompany.saveurl.ui.composable

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@SuppressLint("MutableCollectionMutableState")
@Composable
fun SearchHeaderUserSelectFilterInfo(
    searchResultItemCnt: Int = 0,
    onFilterClick: () -> Unit,
    selectedFilterTxt: String = "전체",
    siteTypeList: List<String> = listOf()
) {
    val userSelects by remember { mutableStateOf<MutableList<String>>(mutableListOf()) }
    val userSiteTypeSelectTxt = { text: String ->
        val index = userSelects.indexOfFirst { it == text }
        if (index == -1) {
            userSelects.add(text)
        } else {
            userSelects.removeAt(index)
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "${searchResultItemCnt}",
            modifier = Modifier.padding(start = 24.dp, end = 6.dp),
            fontSize = 16.sp,
            color = Color.LightGray,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "개",
            fontSize = 12.sp,
            color = Color.LightGray,
            fontWeight = FontWeight.Medium,
        )
        VerticalDivider(
            modifier = Modifier
                .height(12.dp)
                .padding(start = 12.dp, end = 12.dp), color = Color.Gray,
            thickness = 1.dp
        )
        LazyRow {
            item {
                OutlinedButton(
                    onFilterClick,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(start = 10.dp, end = 5.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = Color.LightGray
                    )
                ) {
                    Text(selectedFilterTxt)
                    Icon(
                        modifier = Modifier.padding(top = 3.dp, bottom = 0.dp, start = 5.dp),
                        imageVector = Icons.Default.ArrowDropUp,
                        contentDescription = "categorySelect",
                        tint = Color.LightGray
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
            }
            if (siteTypeList.isNotEmpty()) {
                itemsIndexed(siteTypeList) { index, item ->
                    var isSelect by remember { mutableStateOf(false) }
                    OutlinedButton(
                        onClick = {
                            isSelect = !isSelect
                            userSiteTypeSelectTxt.invoke(item)
                        },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(start = 10.dp, end = 10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelect) Color.LightGray else Color.Transparent,
                            contentColor = if (isSelect) Color.DarkGray else Color.LightGray
                        )
                    ) {
                        Text(item)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                }
            }
        }
    }
}

@Composable
@Preview(
    showBackground = true, backgroundColor = 0xFF444444,
)
private fun SearchHeaderUserSelectFilterInfoPreview() {
    SearchHeaderUserSelectFilterInfo(
        0,
        onFilterClick = {},
        siteTypeList = listOf("Youtube" , "Instargram" , "Facebook", "Naver")
    )
}

@Composable
@Preview(
    showBackground = true, backgroundColor = 0xFF444444,
)
private fun SearchHeaderUserSelectFilterInfoPreview2() {
    SearchHeaderUserSelectFilterInfo(
        0,
        onFilterClick = {},
        siteTypeList = listOf()
    )
}