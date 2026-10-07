package com.jinscompany.saveurl.ui.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jinscompany.saveurl.ui.theme.AppDimens
import com.jinscompany.saveurl.ui.theme.AppShapes
import com.jinscompany.saveurl.ui.theme.AppTheme

// ---------------------------------------------------------------------------------------------
// 리디자인 공통 컴포넌트 (Stitch "SaveURL Simple" 디자인 시스템)
// ---------------------------------------------------------------------------------------------

/** 입력칸·버튼 공통 모서리 */
val FieldShape = RoundedCornerShape(14.dp)
val ButtonShape = RoundedCornerShape(16.dp)

/**
 * 화면 하단 광고 슬롯. surface 배경 + 상단 구분선 + 기존 [AdMobBannerAd] (navigationBarsPadding/생명주기 처리 포함).
 * 모든 전체 화면의 Scaffold bottomBar 에서 사용한다.
 */
@Composable
fun AdBannerBar(modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    Column(modifier = modifier.fillMaxWidth().background(colors.surface)) {
        HorizontalDivider(color = colors.outline, thickness = 1.dp)
        AdMobBannerAd()
    }
}

/** ← 제목 [actions] 형태의 단순 앱바 (56dp) */
@Composable
fun AppTopBar(
    title: String,
    onNavigationClick: () -> Unit,
    navigationIcon: ImageVector = Icons.AutoMirrored.Filled.ArrowBack,
    navigationContentDescription: String = "뒤로",
    showDivider: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = AppTheme.colors
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(start = 4.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = singleClick { onNavigationClick() }) {
                Icon(navigationIcon, contentDescription = navigationContentDescription, tint = colors.textPrimary)
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize * 0.9f),
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            actions()
        }
        if (showDivider) HorizontalDivider(color = colors.outline, thickness = 1.dp)
    }
}

/** 입력칸 위의 작은 라벨 / 섹션 라벨 */
@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
        color = AppTheme.colors.textSecondary,
        modifier = modifier
    )
}

/**
 * surface 로 채운 입력칸 (테두리 없음, 포커스 시 강조색 테두리).
 * 지우기(×) 버튼은 [onClear] 를 넘기고 값이 있을 때만 보인다.
 */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    leadingIcon: ImageVector? = null,
    onClear: (() -> Unit)? = null,
    imeAction: ImeAction = ImeAction.Done,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val colors = AppTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.textPrimary),
        cursorBrush = SolidColor(colors.accent),
        keyboardOptions = KeyboardOptions.Default.copy(imeAction = imeAction),
        keyboardActions = keyboardActions,
        interactionSource = interaction,
        decorationBox = { inner ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .clip(FieldShape)
                    .background(colors.surface)
                    .border(
                        width = if (focused) 1.5.dp else 0.dp,
                        color = if (focused) colors.accent else Color.Transparent,
                        shape = FieldShape
                    )
                    .padding(start = 16.dp, end = if (onClear != null || trailing != null) 6.dp else 16.dp),
                verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
            ) {
                if (leadingIcon != null) {
                    Icon(
                        leadingIcon, contentDescription = null,
                        tint = if (focused) colors.accent else colors.textSecondary,
                        modifier = Modifier
                            .padding(top = if (singleLine) 0.dp else 15.dp)
                            .size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 14.dp)
                ) {
                    if (value.isEmpty()) {
                        Text(
                            placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.textSecondary,
                            maxLines = if (singleLine) 1 else Int.MAX_VALUE,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    inner()
                }
                if (trailing != null && value.isNotEmpty()) trailing()
                if (onClear != null && value.isNotEmpty()) {
                    IconButton(onClick = onClear, modifier = Modifier.size(40.dp)) {
                        Icon(
                            Icons.Filled.Cancel, contentDescription = "지우기",
                            tint = colors.textSecondary, modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    )
}

/** 강조색으로 채운 기본 버튼 (52dp) */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isDanger: Boolean = false,
) {
    val colors = AppTheme.colors
    Button(
        onClick = singleClick { onClick() },
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = ButtonShape,
        contentPadding = PaddingValues(horizontal = 16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isDanger) colors.danger else colors.accent,
            contentColor = colors.onAccent,
            disabledContainerColor = colors.outline,
            disabledContentColor = colors.textSecondary,
        ),
        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, maxLines = 1)
    }
}

/** surface 로 채운 보조 버튼 (52dp) */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = ButtonShape,
        contentPadding = PaddingValues(horizontal = 16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = colors.surface, contentColor = colors.textPrimary),
        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, maxLines = 1)
    }
}

/** 강조색 텍스트 버튼 (시트 헤더의 "편집", "초기화" 등) */
@Composable
fun TextAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = AppTheme.colors.textSecondary) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = color,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = singleClick { onClick() })
            .padding(horizontal = 8.dp, vertical = 6.dp)
    )
}

@Composable
fun AppSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val colors = AppTheme.colors
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = colors.onAccent,
            checkedTrackColor = colors.accent,
            checkedBorderColor = colors.accent,
            uncheckedThumbColor = colors.textSecondary,
            uncheckedTrackColor = colors.surface,
            uncheckedBorderColor = colors.outline,
        )
    )
}

/** 공통 바텀시트: 상단 28dp 라운드, 라이트는 흰 시트 / 다크는 한 단계 올라온 면 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBottomSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = AppTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = AppShapes.SheetTop,
        dragHandle = { BottomSheetDefaults.DragHandle(color = colors.outline) },
        containerColor = sheetContainerColor(),
        contentColor = colors.textPrimary,
        content = content,
    )
}

@Composable
fun sheetContainerColor(): Color =
    if (AppTheme.colors.isDark) AppTheme.colors.surface else AppTheme.colors.background

/** 시트 상단 제목 + 오른쪽 액션 + 보조 설명 */
@Composable
fun SheetHeader(
    title: String,
    description: String? = null,
    action: (@Composable () -> Unit)? = null,
    horizontalPadding: Dp = AppDimens.Gutter,
) {
    val colors = AppTheme.colors
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalPadding)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize * 0.9f),
                color = colors.textPrimary,
                modifier = Modifier.weight(1f)
            )
            action?.invoke()
        }
        if (!description.isNullOrEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(description, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
        }
    }
}

/** 선택형 칩 (필터 시트): 선택 시 accentTint 배경 + 체크 + 강조색 테두리 */
@Composable
fun SelectableChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: ImageVector? = null,
) {
    val colors = AppTheme.colors
    Row(
        modifier = modifier
            .height(36.dp)
            .clip(CircleShape)
            .background(if (selected) colors.accentTint else Color.Transparent)
            .border(1.dp, if (selected) colors.accent.copy(alpha = 0.5f) else colors.outline, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val icon = if (selected) Icons.Filled.Check else leading
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = if (selected) colors.accent else colors.textSecondary, modifier = Modifier.size(16.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) colors.accent else colors.textPrimary,
            maxLines = 1,
        )
    }
}
