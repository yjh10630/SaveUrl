package com.jinscompany.saveurl.ui.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkItemInfoDialog(
    onClickShare: () -> Unit,
    onClickEdit: () -> Unit,
    onClickDelete: () -> Unit,
    dismiss: () -> Unit
) {
    val modalBottomSheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = { dismiss() },
        sheetState = modalBottomSheetState,
        dragHandle = { BottomSheetDefaults.DragHandle(
            color = Color.LightGray
        ) },
        containerColor = Color.DarkGray
    ) {
        LinkItemInfoDialog(
            onClickShare = {
                scope.launch {
                    modalBottomSheetState.hide()
                }.invokeOnCompletion {
                    onClickShare.invoke()
                }
            },
            onClickDelete = {
                scope.launch {
                    modalBottomSheetState.hide()
                }.invokeOnCompletion {
                    onClickDelete.invoke()
                }
            },
            onClickEdit = {
                scope.launch {
                    modalBottomSheetState.hide()
                }.invokeOnCompletion {
                    onClickEdit.invoke()
                }
            }
        )
    }
}

@Composable
fun LinkItemInfoDialog(
    bottomPadding: Dp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
    onClickShare: () -> Unit = {},
    onClickEdit: () -> Unit = {},
    onClickDelete: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .padding(top = 20.dp, start = 20.dp, end = 20.dp, bottom = bottomPadding)
            .fillMaxWidth()
            .wrapContentHeight(),
        horizontalAlignment = Alignment.Start
    ) {
        TextButton(onClickShare) {
            Icon(
                modifier = Modifier.size(20.dp),
                imageVector = Icons.Default.Share,
                contentDescription = "share",
                tint = Color.LightGray
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = "공유하기", fontWeight = FontWeight.Normal, color = Color.LightGray)
        }
        TextButton(onClickEdit) {
            Icon(
                modifier = Modifier.size(20.dp),
                imageVector = Icons.Default.Edit,
                contentDescription = "edit",
                tint = Color.LightGray
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = "수정하기", fontWeight = FontWeight.Normal, color = Color.LightGray)
        }
        TextButton(
            onClick = onClickDelete,
        ) {
            Icon(
                modifier = Modifier.size(20.dp),
                imageVector = Icons.Default.Delete,
                contentDescription = "delete",
                tint = Color.Red
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = "삭제하기", fontWeight = FontWeight.Bold, color = Color.Red)
        }
    }
}

@Composable
@Preview(
    showBackground = true, backgroundColor = 0xFF444444
)
private fun LinkUrlItemInfoDialogPreview() {
    LinkItemInfoDialog()
}