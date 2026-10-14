package com.jinscompany.saveurl.ui.autocat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jinscompany.saveurl.R
import com.jinscompany.saveurl.domain.autocat.AutoCategoryKeys
import com.jinscompany.saveurl.domain.autocat.AutoRuleStats
import com.jinscompany.saveurl.domain.autocat.RuleSuggestion
import com.jinscompany.saveurl.domain.model.AutoRuleState
import com.jinscompany.saveurl.domain.model.AutoRuleWithCategory
import com.jinscompany.saveurl.ui.composable.AdBannerBar
import com.jinscompany.saveurl.ui.composable.AppSwitch
import com.jinscompany.saveurl.ui.composable.AppTopBar
import com.jinscompany.saveurl.ui.composable.singleClick
import com.jinscompany.saveurl.ui.theme.AppDimens
import com.jinscompany.saveurl.ui.theme.AppTheme
import kotlinx.coroutines.launch

/** 설정 → 자동 분류 (36번 §5, Stitch 08·09·13) */
@Composable
fun AutoCategoryScreen(
    onBack: () -> Unit,
    onOpenTutorial: () -> Unit,
    viewModel: AutoCategoryViewModel = hiltViewModel(),
    sheetViewModel: RuleSheetViewModel = hiltViewModel(),
) {
    val enabled by viewModel.enabled.collectAsState()
    val rules by viewModel.rules.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current

    LaunchedEffect(Unit) {
        viewModel.snacks.collect { snack ->
            val message = when (val m = snack.message) {
                AutoCategoryMessage.RuleCreated -> resources.getString(R.string.autocat_rule_created)
                AutoCategoryMessage.SuggestionDismissed -> resources.getString(R.string.autocat_suggest_dismissed)
                is AutoCategoryMessage.Moved -> resources.getString(R.string.autocat_apply_done, m.count)
                is AutoCategoryMessage.RuleChanged -> resources.getString(R.string.autocat_fix_changed, m.categoryName)
                AutoCategoryMessage.RuleDeleted -> resources.getString(R.string.autocat_rule_deleted)
                AutoCategoryMessage.RuleDisabled -> resources.getString(R.string.autocat_rule_disabled_snackbar)
                AutoCategoryMessage.NothingToMove -> resources.getString(R.string.autocat_apply_nothing)
            }
            scope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                val result = snackbarHostState.showSnackbar(
                    message = message,
                    actionLabel = snack.undo?.let { resources.getString(R.string.autocat_undo) },
                    withDismissAction = snack.undo != null,
                    duration = if (snack.undo != null) SnackbarDuration.Long else SnackbarDuration.Short,
                )
                if (result == SnackbarResult.ActionPerformed) snack.undo?.invoke()
            }
        }
    }

    RuleSheetHost(sheetViewModel) { event -> viewModel.onSheetEvent(event, sheetViewModel) }

    Scaffold(
        containerColor = AppTheme.colors.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = { AdBannerBar() },
    ) { padding ->
        AutoCategoryContent(
            padding = padding,
            enabled = enabled,
            rules = rules,
            suggestions = suggestions,
            onBack = onBack,
            onEnabledChange = viewModel::setEnabled,
            onRuleToggle = viewModel::setRuleEnabled,
            onRuleClick = { sheetViewModel.open(RuleSheetRequest.Edit(it.rule.id)) },
            onFixClick = { sheetViewModel.open(RuleSheetRequest.Fix(it.rule.id)) },
            onAccept = viewModel::accept,
            onDismiss = viewModel::dismiss,
            onOpenTutorial = onOpenTutorial,
        )
    }
}

