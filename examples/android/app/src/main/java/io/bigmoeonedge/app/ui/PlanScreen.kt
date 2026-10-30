package io.bigmoeonedge.app.ui

import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.bigmoeonedge.app.engine.AppliedChange
import io.bigmoeonedge.app.engine.Engine
import io.bigmoeonedge.app.engine.PlanFigures
import io.bigmoeonedge.app.engine.appliedChanges
import io.bigmoeonedge.app.engine.diagnosticText
import io.bigmoeonedge.app.engine.loadTargetChosen
import io.bigmoeonedge.app.engine.planFigures
import io.bigmoeonedge.app.engine.PlanDecision
import io.bigmoeonedge.app.engine.PlanDevice
import io.bigmoeonedge.app.engine.PlanPool
import io.bigmoeonedge.app.engine.PlanProgress
import io.bigmoeonedge.app.engine.PlanResult
import kotlinx.coroutines.launch
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import io.bigmoeonedge.app.engine.planModelName
import java.util.Locale

/**
 * 方案页：硬件规划器在这台手机上测得了什么，以及它为所选模型做出了什么决定。
 * 自上而下阅读的顺序与 Web 界面一致（见 docs/screens.md）：一句话结论及其实际表现、
 * 内存去向、每个设备的作用、分组后的决策、启动命令。所有机型共用同一套布局，
 * 各个前端也完全一致：每个区块都只是渲染方案中已有的内容。方案本身存于引擎状态，
 * 引擎在会话打开与回合结束时重新读取，因此本页只负责展示。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanScreen(modifier: Modifier = Modifier, onOpenSetting: (key: String) -> Unit = {}, onOpenModels: () -> Unit = {}) {
    val engine by Engine.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var applied by remember { mutableStateOf<List<AppliedChange>?>(null) }

    // 引擎在连上服务端、会话打开以及回合结束时读取方案；绝不会主动触发测量，
    // 因为在手机上测量耗时较长，不应自行启动。若页面在引擎就绪前打开，则只请求一次。
    LaunchedEffect(engine.serverUp) {
        if (engine.serverUp && engine.plan == null) Engine.refreshPlan()
    }
    fun measure() {
        applied = null
        Engine.measurePlan()
    }

    val cfg = engine.config
    val plan = engine.plan
    val busy = engine.measuring
    val planner = engine.info?.plannerAvailable ?: true
    // 方案针对某次加载所打开的模型：该模型在“模型”页选择，参数表将其标记为只读分组。
    val chosen = engine.info?.model != null || loadTargetChosen(engine.params, cfg)
    val canMeasure = planner && chosen && !busy && engine.serverUp
    val modelName = planModelName(plan, engine.params, cfg, engine.info?.model?.path)
    // 所有出错情况都汇总到同一条提示：请求失败，或服务端无法生成方案。
    val shownError = engine.planError.ifEmpty { plan?.takeIf { it.available }?.error.orEmpty() }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("方案") },
                navigationIcon = { Box(Modifier.padding(start = 16.dp)) { BrandMark(wordmark = false) } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!planner) Text("此版本不包含硬件规划器。", color = MaterialTheme.colorScheme.error)
            // 先说明方案针对哪个模型：一个方案只对应一个模型，测量也围绕它进行。
            if (modelName.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        buildAnnotatedString {
                            append("针对 ")
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(modelName) }
                        },
                        fontSize = 14.sp, modifier = Modifier.weight(1f),
                    )
                    TextButton(shape = Corner, onClick = onOpenModels) { Text("更换") }
                }
            } else if (!chosen && engine.params != null) {
                Text("请先在“模型”页选择一个模型：一个方案只对应这台手机上的一个模型。", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // 失败的方案可在提示处重试：测量必须由用户点击触发，绝不自动进行。
            if (shownError.isNotEmpty()) RetryLine(shownError, canMeasure) { measure() }
            if (busy || engine.planProgress != null) PlanProgressCard(engine.planProgress)

            val p = plan
            if (p?.available == true && cfg != null) {
                AutoPlanSwitch(cfg.autoPlan, planner) { on -> scope.launch { Engine.setAutoPlan(on) } }
            }
            when {
                p == null -> {}
                !p.available -> Text(p.error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                p.error.isNotEmpty() -> {} // 在上方提示条中显示
                p.none -> Tile {
                    Text("尚未测量。", fontSize = 22.sp, lineHeight = 26.sp)
                    Text(
                        if (modelName.isNotEmpty()) "为 $modelName 测量这台手机。" else "测量这台手机，看看方案会如何处理所选模型。",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    PrimaryAction(if (busy) "正在测量…" else "开始测量", enabled = canMeasure) { measure() }
                }
                else -> PlanBody(
                    p,
                    canApply = !busy,
                    onApply = {
                        val before = engine.config?.values.orEmpty()
                        applied = null
                        scope.launch {
                            Engine.applyPlan().onSuccess { c -> applied = appliedChanges(before, c.values, engine.params.orEmpty()) }
                                .onFailure { snackbar.showSnackbar(it.message ?: "应用方案失败") }
                        }
                    },
                    applied = applied,
                    canMeasure = canMeasure,
                    measuring = busy,
                    onMeasure = { measure() },
                    reloadNeeded = engine.ready && cfg?.reloadRequired == true,
                    onOpenSetting = onOpenSetting,
                    onCopied = { scope.launch { snackbar.showSnackbar("已复制：启动命令与规划器的详细说明") } },
                )
            }
            // 在未加载模型且没有可展示的方案时，显示当前配置沿用的上次已应用方案的决策，
            // 分组方式保持一致：只要生成过方案，本页就不会是空的。
            val carried = cfg?.plan.orEmpty()
            if (engine.info?.model == null && carried.isNotEmpty() && (p == null || p.none || p.error.isNotEmpty() || !p.available)) {
                Text("来自上次已应用方案的决策", style = MaterialTheme.typography.titleMedium)
                Hint("当前设置沿用的内容；点击“开始测量”可查看完整方案。")
                Decisions(carried, onOpenSetting)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PrimaryAction(text: String, enabled: Boolean, onClick: () -> Unit) {
    Button(shape = Corner, onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(text, fontSize = 16.sp)
    }
}

@Composable
private fun AutoPlanSwitch(checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Plan before every load", fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

/** An error with the way to try again: Measure again, beside it. */
@Composable
private fun RetryLine(error: String, canMeasure: Boolean, onMeasure: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.weight(1f))
        OutlinedButton(shape = Corner, onClick = onMeasure, enabled = canMeasure) { Text("Measure again") }
    }
}

