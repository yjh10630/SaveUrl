package com.jinscompany.saveurl.ui.main.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.outlined.ViewHeadline
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jinscompany.saveurl.R
import com.jinscompany.saveurl.domain.model.ListViewMode
import com.jinscompany.saveurl.ui.FilterDefaults
import com.jinscompany.saveurl.ui.composable.singleClick
import com.jinscompany.saveurl.ui.main.MainTab
import com.jinscompany.saveurl.ui.theme.AppDimens
import com.jinscompany.saveurl.ui.theme.AppTheme
import com.jinscompany.saveurl.ui.theme.SaveUrlTheme

val ListViewMode.icon: ImageVector
    get() = when (this) {
        ListViewMode.DEFAULT -> Icons.AutoMirrored.Outlined.ViewList
        ListViewMode.LARGE_CARD -> Icons.Outlined.ViewAgenda
        ListViewMode.COMPACT -> Icons.Outlined.ViewHeadline
    }

val ListViewMode.labelRes: Int
    get() = when (this) {
        ListViewMode.DEFAULT -> R.string.view_mode_default
        ListViewMode.LARGE_CARD -> R.string.view_mode_large_card
        ListViewMode.COMPACT -> R.string.view_mode_compact
    }

/** 앱바: SaveLink / 보기 방식 / 검색 / 설정 */
@Composable
fun MainTopBar(
    viewMode: ListViewMode,
    onViewModeChange: (ListViewMode) -> Unit,
    onSearchClick: () -> Unit,
    onSettingClick: () -> Unit,
) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(start = AppDimens.Gutter, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f)
        )
        ViewModeMenuButton(viewMode = viewMode, onViewModeChange = onViewModeChange)
        IconButton(onClick = singleClick { onSearchClick() }) {
            Icon(Icons.Outlined.Search, contentDescription = stringResource(R.string.main_search), tint = colors.textPrimary)
        }
        IconButton(onClick = singleClick { onSettingClick() }) {
            Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.main_settings), tint = colors.textPrimary)
        }
    }
}

@Composable
private fun ViewModeMenuButton(viewMode: ListViewMode, onViewModeChange: (ListViewMode) -> Unit) {
    val colors = AppTheme.colors
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(viewMode.icon, contentDescription = stringResource(R.string.main_view_mode), tint = colors.textPrimary)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            Text(
                text = stringResource(R.string.main_view_mode),
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            ListViewMode.entries.forEach { mode ->
                val selected = mode == viewMode
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(mode.labelRes),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) colors.accent else colors.textPrimary,
                        )
                    },
                    leadingIcon = {
                        Icon(mode.icon, contentDescription = null, tint = if (selected) colors.accent else colors.textSecondary)
                    },
                    trailingIcon = {
                        if (selected) Icon(Icons.Outlined.Check, contentDescription = null, tint = colors.accent)
                    },
                    onClick = {
                        expanded = false
                        onViewModeChange(mode)
                    }
                )
            }
        }
    }
}

/** "최근 저장 | 즐겨찾기" 탭 (리스트 전체를 전환) */
@Composable
fun MainTabRow(selectedTab: MainTab, onTabSelect: (MainTab) -> Unit) {
    val colors = AppTheme.colors
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = AppDimens.Gutter),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            MainTab.entries.forEach { tab ->
                val selected = tab == selectedTab
                Column(
                    modifier = Modifier
                        .width(IntrinsicSize.Max)
                        .clickable { onTabSelect(tab) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(if (tab == MainTab.RECENT) R.string.main_tab_recent else R.string.main_tab_favorites),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (selected) colors.accent else colors.textSecondary,
                        modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.5.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (selected) colors.accent else colors.background)
                    )
                }
            }
        }
        HorizontalDivider(color = colors.outline, thickness = 1.dp)
    }
}

