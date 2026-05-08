package com.jinscompany.saveurl.ui.save_screen.components

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jinscompany.saveurl.R
import com.jinscompany.saveurl.ui.composable.singleClick
import com.jinscompany.saveurl.ui.save_screen.LinkUrlPreviewUiState

@Composable
fun LazyItemScope.LinkOptionsSection(
    state: LinkUrlPreviewUiState,
    isBookMark: Boolean,
    categoryName: String,
    bookMarkClick: (Boolean) -> Unit,
    categoryClick: (String) -> Unit,
    editorClick: () -> Unit
) {
    val context: Context = LocalContext.current
    Row(
        modifier = Modifier.padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.Absolute.Left
    ) {
        OutlinedButton(
            onClick = singleClick { bookMarkClick.invoke(!isBookMark) },
            modifier = Modifier
                .wrapContentSize()
                .padding(horizontal = 10.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(Color.Transparent),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Icon(
                imageVector = if (isBookMark) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                contentDescription = "bookmark",
                tint = Color.LightGray
            )
        }
        OutlinedButton(
            onClick = singleClick { categoryClick.invoke(categoryName) },
            modifier = Modifier
                .wrapContentSize()
                .weight(1f, fill = false)
                .padding(horizontal = 4.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(Color.Transparent),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    categoryName,
                    color = Color.LightGray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    modifier = Modifier.size(25.dp),
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = "categorySelect",
                    tint = Color.LightGray
                )
            }
        }
        OutlinedButton(
            onClick = singleClick {
                if (state is LinkUrlPreviewUiState.LinkUrlData) {
                    editorClick.invoke()
                } else {
                    Toast.makeText(context, R.string.insert_link_edit_unavailable, Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier
                .wrapContentSize()
                .padding(horizontal = 4.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(Color.Transparent),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text("수정", color = Color.LightGray)
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = "categorySelect",
                tint = Color.LightGray
            )
        }
    }
}
