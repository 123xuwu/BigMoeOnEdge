package io.bigmoeonedge.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.bigmoeonedge.app.data.AppPrefs
import io.bigmoeonedge.app.engine.ConfigState
import io.bigmoeonedge.app.engine.ContextWarning
import io.bigmoeonedge.app.engine.contextWarning
import io.bigmoeonedge.app.engine.text
import io.bigmoeonedge.app.engine.Engine
import io.bigmoeonedge.app.engine.ParamSpec
import io.bigmoeonedge.app.engine.LanAddress
import io.bigmoeonedge.app.engine.PlanDecision
import io.bigmoeonedge.app.engine.ValueSource
import io.bigmoeonedge.app.engine.differsFromPlan
import io.bigmoeonedge.app.engine.paramValueFromText
import io.bigmoeonedge.app.engine.paramValueText
import io.bigmoeonedge.app.engine.setByYou
import io.bigmoeonedge.app.engine.resettableKeys
import io.bigmoeonedge.app.engine.settingsCards
import io.bigmoeonedge.app.engine.valueSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 本应用自有分组的展开状态键：绝不与参数表的分组同名，因为那些键属于引擎。 */
private const val APP_SECTION = " app"
private const val SHARE_SECTION = " share"
private const val LOOK_SECTION = " appearance"
private const val MINE_SECTION = " mine"