@Composable
private fun PlanBody(
    p: PlanResult,
    canApply: Boolean,
    onApply: () -> Unit,
    applied: List<AppliedChange>?,
    canMeasure: Boolean,
    measuring: Boolean,
    onMeasure: () -> Unit,
    reloadNeeded: Boolean,
    onOpenSetting: (String) -> Unit,
    onCopied: () -> Unit,
) {
    if (p.warning.isNotEmpty()) Text(p.warning, fontSize = 12.sp, color = MaterialTheme.colorScheme.tertiary)
    val f = planFigures(p)
    Tile {
        when {
            p.fromLastLoad -> StatusBadge("当前加载的模型正在按此方案运行", okColor())
            p.cached -> StatusBadge("上次测量的方案", MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(p.sentence.ifEmpty { "模式：${p.regime}" }, fontSize = 22.sp, lineHeight = 26.sp)
        if (p.machine.isNotEmpty()) Text(p.machine, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (p.lanes.isNotEmpty()) {
            Text(
                p.lanes.joinToString("   ") { "${it.label} ${it.on.joinToString(" + ")}" },
                fontSize = 13.sp, color = MaterialTheme.colorScheme.primary,
            )
        }
        // 三个关键数字：解码速度、缓存命中、内存占用；在手机上每行两个。
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SpeedWell(f, p.fromLastLoad, Modifier.weight(1f))
            HitsWell(f, Modifier.weight(1f))
        }
        if (f.capBytes != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MemoryWell(f, Modifier.weight(1f))
                Spacer(Modifier.weight(1f))
            }
        }
        if (p.notApplicable.isNotEmpty()) {
            Text("已决定，但本应用无法保存为设置项：${p.notApplicable.joinToString("；")}", fontSize = 12.sp, color = MaterialTheme.colorScheme.tertiary)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(shape = Corner, onClick = onApply, enabled = canApply, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("应用方案") }
            OutlinedButton(shape = Corner, onClick = onMeasure, enabled = canMeasure, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                Text(if (measuring) "正在测量…" else "重新测量")
            }
        }
        Text("你自己设置的值永远不会被改动。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        applied?.let { changes ->
            if (changes.isEmpty()) {
                Text("方案已应用：所有设置原本就已是方案中的取值。", fontSize = 13.sp, color = okColor())
            } else {
                Text("方案已应用。${if (changes.size == 1) "已改动 1 项设置" else "已改动 ${changes.size} 项设置"}：", fontSize = 13.sp, color = okColor())
                changes.forEach { c ->
                    Row(Modifier.fillMaxWidth().clickable { onOpenSetting(c.key) }.padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(c.label, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                        Text("${c.from} → ${c.to}", style = FigureStyle, fontSize = 13.sp)
                    }
                }
            }
            if (reloadNeeded) Text("部分已应用的值需要重新加载模型：可在“设置”页点击“重新加载”。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (p.pools.isNotEmpty()) {
        Section("内存") { p.pools.forEach { MemoryPool(it) } }
    }
    if (p.devices.isNotEmpty()) {
        Section("设备") {
            val scale = p.devices.maxOf { it.promptRatio }.coerceAtLeast(1.0)
            p.devices.forEach { DeviceRow(it, scale) }
            if (p.devicesNoneText.isNotEmpty()) Text(p.devicesNoneText, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Text("决策", style = MaterialTheme.typography.titleMedium)
    Decisions(p.decisions, onOpenSetting)

    if (p.args.isNotEmpty()) Folded("启动命令") {
        SelectionContainer { Text("bmoe-cli " + p.args.joinToString(" "), fontFamily = CodeFont, fontSize = 11.sp) }
    }
    // 规划器的完整原始说明不直接显示（其中每一行在上方都有对应的通俗表述）；
    // 它会随命令一起复制到剪贴板，便于提交问题报告。
    val clipboard = LocalClipboardManager.current
    val uri = LocalUriHandler.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(shape = Corner, onClick = { clipboard.setText(AnnotatedString(diagnosticText(p.args, p.explain))); onCopied() }, modifier = Modifier.weight(1f)) {
            Text("复制诊断信息")
        }
        TextButton(shape = Corner, onClick = { runCatching { uri.openUri(PLANNING_DOC) } }, modifier = Modifier.weight(1f)) { Text("规划器的决策依据") }
    }
}

private const val PLANNING_DOC = "https://github.com/Helldez/BigMoeOnEdge/blob/main/docs/hardware-planning.md"

@Composable
private fun SpeedWell(f: PlanFigures, fromLastLoad: Boolean, modifier: Modifier) {
    Well(modifier) {
        Text("Token 速率", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        when {
            f.measuredTokS != null -> {
                Figure(fmt(f.measuredTokS), "实测 tok/s")
                if (f.deltaPct != null && f.predictedTokS != null) {
                    if (fromLastLoad) {
                        Text(
                            String.format(Locale.US, "%+.0f%%，预期值为 %s", f.deltaPct, fmt(f.predictedTokS)),
                            fontSize = 12.sp, color = if (f.deltaPct >= 0) okColor() else warnColor(),
                        )
                    } else {
                        Text("此方案预期 ${fmt(f.predictedTokS)} tok/s", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            f.predictedTokS != null -> {
                Figure(fmt(f.predictedTokS), "预期 tok/s")
                Text(if (fromLastLoad) "首次回答后测量" else "加载完成后测量", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> Text("无预测值", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MemoryWell(f: PlanFigures, modifier: Modifier) {
    val cap = f.capBytes
    if (cap == null) {
        Spacer(modifier)
        return
    }
    val held = f.heldBytes ?: 0
    Well(modifier) {
        Text("内存", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Figure(bytesLabel(held), "已占用")
        Meter((held.toDouble() / cap).toFloat(), MaterialTheme.colorScheme.primary)
        Text("可用上限为 ${bytesLabel(cap)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HitsWell(f: PlanFigures, modifier: Modifier) {
    val measured = f.measuredHitPct
    val credited = f.creditedHitPct
    if (measured == null && credited == null) {
        Spacer(modifier)
        return
    }
    Well(modifier) {
        Text("缓存命中", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Figure(String.format(Locale.US, "%.0f%%", measured ?: credited), if (measured != null) "实测" else "计入")
        Meter(((measured ?: credited ?: 0.0) / 100).toFloat(), MaterialTheme.colorScheme.primary, mark = if (measured != null) credited?.let { (it / 100).toFloat() } else null)
        if (measured != null && credited != null) {
            Text(String.format(Locale.US, "标记位置为计入的 %.0f%%", credited), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** One pool as one bar: every held row, then what is free, against the larger of the cap and the sum. */
@Composable
private fun MemoryPool(pool: PlanPool) {
    val held = pool.rows.filter { !it.inTurn }
    val sum = held.sumOf { it.bytes }
    val whole = maxOf(pool.capBytes, sum).coerceAtLeast(1)
    val free = whole - sum
    // 使用分类色板而非主题角色色：主题色在并排时难以区分，
    // 而这种由多个进程组成的条状图正是靠区分各段来阅读的。
    val colours = tokens.series
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(pool.label + if (pool.estimated) "（各设备均分）" else "", fontSize = 14.sp)
            if (pool.capBytes > 0) Text("上限 " + bytesLabel(pool.capBytes), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth().height(24.dp).clip(Corner).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
            held.forEachIndexed { i, r ->
                val w = r.bytes.toFloat() / whole
                if (w > 0f) Box(Modifier.fillMaxHeight().weight(w).background(colours[i % colours.size]))
            }
            if (free > 0) Spacer(Modifier.fillMaxHeight().weight(free.toFloat() / whole))
        }
        held.forEachIndexed { i, r ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(12.dp).clip(MaterialTheme.shapes.extraSmall).background(colours[i % colours.size]))
                Text(r.label, fontSize = 14.sp, modifier = Modifier.weight(1f))
                if (r.locked) StatusBadge("已锁定", MaterialTheme.colorScheme.onSurfaceVariant)
                Text(bytesLabel(r.bytes), fontSize = 14.sp)
            }
        }
        if (free > 0) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(12.dp).border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.extraSmall))
                Text("空闲", fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text(bytesLabel(free), fontSize = 14.sp)
            }
        }
        pool.rows.filter { it.inTurn }.forEach {
            Text("${it.label}：处理提示词期间从专家缓存借用 ${bytesLabel(it.bytes)}，之后归还。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DeviceRow(d: PlanDevice, scale: Double) {
    val colour = deviceColour(d)
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(d.name, fontSize = 16.sp)
                if (d.description.isNotEmpty() && d.description != d.name) Text(d.description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            StatusBadge(d.badge, colour)
        }
        val used = d.status == "used"
        Meter(if (d.promptRatio > 0) maxOf(0.02f, (d.promptRatio / scale).toFloat()) else 0f, if (used) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        if (d.rateText.isNotEmpty()) Text(d.rateText, fontSize = 12.sp)
        Text(d.why, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The three groups: the settings the plan decided, open; the ones it left and the notes, folded. */
@Composable
private fun Decisions(all: List<PlanDecision>, onOpenSetting: (String) -> Unit) {
    val by = all.groupBy { it.group.ifEmpty { "set" } }
    by["set"]?.let { rows -> Tile { Text("由方案设定 · ${rows.size}", fontSize = 16.sp); rows.forEach { DecisionRow(it, onOpenSetting) } } }
    by["kept"]?.let { rows ->
        Folded("维持原值 · ${rows.size}", "方案已评估、但保持原样的设置。") { rows.forEach { DecisionRow(it, onOpenSetting) } }
    }
    by["notes"]?.let { rows ->
        Folded("备注 · ${rows.size}", "方案测量与权衡的内容。这些都不是可设置项。") { rows.forEach { DecisionRow(it, onOpenSetting) } }
    }
}

/** 单条决策：标签与取值；点击后显示一行摘要、展开“原因”可见完整理由，以及对应设置项。 */
@Composable
private fun DecisionRow(d: PlanDecision, onOpenSetting: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var why by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clickable { open = !open }.padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (open) "v" else ">", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(d.label.ifEmpty { d.knob }, fontSize = 14.sp, modifier = Modifier.weight(1f))
            val shown = d.valueText.ifEmpty { d.value.takeIf { it != "-" }.orEmpty() }
            if (shown.isNotEmpty()) Text(shown, fontSize = 14.sp)
            SourceBadge(d.source)
        }
        if (open) {
            Column(Modifier.padding(start = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(d.brief.ifEmpty { d.reason }, fontSize = 13.sp)
                if (d.brief.isNotEmpty() && d.brief != d.reason) {
                    Text(if (why) "原因  v" else "原因  >", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { why = !why })
                    if (why) Text(d.reason, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (d.label.isNotEmpty() && d.group != "notes") {
                    Text("在设置中查看 ${d.label}", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { onOpenSetting(d.knob) })
                }
            }
        }
    }
}

/** 进行中的测量，采用服务端的说法：已完成、进行中、还剩多少项。“模型”页加载模型时也会显示。 */
@Composable
fun PlanProgressCard(p: PlanProgress?) {
    var done by remember { mutableStateOf(listOf<String>()) }
    var last by remember { mutableStateOf("") }
    LaunchedEffect(p) {
        if (p == null) return@LaunchedEffect
        if (p.index == 0) done = emptyList()
        if (last.isNotEmpty() && last != p.label && last !in done) done = done + last
        last = p.label
    }
    Tile {
        Text("正在测量这台手机", fontSize = 18.sp)
        Meter(if (p != null && p.count > 0) p.index.toFloat() / p.count else 0f, MaterialTheme.colorScheme.primary)
        done.forEach { StepLine("完成", okColor(), it) }
        if (p != null) {
            StepLine("进行中", MaterialTheme.colorScheme.primary, p.label)
            val left = p.count - p.index - 1
            if (left > 0) StepLine("待处理", MaterialTheme.colorScheme.onSurfaceVariant, "还有 $left 项")
        }
    }
}

@Composable
private fun StepLine(state: String, colour: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.widthIn(min = 72.dp)) { StatusBadge(state, colour) }
        Text(label, fontSize = 14.sp)
    }
}

// ── 小组件：卡片、数据块、进度条、徽标（见 docs/design.md） ──

@Composable
private fun Tile(content: @Composable ColumnScope.() -> Unit) {
    BmoeCard(content = content)
}

@Composable
private fun Well(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.background(tokens.surface2, Corner).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        content = content,
    )
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    Tile(content)
}

@Composable
private fun Folded(title: String, hint: String = "", content: @Composable ColumnScope.() -> Unit) {
    var open by remember { mutableStateOf(false) }
    Tile {
        Row(Modifier.fillMaxWidth().clickable { open = !open }, verticalAlignment = Alignment.CenterVertically) {
            Text(if (open) "v" else ">", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(10.dp))
            Text(title, fontSize = 16.sp)
        }
        if (hint.isNotEmpty()) Text(hint, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (open) content()
    }
}

@Composable
private fun Figure(value: String, unit: String) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(value, fontSize = 26.sp, lineHeight = 28.sp)
        Text(unit, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Meter(fraction: Float, colour: Color, mark: Float? = null) {
    Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).background(colour))
        mark?.let { m ->
            Row(Modifier.fillMaxSize()) {
                if (m > 0f) Spacer(Modifier.fillMaxHeight().weight(m.coerceIn(0.001f, 1f)))
                Box(Modifier.fillMaxHeight().width(2.dp).background(MaterialTheme.colorScheme.onSurface))
                if (m < 1f) Spacer(Modifier.fillMaxHeight().weight((1f - m).coerceIn(0.001f, 1f)))
            }
        }
    }
}

@Composable
private fun StatusBadge(text: String, colour: Color) {
    Surface(color = tintFor(colour), shape = MaterialTheme.shapes.extraSmall) {
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = colour, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
    }
}

/** measured | derived | policy | operator | unprobed, as a small label: where a value came from. */
@Composable
fun SourceBadge(source: String) {
    val color = when (source) {
        "measured" -> MaterialTheme.colorScheme.primary
        "operator" -> MaterialTheme.colorScheme.tertiary
        "unprobed" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(color = color.copy(alpha = 0.12f), shape = MaterialTheme.shapes.extraSmall) {
        Text(source, fontSize = 10.sp, color = color, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}


/** A badge's colour, from a device's role alone; its words come from the server. */
@Composable
private fun deviceColour(d: PlanDevice): Color = when (d.status) {
    "used" -> okColor()
    "refused", "unusable" -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun fmt(v: Double) = String.format(Locale.US, "%.1f", v)

/** Binary units, as the web UI shows them, so a figure reads the same on both. */
private fun bytesLabel(bytes: Long): String {
    val units = listOf("B", "KiB", "MiB", "GiB", "TiB")
    var v = bytes.toDouble()
    var u = 0
    while (v >= 1024 && u < units.size - 1) {
        v /= 1024
        u++
    }
    return String.format(Locale.US, if (u == 0 || v >= 100) "%.0f %s" else "%.1f %s", v, units[u])
}