@Composable
private fun AutoCategoryContent(
    padding: PaddingValues,
    enabled: Boolean,
    rules: AutoCategoryRules,
    suggestions: List<RuleSuggestion>?,
    onBack: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onRuleToggle: (AutoRuleWithCategory, Boolean) -> Unit,
    onRuleClick: (AutoRuleWithCategory) -> Unit,
    onFixClick: (AutoRuleWithCategory) -> Unit,
    onAccept: (RuleSuggestion) -> Unit,
    onDismiss: (RuleSuggestion) -> Unit,
    onOpenTutorial: () -> Unit,
) {
    val colors = AppTheme.colors
    var showAllSuggestions by rememberSaveable { mutableStateOf(false) }
    val visibleSuggestions = if (enabled) suggestions.orEmpty() else emptyList()
    val isEmpty = rules.rules.isEmpty() && suggestions != null && visibleSuggestions.isEmpty()
    Column(modifier = Modifier.fillMaxSize().padding(padding).background(colors.background)) {
        AppTopBar(title = stringResource(R.string.autocat_setting_row), onNavigationClick = onBack)
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            item(key = "master") { MasterRow(enabled = enabled, onChange = onEnabledChange) }
            if (!enabled) {
                item(key = "off_note") {
                    Text(
                        stringResource(R.string.autocat_master_off_note), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary,
                        modifier = Modifier.padding(horizontal = AppDimens.Gutter, vertical = 4.dp),
                    )
                }
            }
            if (rules.needsFix.isNotEmpty()) {
                item(key = "check_label") { SectionLabel(stringResource(R.string.autocat_section_check, rules.needsFix.size)) }
                item(key = "check_box") {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = AppDimens.Gutter).clip(RoundedCornerShape(16.dp))
                            .background(colors.accentTint).padding(vertical = 4.dp),
                    ) {
                        rules.needsFix.forEach { rule -> CheckRow(rule, onFix = { onFixClick(rule) }) }
                    }
                }
            }
            if (enabled) {
                when {
                    suggestions == null -> item(key = "suggest_skeleton") { SuggestionSkeleton() }
                    visibleSuggestions.isNotEmpty() -> {
                        item(key = "suggest_label") { SectionLabel(stringResource(R.string.autocat_section_suggestions, visibleSuggestions.size)) }
                        item(key = "suggest_box") {
                            val shown = if (showAllSuggestions || visibleSuggestions.size <= 4) visibleSuggestions else visibleSuggestions.take(3)
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = AppDimens.Gutter).clip(RoundedCornerShape(16.dp))
                                    .background(colors.surface),
                            ) {
                                shown.forEachIndexed { index, s ->
                                    SuggestionRow(s, onAccept = { onAccept(s) }, onDismiss = { onDismiss(s) })
                                    if (index < shown.lastIndex) HorizontalDivider(color = colors.outline, thickness = 1.dp, modifier = Modifier.padding(horizontal = 16.dp))
                                }
                                if (shown.size < visibleSuggestions.size) {
                                    Text(
                                        stringResource(R.string.autocat_suggest_show_all, visibleSuggestions.size),
                                        style = MaterialTheme.typography.labelLarge, color = colors.accent,
                                        modifier = Modifier.fillMaxWidth().clickable { showAllSuggestions = true }.padding(16.dp),
                                    )
                                }
                            }
                            if (rules.rules.isEmpty()) {
                                Text(
                                    stringResource(R.string.autocat_empty_suggest_only), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary,
                                    modifier = Modifier.padding(horizontal = AppDimens.Gutter, vertical = 8.dp),
                                )
                            }
                        }
                    }
                }
            }
            if (isEmpty) {
                item(key = "empty") { EmptyState(onOpenTutorial) }
            }
            if (rules.rules.isNotEmpty()) {
                item(key = "rules_label") { SectionLabel(stringResource(R.string.autocat_section_rules, rules.rules.size)) }
                val groups = rules.rules.groupBy { it.categoryName }
                groups.forEach { (category, groupRules) ->
                    item(key = "group_$category") {
                        Row(modifier = Modifier.padding(start = AppDimens.Gutter, end = AppDimens.Gutter, top = 10.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (groupRules.first().categoryLocked) {
                                Icon(Icons.Outlined.Lock, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(12.dp))
                                Spacer(Modifier.width(4.dp))
                            }
                            Text(category, fontSize = 12.sp, color = colors.textSecondary)
                        }
                    }
                    items(groupRules, key = { "rule_${it.rule.id}" }) { rule ->
                        RuleRow(rule, onClick = { onRuleClick(rule) }, onToggle = { onRuleToggle(rule, it) })
                    }
                }
                item(key = "priority_note") {
                    Text(
                        stringResource(R.string.autocat_priority_note), fontSize = 12.sp, color = colors.textSecondary,
                        modifier = Modifier.padding(horizontal = AppDimens.Gutter, vertical = 16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
        color = AppTheme.colors.textSecondary,
        modifier = Modifier.padding(start = AppDimens.Gutter, end = AppDimens.Gutter, top = AppDimens.SectionSpacing, bottom = 8.dp),
    )
}

@Composable
private fun MasterRow(enabled: Boolean, onChange: (Boolean) -> Unit) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onChange(!enabled) }.padding(horizontal = AppDimens.Gutter, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.autocat_master_title), style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
            Text(stringResource(R.string.autocat_master_desc), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
        }
        Spacer(Modifier.width(12.dp))
        AppSwitch(checked = enabled, onCheckedChange = onChange)
    }
}