/**
 * 设置页完全由引擎的参数表（`GET /api/params`）渲染，不依赖其他任何来源：
 * 按参数表的分组顺序，每组一张卡片，列出参数表为其提供了表单控件的行（见 docs/screens.md
 * 的“Settings”一节）。分组、行、类型、取值范围与帮助文字全都来自
 * 引擎，因此在那里新增的参数无需改动本应用就会出现在这里。各项取值存于服务端的
 * 各层配置中；在此设置的值归用户所有，方案不会覆盖它。[target] 是某个链接指向的参数
 * （出现在方案页、上下文提示中）：页面会滚动到该行并调用 [onLanded]。
 * 本应用自有的少数设置位于页面末尾；开启指标开关后，从本页一步即可打开 [onOpenMetrics]。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(modifier: Modifier = Modifier, target: String = "", onLanded: () -> Unit = {}, onOpenMetrics: () -> Unit = {}) {
    val context = LocalContext.current
    val engine by Engine.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    // 每张卡片按分组记录展开状态：所有卡片初始都折叠，页面因而先呈现一份简短的
    // 可设置项清单，用户只需展开自己要找的那一张。
    var openCards by remember { mutableStateOf(mapOf<String, Boolean>()) }
    var csv by remember { mutableStateOf(AppPrefs.metricsCsv(context)) }
    var landedOn by remember { mutableStateOf("") }
    // 链接落点：若该值由用户设置，则落在“由你设置”卡片，否则落在该行所属的分组。
    var landedMine by remember { mutableStateOf(false) }
    // 本页内部的链接（例如“在网络上提供服务”下方的上下文提示行）也按同样方式定位。
    var jump by remember { mutableStateOf("") }
    val landing = target.ifEmpty { jump }
    var confirmReset by remember { mutableStateOf(false) }

    fun set(key: String, value: Any?) = scope.launch {
        Engine.setConfig(mapOf(key to value)).onFailure { snackbar.showSnackbar(it.message ?: "未保存") }
    }
    fun reset(keys: List<String>) = scope.launch {
        Engine.setConfig(emptyMap(), keys).onFailure { snackbar.showSnackbar(it.message ?: "未重置") }
    }

    val params = engine.params
    val cfg = engine.config
    // 路径参数只能在启动时设定，无法通过 API 修改：因此只显示只读分组里的那一项（在“模型”页选择）。
    val settable = remember(params) { params?.filter { it.type != "path" || it.groupReadOnly } }

    // 指向某项设置的链接会定位到它：展开其所属卡片（用户自己设置的值则展开“由你设置”，
    // 其余情况展开其分组，即使参数表未提供表单也会显示该行）；该行本身会滚动
    // 到可见区域。每次链接只执行一次，因此随后编辑
    // 其他字段时不会跳回原位置。
    LaunchedEffect(landing, settable, cfg != null) {
        if (landing.isEmpty() || settable == null || cfg == null) return@LaunchedEffect
        val spec = settable.firstOrNull { it.key == landing }
        onLanded()
        jump = ""
        if (spec == null) return@LaunchedEffect
        landedMine = !spec.groupReadOnly && spec.key in cfg.userKeys
        openCards = openCards + ((if (landedMine) MINE_SECTION else spec.group) to true)
        landedOn = spec.key
    }

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("设置") }, navigationIcon = { Box(Modifier.padding(start = 16.dp)) { BrandMark(wordmark = false) } }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 放在最前，因为它是唯一影响到本机之外的一项设置；卡片折叠时标题即显示其状态。
            val ctx = contextWarning(engine.info?.model.takeIf { engine.serverUp && engine.info?.ready == true }, params)
            ShareSection(openCards[SHARE_SECTION] == true, { openCards = openCards + (SHARE_SECTION to it) }, ctx) { jump = it }
            val lookOpen = openCards[LOOK_SECTION] == true
            FoldCard("外观", "", lookOpen, onToggle = { openCards = openCards + (LOOK_SECTION to !lookOpen) }) {
                Appearance(csv, onCsv = { csv = it })
            }
            if (cfg != null && engine.ready && (cfg.reloadRequired || cfg.reloadKeys.isNotEmpty())) {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("部分改动仅在重新加载模型后生效。", fontSize = 13.sp, modifier = Modifier.weight(1f))
                        TextButton(
                            shape = Corner, enabled = cfg.valid,
                            onClick = { scope.launch { Engine.load(null).onFailure { snackbar.showSnackbar(it.message ?: "重新加载失败") } } },
                        ) { Text("重新加载") }
                    }
                }
            }
            if (cfg != null && !cfg.valid) {
                Text("该配置校验未通过：${cfg.error}。在修复之前将拒绝加载模型。", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }

            // 引擎重启期间（例如改动网络共享、更换密钥），各行保持原位，因此
            // 页面长度与滚动位置都不变；上方的一行文字说明改动为何处于等待状态。
            if (!engine.serverUp) {
                Text(
                    if (params != null) "引擎正在重启；恢复后改动即会生效。" else "引擎未运行。",
                    color = if (params != null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                )
            }
            when {
                settable == null || cfg == null -> if (engine.serverUp) Text("正在等待引擎…", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> {
                    val decisions = remember(cfg.plan) { cfg.plan.orEmpty().associateBy { it.knob } }
                    val mine = remember(settable, cfg) { setByYou(settable, cfg) }
                    val cards = remember(settable, mine, landedOn, landedMine) {
                        val pinned = if (landedMine) emptySet() else setOfNotNull(landedOn.ifEmpty { null })
                        settingsCards(settable, pinned, mine.map { it.key }.toSet())
                    }
                    val resettable = remember(settable, cfg) { resettableKeys(cfg.userKeys, cfg.launchKeys, settable) }

                    if (resettable.isNotEmpty()) {
                        TextButton(shape = Corner, onClick = { confirmReset = true }, contentPadding = PaddingValues(horizontal = 0.dp)) {
                            Text("重置我的全部设置（${resettable.size}）", fontSize = 13.sp)
                        }
                    }
                    if (confirmReset) {
                        AlertDialog(
                            onDismissRequest = { confirmReset = false },
                            title = { Text("重置你的全部设置？") },
                            text = {
                                Text(
                                    "你的 ${resettable.size} 项设置将恢复为引擎的默认值；若方案为其指定了值，" +
                                        "则恢复为方案中的值。引擎启动参数与当前模型保持不变。",
                                    fontSize = 13.sp,
                                )
                            },
                            confirmButton = { TextButton(shape = Corner, onClick = { confirmReset = false; reset(resettable) }) { Text("重置") } },
                            dismissButton = { TextButton(shape = Corner, onClick = { confirmReset = false }) { Text("保留") } },
                        )
                    }

                    // 用户设置过的每一个值，无论是否在表单上，都只集中在这一张卡片中：
                    // 设置过的内容不会被藏起来，每一项都有回退入口，重置后归还给其所属分组。
                    if (mine.isNotEmpty()) {
                        val open = openCards[MINE_SECTION] == true
                        FoldCard("由你设置 · ${mine.size}", "", open, onToggle = { openCards = openCards + (MINE_SECTION to !open) }) {
                            mine.forEach { spec ->
                                ParamRow(
                                    spec, cfg, decisions[spec.key], readOnly = false, landing = landedMine && spec.key == landedOn,
                                    onSet = { set(spec.key, it) }, onReset = { reset(listOf(spec.key)) },
                                )
                            }
                        }
                    }
                    cards.forEach { c ->
                        val open = openCards[c.group] == true
                        FoldCard(c.label, c.help, open, onToggle = { openCards = openCards + (c.group to !open) }) {
                            c.params.forEach { spec ->
                                ParamRow(
                                    spec, cfg, decisions[spec.key], readOnly = c.readOnly, landing = !landedMine && spec.key == landedOn,
                                    onSet = { set(spec.key, it) }, onReset = { reset(listOf(spec.key)) },
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = tokens.line)
            val appOpen = openCards[APP_SECTION] == true
            FoldCard("本应用", "这台手机如何运行引擎。", appOpen, onToggle = { openCards = openCards + (APP_SECTION to !appOpen) }) {
                AppSettings(engine.serverUp)
            }
            // 指标是给开启记录的人用的工具，并非独立页面。
            if (csv) {
                BmoeCard(Modifier.clickable(onClick = onOpenMetrics), padding = 12.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("指标", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                        Text(">", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Hint("当前生成的实时数据 and the recorded CSV files.")
                }
            }
            engine.info?.let { Text("引擎 ${it.version}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** 可折叠的卡片：标题、其旁的 [status] and its help line always, its content once opened. */
