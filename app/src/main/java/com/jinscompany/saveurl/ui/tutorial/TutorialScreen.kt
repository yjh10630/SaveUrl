package com.jinscompany.saveurl.ui.tutorial

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoveToInbox
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.VerticalSplit
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jinscompany.saveurl.R
import com.jinscompany.saveurl.ui.adaptive.LocalInRightPane
import com.jinscompany.saveurl.ui.adaptive.LocalWindowLayout
import com.jinscompany.saveurl.ui.composable.AdBannerBar
import com.jinscompany.saveurl.ui.composable.AppTopBar
import com.jinscompany.saveurl.ui.composable.ButtonShape
import com.jinscompany.saveurl.ui.composable.singleClick
import com.jinscompany.saveurl.ui.theme.AppDimens
import com.jinscompany.saveurl.ui.theme.AppShapes
import com.jinscompany.saveurl.ui.theme.AppTheme
import com.jinscompany.saveurl.ui.theme.SaveUrlTheme

/** 넓은 패널·태블릿에서 본문이 너무 길어지지 않게 하는 최대 폭 */
private val ContentMaxWidth = 720.dp

/**
 * 튜토리얼 주제 표시 조건([TutorialAvailability.Flag])에 쓰는 기능 플래그.
 * 지금은 켜진 플래그가 없다. Remote Config 등을 붙일 때 MainActivity 에서 provide 한다.
 */
val LocalTutorialFeatureFlags = staticCompositionLocalOf { TutorialFeatureFlags.None }

/** 지금 화면 구성(2분할 여부)과 기능 플래그로 거른 주제 목록 */
@Composable
fun currentVisibleTutorialTopics(): List<TutorialTopic> {
    val isTwoPane = LocalWindowLayout.current.isTwoPane
    val flags = LocalTutorialFeatureFlags.current
    return remember(isTwoPane, flags) { TutorialTopics.visible(TutorialAvailabilityContext(isTwoPane, flags)) }
}

// -------------------------------------------------------------------------------------------------
// 홈 (주제 목록)
// -------------------------------------------------------------------------------------------------

/**
 * 튜토리얼 홈: 소개 + 진행 표시 + "처음부터 차례로 보기" + 그룹별 주제 목록 (본 주제는 체크).
 * 폰은 전체 화면(하단 광고), 2분할은 오른쪽 패널 안에 그린다 (광고는 2분할 레이아웃이 한 번만 그림).
 */
@Composable
fun TutorialHomeScreen(
    onClose: () -> Unit,
    onOpenTopic: (String) -> Unit,
    viewModel: TutorialViewModel = hiltViewModel(),
) {
    val viewed by viewModel.viewedTopicIds.collectAsState()
    val badgePrefs by viewModel.badgePrefs.collectAsState()
    val visible = currentVisibleTutorialTopics()
    TutorialHomeContent(
        topics = visible,
        viewedIds = viewed,
        newIds = TutorialNews.newIds(visible, badgePrefs),
        onClose = onClose,
        onOpenTopic = onOpenTopic,
    )
}