@Composable
private fun CheckRow(rule: AutoRuleWithCategory, onFix: () -> Unit) {
    val colors = AppTheme.colors
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Info, contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.autocat_suggest_row, AutoCategoryViewModel.siteName(rule), rule.categoryName),
                style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(stringResource(R.string.autocat_rule_unhealthy, AutoRuleStats.recentOverrides(rule.rule)), fontSize = 12.sp, color = colors.accent)
        }
        Text(
            stringResource(R.string.autocat_fix_action), style = MaterialTheme.typography.labelLarge, color = colors.accent,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = singleClick { onFix() }).padding(horizontal = 8.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun SuggestionRow(s: RuleSuggestion, onAccept: () -> Unit, onDismiss: () -> Unit) {
    val colors = AppTheme.colors
    Row(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.autocat_suggest_row, AutoCategoryKeys.displayName(s.key), s.categoryName),
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp), color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = if (s.unanimous) stringResource(R.string.autocat_suggest_row_sub, s.count, s.categoryName)
            else stringResource(R.string.autocat_suggest_row_sub_most, s.count, s.topCount, s.categoryName)
            Text(sub, fontSize = 12.sp, color = colors.textSecondary)
        }
        Text(
            stringResource(R.string.autocat_create), style = MaterialTheme.typography.labelLarge, color = colors.onAccent,
            modifier = Modifier.height(32.dp).clip(CircleShape).background(colors.accent).clickable(onClick = singleClick { onAccept() })
                .padding(horizontal = 14.dp, vertical = 6.dp),
        )
        IconButton(onClick = singleClick { onDismiss() }) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.autocat_dismiss_suggestion_cd), tint = colors.textSecondary, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun SuggestionSkeleton() {
    val colors = AppTheme.colors
    Column(modifier = Modifier.padding(horizontal = AppDimens.Gutter, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth(0.7f).height(14.dp).clip(RoundedCornerShape(7.dp)).background(colors.surface))
        Box(Modifier.fillMaxWidth(0.5f).height(14.dp).clip(RoundedCornerShape(7.dp)).background(colors.surface))
    }
}

@Composable
private fun RuleRow(rule: AutoRuleWithCategory, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    val colors = AppTheme.colors
    val off = rule.rule.ruleState == AutoRuleState.OFF
    val unhealthy = !off && !rule.categoryLocked && AutoRuleStats.needsFix(rule.rule)
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = singleClick { onClick() })
            .padding(horizontal = AppDimens.Gutter, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(colors.surface), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Language, contentDescription = null, tint = if (off) colors.outline else colors.textSecondary, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(AutoCategoryViewModel.siteName(rule), style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                color = if (off) colors.textSecondary else colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val stats = rule.rule
            val meta = when {
                rule.categoryLocked -> stringResource(R.string.autocat_rule_locked_meta)
                off -> stringResource(R.string.autocat_rule_off)
                unhealthy -> stringResource(R.string.autocat_rule_unhealthy, AutoRuleStats.recentOverrides(stats))
                stats.overriddenCount > 0 -> stringResource(R.string.autocat_rule_stats, stats.appliedCount, stats.overriddenCount)
                stats.appliedCount > 0 -> stringResource(R.string.autocat_rule_stats_applied, stats.appliedCount)
                else -> stringResource(R.string.autocat_rule_stats_none)
            }
            Text(meta, fontSize = 12.sp, color = if (unhealthy) colors.accent else colors.textSecondary)
        }
        AppSwitch(checked = !off, onCheckedChange = onToggle)
    }
}

@Composable
private fun EmptyState(onOpenTutorial: () -> Unit) {
    val colors = AppTheme.colors
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(colors.accentTint), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Bolt, contentDescription = null, tint = colors.accent, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.autocat_empty_title), style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.autocat_empty_body), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.autocat_empty_tutorial), style = MaterialTheme.typography.labelLarge, color = colors.accent,
            modifier = Modifier.clip(CircleShape).border(1.dp, colors.outline, CircleShape).clickable(onClick = singleClick { onOpenTutorial() })
                .padding(horizontal = 18.dp, vertical = 10.dp),
        )
    }
}