@Composable
private fun FoldCard(
    title: String,
    help: String,
    open: Boolean,
    onToggle: () -> Unit,
    status: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    BmoeCard(padding = 12.dp) {
        Column(Modifier.fillMaxWidth().clickable(onClick = onToggle)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                status?.let { Spacer(Modifier.width(8.dp)); it() }
                Spacer(Modifier.width(8.dp))
                Text(if (open) "v" else ">", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (help.isNotEmpty()) Hint(help)
        }
        if (open) content()
    }
}

/**
 * 在网络上提供服务：开关本身；开启后显示其他设备上的客户端所需的信息（
 * 地址、密钥、客户端请求的上下文长度），以及共享的代价（流量为明文传输）。
 * 卡片折叠时标题即显示状态，因此手机是否可被访问从不隐藏。
 */
@Composable
private fun ShareSection(open: Boolean, onOpen: (Boolean) -> Unit, ctx: ContextWarning?, onRaise: (String) -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var share by remember { mutableStateOf(AppPrefs.shareOnNetwork(context)) }
    var key by remember { mutableStateOf(AppPrefs.apiKey(context)) }
    var showKey by remember { mutableStateOf(false) }
    // 手机在本网络中的地址，随网络变化实时跟随（仅在共享开启时）：
    // 这是客户端要填写的地址，而非需要自行推导的占位符。
    var lan by remember { mutableStateOf(emptyList<String>()) }
    if (share) {
        DisposableEffect(Unit) {
            lan = LanAddress.current(context)
            onDispose(LanAddress.watch(context) { lan = it })
        }
    }
    val first = lan.firstOrNull()
    FoldCard(
        "在网络上提供服务", "", open, onToggle = { onOpen(!open) },
        status = {
            val (text, colour) = when {
                !share -> "已关闭" to MaterialTheme.colorScheme.onSurfaceVariant
                first == null -> "已开启 · 未连接网络" to warnColor()
                else -> "已开启 · ${LanAddress.address(first)}" to MaterialTheme.colorScheme.onSurfaceVariant
            }
            Text(text, fontSize = 12.sp, color = colour, maxLines = 1)
        },
    ) {
        SwitchRow(
            "在网络上提供服务",
            "本网络中的其他设备可将这台手机的引擎 as an OpenAI-compatible server. Changing this restarts the engine.",
            share,
        ) { on ->
            share = on
            AppPrefs.setShareOnNetwork(context, on)
            Engine.restart(context)
        }
        if (!share) return@FoldCard
        Text("地址", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (lan.isEmpty()) {
            Text(
                "当前未连接 Wi-Fi 或有线网络： no other device can reach the engine until the phone joins one.",
                fontSize = 12.sp, color = warnColor(),
            )
        }
        lan.forEach { ip ->
            val address = LanAddress.address(ip)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(address, fontFamily = CodeFont, fontSize = 15.sp, modifier = Modifier.weight(1f))
                TextButton(shape = Corner, onClick = { clipboard.setText(AnnotatedString(address)) }) { Text("复制") }
            }
        }
        Text("API 密钥", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            // 默认掩码显示：屏幕可能被旁观或录制，而这把密钥正是放行设备的凭据。
            Text(if (showKey) key else "•".repeat(key.length), fontFamily = CodeFont, fontSize = 13.sp, maxLines = 1, modifier = Modifier.weight(1f))
            TextButton(shape = Corner, onClick = { showKey = !showKey }) { Text(if (showKey) "隐藏" else "显示") }
            TextButton(shape = Corner, onClick = { clipboard.setText(AnnotatedString(key)) }) { Text("复制") }
            TextButton(shape = Corner, onClick = { key = AppPrefs.regenerateApiKey(context); Engine.restart(context) }) { Text("新密钥") }
        }
        first?.let { Hint("客户端中填写：基础 URL 为 ${LanAddress.url(it)}, and the key above.") }
        // 客户端在何处启用及其所需内容：无论状态行上的提示是否已被关闭，此处都会说明。
        if (ctx?.short == true) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(ctx.text(), fontSize = 12.sp, color = warnColor(), modifier = Modifier.weight(1f))
                TextButton(shape = Corner, onClick = { onRaise(ctx.key) }) { Text("提高 ${ctx.label}") }
            }
        }
        Text("提问与回答在网络中为明文传输: use it on a network you trust.", fontSize = 12.sp, color = warnColor())
    }
}