@Composable
fun TutorialHomeContent(
    topics: List<TutorialTopic>,
    viewedIds: Set<String>?,
    onClose: () -> Unit,
    onOpenTopic: (String) -> Unit,
    /** "NEW" 표시할 주제 id ([TutorialNews.newIds]) */
    newIds: Set<String> = emptySet(),
) {
    val colors = AppTheme.colors
    Scaffold(
        containerColor = colors.background,
        bottomBar = { AdBannerBar() },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(colors.background)
        ) {
            AppTopBar(
                title = stringResource(R.string.tutorial_title),
                onNavigationClick = onClose,
                navigationIcon = Icons.Filled.Close,
                navigationContentDescription = stringResource(R.string.tutorial_close),
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = PaddingValues(bottom = 32.dp),
            ) {
                item(key = "intro") {
                    TutorialIntro(topics = topics, viewedIds = viewedIds, onStart = {
                        topics.firstOrNull()?.let { onOpenTopic(it.id) }
                    })
                }
                topics.groupBy { it.group }.forEach { (group, groupTopics) ->
                    item(key = "group_${group.name}") {
                        Text(
                            text = stringResource(group.labelRes),
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.textSecondary,
                            modifier = Modifier
                                .contentWidth()
                                .padding(start = AppDimens.Gutter + 4.dp, end = AppDimens.Gutter, top = 24.dp, bottom = 8.dp)
                                .semantics { heading() }
                        )
                    }
                    item(key = "list_${group.name}") {
                        Column(
                            modifier = Modifier
                                .contentWidth()
                                .padding(horizontal = AppDimens.Gutter)
                                .clip(AppShapes.Card)
                                .background(colors.surface)
                        ) {
                            groupTopics.forEachIndexed { index, topic ->
                                TopicRow(
                                    topic = topic,
                                    viewed = viewedIds?.contains(topic.id) == true,
                                    isNew = topic.id in newIds,
                                    onClick = { onOpenTopic(topic.id) },
                                )
                                if (index < groupTopics.lastIndex) {
                                    HorizontalDivider(
                                        color = if (colors.isDark) colors.background else colors.outline,
                                        thickness = 1.dp,
                                        modifier = Modifier.padding(start = 72.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 최대 폭 제한 + 가운데 정렬 (LazyColumn 의 horizontalAlignment 와 함께) */
private fun Modifier.contentWidth(): Modifier = this.widthIn(max = ContentMaxWidth).fillMaxWidth()

@Composable
private fun TutorialIntro(topics: List<TutorialTopic>, viewedIds: Set<String>?, onStart: () -> Unit) {
    val colors = AppTheme.colors
    val progress = TutorialProgress.of(topics, viewedIds.orEmpty())
    Column(modifier = Modifier.contentWidth().padding(horizontal = AppDimens.Gutter)) {
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(colors.accent),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Link, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(26.dp))
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.tutorial_intro_title),
            style = MaterialTheme.typography.titleLarge,
            color = colors.textPrimary,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.tutorial_intro_body),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 22.sp),
            color = colors.textSecondary,
        )
        Spacer(modifier = Modifier.height(20.dp))
        // 진행 표시: 읽기 전(null)에는 자리만 잡아 둔다
        val progressText = stringResource(R.string.tutorial_progress, progress.total, progress.viewed)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (viewedIds == null) "" else progressText,
                style = MaterialTheme.typography.labelLarge,
                color = colors.accent,
                modifier = Modifier.weight(1f)
            )
            if (viewedIds != null) {
                Text(
                    text = stringResource(R.string.tutorial_progress_percent, progress.percent),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary,
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { progress.fraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape),
            color = colors.accent,
            trackColor = colors.outline,
            drawStopIndicator = {},
            gapSize = 0.dp,
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = singleClick { onStart() },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp),
            shape = ButtonShape,
            colors = ButtonDefaults.buttonColors(containerColor = colors.accentTint, contentColor = colors.accent),
            elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
        ) {
            Text(stringResource(R.string.tutorial_start_from_first), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun TopicRow(topic: TutorialTopic, viewed: Boolean, onClick: () -> Unit, isNew: Boolean = false) {
    val colors = AppTheme.colors
    val viewedLabel = stringResource(R.string.tutorial_viewed_cd)
    val newLabel = stringResource(R.string.tutorial_new_badge_cd)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .clickable(onClick = singleClick { onClick() })
            .semantics(mergeDescendants = true) {
                if (viewed) stateDescription = viewedLabel else if (isNew) stateDescription = newLabel
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(colors.background),
            contentAlignment = Alignment.Center
        ) {
            Icon(topicIcon(topic.id), contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(topic.titleRes),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (isNew) {
                    Spacer(modifier = Modifier.width(8.dp))
                    NewPill()
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(topic.subtitleRes),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                color = colors.textSecondary,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        if (viewed) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = colors.accent, modifier = Modifier.size(22.dp))
        } else {
            Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(22.dp))
        }
    }
}

/** 새 주제 표시 알약 (강조색 바탕). 화면 읽기 프로그램에는 행의 상태("새 기능")로 읽힌다 */
@Composable
fun NewPill(text: String = stringResource(R.string.tutorial_new_badge), modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp),
        color = colors.onAccent,
        maxLines = 1,
        modifier = modifier
            .clip(CircleShape)
            .background(colors.accent)
            .padding(horizontal = 7.dp, vertical = 2.dp)
            .clearAndSetSemantics {}
    )
}

private fun topicIcon(id: String): ImageVector = when (id) {
    TutorialTopics.SHARE -> Icons.Outlined.Share
    TutorialTopics.DIRECT -> Icons.Outlined.Add
    TutorialTopics.IMPORT -> Icons.Outlined.MoveToInbox
    TutorialTopics.AUTOFILL -> Icons.Outlined.Image
    TutorialTopics.CLEAN -> Icons.Outlined.CleaningServices
    TutorialTopics.CATEGORY -> Icons.Outlined.Folder
    TutorialTopics.CATEGORY_LOCK -> Icons.Outlined.Lock
    TutorialTopics.AUTO_CATEGORY -> Icons.Outlined.Bolt
    TutorialTopics.TAG -> Icons.AutoMirrored.Outlined.Label
    TutorialTopics.FAVORITE -> Icons.Outlined.StarOutline
    TutorialTopics.VIEW_MODE -> Icons.Outlined.ViewAgenda
    TutorialTopics.SEARCH -> Icons.Outlined.Search
    TutorialTopics.MANAGE -> Icons.Outlined.TouchApp
    TutorialTopics.TRASH -> Icons.Outlined.Delete
    TutorialTopics.SETTINGS -> Icons.Outlined.Palette
    TutorialTopics.TWO_PANE -> Icons.Outlined.VerticalSplit
    else -> Icons.Outlined.BugReport
}

// -------------------------------------------------------------------------------------------------
// 주제 상세
// -------------------------------------------------------------------------------------------------

/**
 * 주제 상세: 그룹 라벨, 제목, 본문, 번호 단계, 스크린샷(앱 화면 모드에 맞는 라이트/다크), 팁, "목록으로" / "다음 주제".
 * 열면 본 주제로 저장한다.
 */
@Composable
fun TutorialTopicScreen(
    topicId: String,
    onBackToList: () -> Unit,
    onOpenNext: (String) -> Unit,
    onClose: () -> Unit,
    viewModel: TutorialViewModel = hiltViewModel(),
) {
    val topic = TutorialTopics.find(topicId)
    LaunchedEffect(topicId) {
        if (topic != null) viewModel.markViewed(topic.id)
    }
    if (topic == null) {
        // 알 수 없는 주제(저장된 백스택 복원 등): 목록으로 돌아간다
        LaunchedEffect(Unit) { onBackToList() }
        return
    }
    val visible = currentVisibleTutorialTopics()
    TutorialTopicContent(
        topic = topic,
        position = TutorialTopics.position(visible, topic.id),
        total = visible.size,
        next = TutorialTopics.next(visible, topic.id),
        onBackToList = onBackToList,
        onOpenNext = onOpenNext,
        onClose = onClose,
    )
}

@Composable
fun TutorialTopicContent(
    topic: TutorialTopic,
    position: Int?,
    total: Int,
    next: TutorialTopic?,
    onBackToList: () -> Unit,
    onOpenNext: (String) -> Unit,
    onClose: () -> Unit,
) {
    val colors = AppTheme.colors
    val inRightPane = LocalInRightPane.current
    Scaffold(
        containerColor = colors.background,
        bottomBar = { AdBannerBar() },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(colors.background)
        ) {
            TopicTopBar(
                title = if (inRightPane) stringResource(R.string.tutorial_breadcrumb, stringResource(topic.group.labelRes))
                        else stringResource(R.string.tutorial_title),
                position = position,
                total = total,
                onBack = onBackToList,
                onClose = if (inRightPane) onClose else null,
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .contentWidth()
                        .padding(horizontal = AppDimens.Gutter)
                        .padding(top = 8.dp, bottom = 32.dp)
                ) {
                    TopicBody(topic)
                    Spacer(modifier = Modifier.height(28.dp))
                    TopicActions(next = next, onBackToList = onBackToList, onOpenNext = onOpenNext)
                }
            }
        }
    }
}

@Composable
private fun TopicTopBar(
    title: String,
    position: Int?,
    total: Int,
    onBack: () -> Unit,
    onClose: (() -> Unit)?,
) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (onClose != null) 64.dp else 56.dp)
            .padding(start = 4.dp, end = if (onClose != null) 12.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = singleClick { onBack() }) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.tutorial_back_to_list_cd),
                tint = colors.textPrimary
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = title,
            style = if (onClose != null) MaterialTheme.typography.titleMedium
                    else MaterialTheme.typography.titleLarge.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize * 0.9f),
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (position != null) {
            val positionCd = stringResource(R.string.tutorial_position_cd, position, total)
            Text(
                text = stringResource(R.string.tutorial_position, position, total),
                style = MaterialTheme.typography.labelLarge,
                color = colors.textSecondary,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .semantics { contentDescription = positionCd }
            )
        }
        if (onClose != null) {
            IconButton(onClick = singleClick { onClose() }) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.tutorial_close), tint = colors.textPrimary)
            }
        }
    }
}