/** 카테고리 칩 한 줄 + 끝의 필터(tune) 버튼 */
@Composable
fun CategoryChipRow(
    chips: List<String>,
    selected: List<String>,
    hasDetailFilter: Boolean,
    onCategoryClick: (String) -> Unit,
    onFilterClick: () -> Unit,
) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 칩 행은 필터 버튼 앞에서 끝나야 한다: 영역 밖으로 그려지지 않게 clipToBounds 하고,
        // 오른쪽 끝을 배경색으로 페이드해 칩이 버튼 밑으로 들어가 보이지 않게 한다 (1단계 겹침 버그 수정).
        Box(modifier = Modifier.weight(1f).clipToBounds()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(start = AppDimens.Gutter, end = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(chips, key = { it }) { name ->
                    CategoryChip(
                        name = name,
                        isSelected = name in selected,
                        onClick = { onCategoryClick(name) }
                    )
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(32.dp)
                    .height(36.dp)
                    .background(Brush.horizontalGradient(listOf(colors.background.copy(alpha = 0f), colors.background)))
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .padding(end = AppDimens.Gutter)
                .size(36.dp)
                .clip(CircleShape)
                // 칩(surface 채움)과 구분되도록 테두리형 버튼으로 둔다
                .background(if (hasDetailFilter) colors.accentTint else colors.background)
                .border(1.dp, if (hasDetailFilter) colors.accent.copy(alpha = 0.4f) else colors.outline, CircleShape)
                .clickable(onClick = singleClick { onFilterClick() }),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Tune,
                contentDescription = stringResource(R.string.main_filter_open),
                tint = if (hasDetailFilter) colors.accent else colors.textSecondary,
                modifier = Modifier.size(19.dp)
            )
        }
    }
}

@Composable
private fun CategoryChip(name: String, isSelected: Boolean, onClick: () -> Unit) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .height(34.dp)
            .clip(CircleShape)
            .background(if (isSelected) colors.accent else colors.surface)
            .clickable(onClick = singleClick { onClick() })
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (name == FilterDefaults.CATEGORY_BOOKMARK) {
            Icon(
                imageVector = Icons.Filled.Bookmark,
                contentDescription = name,
                tint = if (isSelected) colors.onAccent else colors.textSecondary,
                modifier = Modifier.size(14.dp)
            )
        } else {
            Text(
                text = name,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (isSelected) colors.onAccent else colors.textSecondary,
                maxLines = 1,
            )
        }
    }
}

/** 필터 시트에서 고른 사이트/태그를 작은 칩으로 표시 (표시 전용, 기존과 동일) */
@Composable
fun ActiveFilterRow(filters: List<String>) {
    if (filters.isEmpty()) return
    val colors = AppTheme.colors
    LazyRow(
        modifier = Modifier.padding(top = 6.dp),
        contentPadding = PaddingValues(horizontal = AppDimens.Gutter),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(filters) { txt ->
            Text(
                text = txt,
                style = MaterialTheme.typography.labelMedium,
                color = colors.accent,
                maxLines = 1,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(colors.accentTint)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

/** 왼쪽: 개수 라벨(있을 때), 오른쪽: "최신순 ▾" 정렬 메뉴 */
@Composable
fun SortRow(
    countLabel: String?,
    sort: String,
    onSortChange: (String) -> Unit,
) {
    val colors = AppTheme.colors
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = AppDimens.Gutter, end = AppDimens.Gutter - 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = countLabel.orEmpty(),
            style = MaterialTheme.typography.labelMedium,
            color = colors.textSecondary,
            modifier = Modifier.weight(1f)
        )
        Box {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { expanded = true }
                    .padding(start = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(sort, style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
                Icon(Icons.Outlined.ArrowDropDown, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(18.dp))
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                listOf(FilterDefaults.SORT_LATEST, FilterDefaults.SORT_OLDEST).forEach { option ->
                    val selected = option == sort
                    DropdownMenuItem(
                        text = {
                            Text(
                                option,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selected) colors.accent else colors.textPrimary,
                            )
                        },
                        trailingIcon = {
                            if (selected) Icon(Icons.Outlined.Check, contentDescription = null, tint = colors.accent)
                        },
                        onClick = {
                            expanded = false
                            if (!selected) onSortChange(option)
                        }
                    )
                }
            }
        }
    }
}

/** 날짜 그룹 헤더 (오늘 / 어제 / 10월 3일) */
@Composable
fun DateHeader(label: String, isFirst: Boolean) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
        color = AppTheme.colors.textSecondary,
        modifier = Modifier.padding(
            start = AppDimens.Gutter,
            end = AppDimens.Gutter,
            top = if (isFirst) 8.dp else AppDimens.SectionSpacing - 8.dp,
            bottom = 4.dp
        )
    )
}

@Preview(showBackground = true)
@Composable
private fun MainHeaderPreview() {
    SaveUrlTheme(darkTheme = false) {
        Column {
            MainTopBar(ListViewMode.DEFAULT, {}, {}, {})
            MainTabRow(MainTab.RECENT) {}
            CategoryChipRow(listOf("전체", "개발", "뉴스"), listOf("전체"), false, {}, {})
            SortRow("즐겨찾기 4개", "최신순") {}
            Spacer(Modifier.height(8.dp))
        }
    }
}