/** 应用的外观与呈现内容：立即生效的主题, applied at once, and whether metrics are recorded. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Appearance(csv: Boolean, onCsv: (Boolean) -> Unit) {
    val context = LocalContext.current
    val theme by AppPrefs.theme(context).collectAsStateWithLifecycle()
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("主题", fontSize = 15.sp)
        Hint("跟随系统，或始终使用浅色、深色。")
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            AppPrefs.THEMES.forEachIndexed { i, (value, label) ->
                SegmentedButton(
                    selected = theme == value,
                    onClick = { AppPrefs.setTheme(context, value) },
                    shape = SegmentedButtonDefaults.itemShape(i, AppPrefs.THEMES.size),
                ) { Text(label) }
            }
        }
    }
    SwitchRow(
        "指标",
        "为每个已加载的模型逐 token 记录 CSV and show the measurements here.",
        csv,
    ) { on -> onCsv(on); AppPrefs.setMetricsCsv(context, on) }
}

/** 引擎进程：除网络共享与外观之外，本应用自有的设置 besides sharing and appearance. */
@Composable
private fun AppSettings(serverUp: Boolean) {
    val context = LocalContext.current
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(shape = Corner, onClick = { Engine.stop(context) }, enabled = serverUp, modifier = Modifier.weight(1f)) { Text("停止引擎") }
        OutlinedButton(shape = Corner, onClick = { Engine.start(context) }, enabled = !serverUp, modifier = Modifier.weight(1f)) { Text("启动引擎") }
    }
}