@Composable
private fun TopicBody(topic: TutorialTopic) {
    val colors = AppTheme.colors
    Text(
        text = stringResource(topic.group.labelRes),
        style = MaterialTheme.typography.labelLarge,
        color = colors.accent,
    )
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = stringResource(topic.titleRes),
        style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp, lineHeight = 32.sp),
        color = colors.textPrimary,
        modifier = Modifier.semantics { heading() }
    )
    Spacer(modifier = Modifier.height(10.dp))
    Text(
        text = TutorialText.annotated(
            stringResource(topic.bodyRes),
            androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
        ),
        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 23.sp),
        color = colors.textSecondary,
    )
    if (topic.stepRes.isNotEmpty()) {
        Spacer(modifier = Modifier.height(20.dp))
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            topic.stepRes.forEachIndexed { index, res -> StepRow(number = index + 1, text = stringResource(res)) }
        }
    }
    if (topic.imageRows.isNotEmpty()) {
        Spacer(modifier = Modifier.height(24.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            topic.imageRows.forEach { row -> ScreenshotRow(row) }
        }
    }
    topic.tipRes?.let { tip ->
        Spacer(modifier = Modifier.height(24.dp))
        TipBox(text = stringResource(tip))
    }
}

@Composable
private fun StepRow(number: Int, text: String) {
    val colors = AppTheme.colors
    // 큰 글꼴에서도 숫자가 원 밖으로 나가지 않게 원을 함께 키운다 (최대 1.6배)
    val badgeSize = 24.dp * LocalDensity.current.fontScale.coerceIn(1f, 1.6f)
    Row(
        modifier = Modifier.semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 1.dp)
                .size(badgeSize)
                .clip(CircleShape)
                .background(colors.accent),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp),
                color = colors.onAccent,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 22.sp),
            color = colors.textPrimary,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * 스크린샷 묶음 한 줄: surface 받침(모서리 16dp) 안에 이미지를 나란히 놓는다.
 * 폰 화면 한 장은 최대 280dp, 여러 장은 최대 560dp, 2분할 가로 캡처는 최대 640dp.
 */
