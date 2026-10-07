package com.jinscompany.saveurl.ui.composable

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.jinscompany.saveurl.ui.theme.AppDimens
import com.jinscompany.saveurl.ui.theme.AppShapes
import com.jinscompany.saveurl.ui.theme.AppTheme
import com.jinscompany.saveurl.ui.theme.SaveUrlTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommonSimpleMenuBottomSheet(
    model: SimpleMenuModel,
    dismiss: () -> Unit,
) {
    val modalBottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    AppBottomSheet(onDismissRequest = { dismiss.invoke() }, sheetState = modalBottomSheetState) {
        CommonSimpleMenuView(
            header = model.header,
            menuList = model.menuList,
            titleTxt = model.titleTxtRes?.let { stringResource(it) } ?: model.titleTxt,
            descriptionTxt = model.descriptionTxtRes?.let { stringResource(it) } ?: model.descriptionTxt,
            event = { index ->
                scope.launch {
                    modalBottomSheetState.hide()
                }.invokeOnCompletion {
                    dismiss.invoke()
                    model.menuList[index].event.invoke()
                }
            }
        )
    }
}

@Composable
fun CommonSimpleMenuView(
    bottomPadding: Dp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
    header: SimpleMenuModel.Header? = null,
    titleTxt: String? = null,
    descriptionTxt: String? = null,
    menuList: List<SimpleMenuModel.MenuModel>,
    event: (Int) -> Unit = {},
) {
    val colors = AppTheme.colors
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = bottomPadding + 8.dp)
    ) {
        if (header != null) {
            item {
                MenuHeader(header)
                HorizontalDivider(color = colors.outline, thickness = 1.dp)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
        if (!titleTxt.isNullOrEmpty()) {
            item {
                Text(
                    titleTxt,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize * 0.9f),
                    color = colors.textPrimary,
                    modifier = Modifier.padding(horizontal = AppDimens.Gutter)
                )
            }
        }
        if (!descriptionTxt.isNullOrEmpty()) {
            item {
                Text(
                    descriptionTxt,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(horizontal = AppDimens.Gutter)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
        itemsIndexed(items = menuList) { index, item ->
            val tint = when {
                item.isDanger -> colors.danger
                item.txtColor != Color.Unspecified -> item.txtColor
                else -> colors.textPrimary
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clickable { event.invoke(index) }
                    .padding(horizontal = AppDimens.Gutter + 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (item.icon != null) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        tint = if (item.isDanger) colors.danger else colors.textSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(20.dp))
                }
                val label = item.txtRes?.let { stringResource(it) } ?: item.txt
                Text(
                    label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (item.isBold) FontWeight.SemiBold else FontWeight.Normal,
                    color = tint,
                )
            }
        }
    }
}

@Composable
private fun MenuHeader(header: SimpleMenuModel.Header) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.Gutter, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (!header.imgUrl.isNullOrEmpty()) {
            AsyncImage(
                modifier = Modifier
                    .size(48.dp)
                    .clip(AppShapes.Thumb)
                    .background(colors.surface),
                model = header.imgUrl,
                placeholder = ColorPainter(colors.surface),
                error = ColorPainter(colors.surface),
                contentDescription = null,
                contentScale = ContentScale.Crop,
            )
        } else {
            Row(
                modifier = Modifier
                    .size(48.dp)
                    .clip(AppShapes.Thumb)
                    .background(colors.surface),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Link, contentDescription = null, tint = colors.textSecondary)
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = header.title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (header.subtitle.isNotEmpty()) {
                Text(
                    text = header.subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

data class SimpleMenuModel(
    val menuList: List<MenuModel>,
    val titleTxt: String? = null,
    val descriptionTxt: String? = null,
    @StringRes val titleTxtRes: Int? = null,
    @StringRes val descriptionTxtRes: Int? = null,
    /** 링크 메뉴처럼 대상 링크를 미니 미리보기로 보여줄 때 사용 */
    val header: Header? = null,
) {
    data class MenuModel(
        val txt: String = "",
        @StringRes val txtRes: Int? = null,
        val event: () -> Unit,
        /** 지정하지 않으면 테마의 기본 텍스트색을 사용 */
        val txtColor: Color = Color.Unspecified,
        val isBold: Boolean = false,
        /** 휴지통 이동/삭제처럼 위험한 동작 (빨간색) */
        val isDanger: Boolean = false,
        val icon: ImageVector? = null,
    )

    data class Header(
        val imgUrl: String?,
        val title: String,
        val subtitle: String,
    )
}

@Composable
@Preview(showBackground = true)
fun CommonSimpleMenuBottomSheetPreview() {
    SaveUrlTheme(darkTheme = false) {
        CommonSimpleMenuView(
            header = SimpleMenuModel.Header(null, "초보도 쉽게 따라하는 김치찌개", "naver.com · 레시피"),
            menuList = listOf(
                SimpleMenuModel.MenuModel(txt = "전체 복원", event = {}),
                SimpleMenuModel.MenuModel(txt = "전체 삭제", event = {}, isDanger = true),
            )
        )
    }
}