/**
 * 单个参数：其标签、按其类型所需的控件、一枚标明取值来源的标签，
 * 以及回退途径（恢复为方案中的值或默认值）。被链接 [landing] 定位的行会滚动到可见区域、
 * 加上边框，且其输入框获得焦点。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ParamRow(
    spec: ParamSpec,
    cfg: ConfigState,
    decision: PlanDecision?,
    readOnly: Boolean,
    landing: Boolean,
    onSet: (Any?) -> Unit,
    onReset: () -> Unit,
) {
    val value = cfg.value(spec.key)
    val mine = spec.key in cfg.userKeys
    val source = valueSource(mine, decision)
    val planOffer = mine && differsFromPlan(value, decision)
    val bring = remember { BringIntoViewRequester() }
    val focus = remember { FocusRequester() }
    LaunchedEffect(landing) {
        if (!landing) return@LaunchedEffect
        delay(100) // 该行所在的卡片与折叠区 were opened in the same frame: let them lay out
        bring.bringIntoView()
        if (!readOnly && spec.type != "bool" && spec.type != "choice") runCatching { focus.requestFocus() }
    }
    var mod = Modifier.fillMaxWidth().bringIntoViewRequester(bring)
    if (landing) mod = mod.border(1.dp, tokens.accent, Corner).padding(6.dp)
    Column(mod, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        when {
            readOnly -> {
                Text(spec.label, fontSize = 15.sp)
                Text(paramValueText(value).substringAfterLast('/').ifEmpty { "无" }, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            spec.type == "bool" -> SwitchRow(spec.label, null, value == true) { onSet(it) }
            spec.type == "choice" -> {
                val idx = spec.choices.indexOfFirst { it.first == value?.toString() }.coerceAtLeast(0)
                LabeledDropdown(spec.label, spec.choices.map { it.second }, idx) { i -> onSet(spec.choices[i].first) }
            }
            else -> NumberOrTextField(spec, value, focus, onSet)
        }
        val (lead, rest) = spec.helpParts()
        var more by remember(spec.key) { mutableStateOf(false) }
        if (lead.isNotEmpty()) Hint(lead)
        val extra = listOfNotNull(
            rest.ifEmpty { null },
            spec.special.takeIf { it.isNotEmpty() }?.joinToString("; ") { "${it.first} = ${it.second}" },
        )
        if (extra.isNotEmpty()) {
            if (more) extra.forEach { Hint(it) }
            Text(
                if (more) "收起" else "更多",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { more = !more }.padding(vertical = 2.dp),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (!readOnly) Chip(source.chip, if (source == ValueSource.DEFAULT) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
            if (spec.lossy) Chip("会改变回复", warnColor())
            if (spec.scope == "request") Chip("每条消息", MaterialTheme.colorScheme.onSurfaceVariant)
            if (spec.key in cfg.reloadKeys) Chip("需重新加载", warnColor())
            Spacer(Modifier.weight(1f))
            if (!readOnly && mine) {
                val label = when {
                    planOffer -> "采用方案值" + (decision?.valueText?.ifEmpty { decision.value }?.let { ": $it" } ?: "")
                    source == ValueSource.YOU && decision != null && decision.group.ifEmpty { "set" } == "set" -> "交给方案设定"
                    else -> "重置"
                }
                TextButton(shape = Corner, onClick = onReset, contentPadding = PaddingValues(horizontal = 8.dp)) { Text(label, fontSize = 12.sp) }
            }
        }
    }
}

/** 数字（若该行接受，也可填 `auto`）或自由文本；离开输入框时提交。 */
@Composable
private fun NumberOrTextField(spec: ParamSpec, value: Any?, focus: FocusRequester, onSet: (Any?) -> Unit) {
    var text by remember(spec.key, value) { mutableStateOf(paramValueText(value)) }
    var error by remember(spec.key) { mutableStateOf<String?>(null) }
    fun commit() {
        if (text == paramValueText(value)) return
        val v = paramValueFromText(spec, text)
        if (v == null) {
            error = "不是有效的 ${spec.type}" + if (spec.acceptsAuto) "（或 auto）" else ""
            return
        }
        val n = (v as? Number)?.toDouble()
        if (n != null && ((spec.min != null && n < spec.min) || (spec.max != null && n > spec.max))) {
            // 取值范围仅在被拒绝时显示；其余情况下输入框只提示常见取值。
            error = if (spec.max != null) "需在 ${paramValueText(spec.min)} 与 ${paramValueText(spec.max)} 之间"
            else "至少为 ${paramValueText(spec.min)}"
            return
        }
        error = null
        onSet(v)
    }
    // 输入框下方：若有问题则显示问题，否则显示取值含义（= 关闭）与常见取值。
    val hint = error ?: listOfNotNull(spec.specialLabel(value)?.let { "= $it" }, spec.typicalText()).joinToString(" · ").ifEmpty { null }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it; error = null },
        label = { Text(spec.label) },
        suffix = spec.unit?.let { { Text(it, fontSize = 12.sp) } },
        isError = error != null,
        supportingText = hint?.let { { Text(it) } },
        singleLine = spec.type != "text",
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { commit() }),
        modifier = Modifier.fillMaxWidth().focusRequester(focus).onFocusChanged { if (!it.isFocused) commit() },
    )
}