@Composable
private fun ScreenshotRow(images: List<TutorialImage>) {
    val colors = AppTheme.colors
    val maxWidth = when {
        images.any { it.wide } -> 640.dp
        images.size == 1 -> 280.dp
        else -> 560.dp
    }
    val imageShape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .widthIn(max = maxWidth)
            .fillMaxWidth()
            .clip(AppShapes.Card)
            .background(colors.surface)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        images.forEach { image ->
            val painter = painterResource(image.resFor(colors.isDark))
            val size = painter.intrinsicSize
            val ratio = if (size.width > 0f && size.height > 0f) size.width / size.height else 0.5f
            Image(
                painter = painter,
                contentDescription = stringResource(image.descriptionRes),
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(ratio)
                    .clip(imageShape)
                    .border(1.dp, colors.outline, imageShape)
            )
        }
    }
}

@Composable
private fun TipBox(text: String) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.accentTint)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(Icons.Outlined.Lightbulb, contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = TutorialText.annotated("**${stringResource(R.string.tutorial_tip_label)}** $text"),
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp),
            color = colors.accent,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun TopicActions(next: TutorialTopic?, onBackToList: () -> Unit, onOpenNext: (String) -> Unit) {
    val colors = AppTheme.colors
    if (next == null) {
        Button(
            onClick = singleClick { onBackToList() },
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            shape = ButtonShape,
            colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
            elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
        ) {
            Text(stringResource(R.string.tutorial_finish), style = MaterialTheme.typography.titleMedium)
        }
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = singleClick { onBackToList() }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.tutorial_to_list), style = MaterialTheme.typography.titleMedium, color = colors.textSecondary)
        }
        Spacer(modifier = Modifier.weight(1f).widthIn(min = 8.dp))
        Button(
            onClick = singleClick { onOpenNext(next.id) },
            modifier = Modifier.weight(3f, fill = false).heightIn(min = 52.dp),
            shape = CircleShape,
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
            elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
        ) {
            Text(
                text = stringResource(R.string.tutorial_next_topic, stringResource(next.titleRes)),
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}

// -------------------------------------------------------------------------------------------------
// 프리뷰
// -------------------------------------------------------------------------------------------------

@Preview(showBackground = true, heightDp = 1400)
@Composable
private fun TutorialHomePreview() {
    SaveUrlTheme(darkTheme = false) {
        TutorialHomeContent(
            topics = TutorialTopics.visible(isTwoPane = false),
            viewedIds = setOf(TutorialTopics.SHARE, TutorialTopics.DIRECT),
            onClose = {}, onOpenTopic = {},
        )
    }
}

/** NEW 표시 확인용 (가짜 주제는 미리보기 안에서만 쓴다) */
@Preview(showBackground = true, heightDp = 700)
@Composable
private fun TutorialHomeNewPreview() {
    val fake = TutorialTopics.all.first().copy(id = "preview_new", group = TutorialGroup.START, addedInVersionCode = 99)
    val topics = listOf(fake) + TutorialTopics.visible(isTwoPane = false)
    SaveUrlTheme(darkTheme = true) {
        TutorialHomeContent(
            topics = topics,
            viewedIds = setOf(TutorialTopics.SHARE),
            newIds = TutorialNews.newIds(topics, TutorialBadgePrefs(viewedIds = setOf(TutorialTopics.SHARE), baselineVersionCode = 38)),
            onClose = {}, onOpenTopic = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 1200)
@Composable
private fun TutorialTopicPreview() {
    val visible = TutorialTopics.visible(isTwoPane = false)
    SaveUrlTheme(darkTheme = true) {
        TutorialTopicContent(
            topic = visible[7], position = 8, total = visible.size, next = visible[8],
            onBackToList = {}, onOpenNext = {}, onClose = {},
        )
    }
}
