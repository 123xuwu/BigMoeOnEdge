package io.bigmoeonedge.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.bigmoeonedge.app.chat.Chat
import io.bigmoeonedge.app.chat.ConversationSummary
import io.bigmoeonedge.app.chat.ItemReducer
import io.bigmoeonedge.app.data.Breakdown
import io.bigmoeonedge.app.data.Telemetry
import io.bigmoeonedge.app.data.breakdown
import io.bigmoeonedge.app.engine.ApiException
import io.bigmoeonedge.app.engine.Decide
import io.bigmoeonedge.app.engine.Decisions
import io.bigmoeonedge.app.engine.Engine
import io.bigmoeonedge.app.engine.contextWarning
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 聊天中错误卡片可跳转的去处：其他页面。 */
data class ChatNav(
    val openModels: () -> Unit,
    val openSetting: (String) -> Unit,
)

/**
 * 聊天页：会话列表（来自服务端，见 docs/chat.md；手机上即普通聊天）位于抽屉中；
 * 打开的会话以消息形式显示在中间；输入框位于底部拇指可及之处。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(modifier: Modifier = Modifier, nav: ChatNav) {
    val chat by Chat.state.collectAsStateWithLifecycle()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf<ConversationSummary?>(null) }
    var renaming by remember { mutableStateOf<ConversationSummary?>(null) }
    LaunchedEffect(Unit) { Chat.refreshConversations() }

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = tokens.surface) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) { BrandMark() }
                    FilledTonalButton(onClick = { Chat.newConversation(); scope.launch { drawer.close() } }, shape = Corner) {
                        Icon(Icons.Filled.Add, contentDescription = null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("新建对话")
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = tokens.line)
                SectionTitle("会话", Modifier.padding(horizontal = 28.dp, vertical = 4.dp))
                val stamp = remember { SimpleDateFormat("d MMM HH:mm", Locale.getDefault()) }
                if (chat.conversations.isEmpty()) {
                    Text(
                        "还没有会话。发送第一条消息即会创建，并由本机引擎保存。",
                        style = MaterialTheme.typography.bodySmall, color = tokens.muted,
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
                    )
                }
                LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                    items(chat.conversations, key = { it.id }) { c ->
                        var menu by remember { mutableStateOf(false) }
                        NavigationDrawerItem(
                            selected = c.id == chat.open?.id,
                            onClick = { Chat.open(c.id); scope.launch { drawer.close() } },
                            label = {
                                Column {
                                    Text(c.title.ifEmpty { "新建对话" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        val meta = c.updated.takeIf { it > 0 }?.let { stamp.format(Date(it)) }.orEmpty()
                                        Text(meta, fontSize = 11.sp, color = tokens.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                        ConversationStateChip(c.state)
                                    }
                                }
                            },
                            badge = {
                                Box {
                                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "更多") }
                                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                        DropdownMenuItem(text = { Text("重命名") }, leadingIcon = { Icon(Icons.Filled.Edit, null) }, onClick = { menu = false; renaming = c })
                                        DropdownMenuItem(text = { Text("删除") }, leadingIcon = { Icon(Icons.Filled.Delete, null) }, onClick = { menu = false; confirmDelete = c })
                                    }
                                }
                            },
                        )
                    }
                }
            }
        },
    ) {
        Column(modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Text(chat.open?.title?.ifEmpty { null } ?: "新建对话", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                },
                navigationIcon = {
                    IconButton(onClick = { scope.launch { drawer.open() } }) { Icon(Icons.Filled.Menu, contentDescription = "会话") }
                },
                actions = {
                    if (chat.open != null) {
                        IconButton(onClick = { Chat.newConversation() }) { Icon(Icons.Filled.Add, contentDescription = "新建对话") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = tokens.bg),
            )
            HorizontalDivider(color = tokens.line)
            Transcript(Modifier.weight(1f), nav)
            Composer()
        }
    }

    confirmDelete?.let { c ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("删除这个会话？") },
            text = { Text("\"${c.title.ifEmpty { "新建对话" }}\" 及其中的全部内容都会从引擎中移除。") },
            confirmButton = { TextButton(onClick = { Chat.delete(c.id); confirmDelete = null }) { Text("删除", color = tokens.err) } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("取消") } },
        )
    }
    renaming?.let { c ->
        var title by remember(c.id) { mutableStateOf(c.title) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("重命名") },
            text = { OutlinedTextField(value = title, onValueChange = { title = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = { Chat.rename(c.id, title.trim()); renaming = null }, enabled = title.isNotBlank()) { Text("保存") } },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("取消") } },
        )
    }
}

@Composable
fun ConversationStateChip(state: String) {
    when (state) {
        ConversationSummary.STATE_RUNNING -> Chip("运行中", tokens.accentInk)
        ConversationSummary.STATE_ERROR -> Chip("出错", tokens.err)
    }
}

@Composable
private fun Transcript(modifier: Modifier, nav: ChatNav) {
    val chat by Chat.state.collectAsStateWithLifecycle()
    val decision by Decisions.state.collectAsStateWithLifecycle()
    // 只订阅上下文建议，而非每个 token 事件：消息列表是界面上开销最大的部分。
    val ctxAdvice by remember { Engine.state.map { contextWarning(it.info?.model, it.params) }.distinctUntilChanged() }.collectAsStateWithLifecycle(null)
    val listState = rememberLazyListState()
    val shown = chat.items
    val againOn = remember(shown) { ItemReducer.answerAgainOn(shown) }
    val working = chat.running && shown.lastOrNull()?.streaming != true
    val total = shown.size + (if (working || chat.sending) 1 else 0) + (if (decision != null) 1 else 0) + (if (chat.error != null) 1 else 0)
    val lastText = chat.items.lastOrNull()?.let { it.text.length + it.reasoning.length } ?: 0

    // 仅在用户停在底部时才跟随最新消息：向上回看长回答时，
    // 不应与逐 token 的滚动指令相互抢夺。
    var followTail by remember { mutableStateOf(true) }
    val atBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            last == null || (last.index == info.totalItemsCount - 1 && last.offset + last.size <= info.viewportEndOffset)
        }
    }
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { if (it is DragInteraction.Start) followTail = false }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling -> if (!scrolling) followTail = atBottom }
    }
    LaunchedEffect(total, lastText, followTail) {
        if (followTail && total > 0) runCatching { listState.scrollToItem(total - 1, Int.MAX_VALUE) }
    }

    Box(modifier.fillMaxWidth()) {
        if (chat.opening) {
            LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
        if (shown.isEmpty() && decision == null && chat.error == null && !chat.sending && !chat.opening) {
            EmptyChat(chat.unsupported)
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(shown) { item ->
                ItemView(item, answerAgain = item.id == againOn, running = chat.running || chat.sending)
            }
            if (working || chat.sending) {
                item(key = "working") {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = tokens.accent)
                        Text(if (chat.sending) "正在发送…" else "正在处理…", style = MaterialTheme.typography.bodySmall, color = tokens.muted)
                    }
                }
            }
            decision?.let { d -> item(key = "decision") { DecisionCard(d, nav.openModels) } }
            chat.error?.let { err ->
                item(key = "error") {
                    ErrorCard(err, chat.errorCode, ctxAdvice?.let { it.nCtx to (it.key to it.label) }, nav)
                }
            }
        }
        if (!followTail && !atBottom && total > 0) {
            val scope = rememberCoroutineScope()
            FilledTonalButton(
                shape = Corner,
                onClick = { scope.launch { listState.animateScrollToItem(total - 1, Int.MAX_VALUE) } },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
            ) { Text("跳到最新") }
        }
    }
}

@Composable
private fun EmptyChat(unsupported: Boolean) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        if (unsupported) {
            Text("该引擎不保存会话。", style = MaterialTheme.typography.titleMedium)
            Text(
                "本应用启动的服务端不支持会话功能。请将应用更新到支持该功能的版本。",
                style = MaterialTheme.typography.bodyMedium, color = tokens.muted,
            )
            return@Column
        }
        Text("随便问点什么。", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text(
            "模型运行在这台手机上，你写的内容不会离开本机。请先在“模型”页加载模型，" +
                "然后在下方输入。",
            style = MaterialTheme.typography.bodyMedium, color = tokens.muted,
        )
    }
}

/** 发送或运行失败的原因，由其错误码决定；修复入口就在旁边。 */
@Composable
private fun ErrorCard(err: String, code: String, ctx: Pair<Int, Pair<String, String>>?, nav: ChatNav) {
    BmoeCard(colour = tokens.errTint, padding = 12.dp) {
        Text(err, color = tokens.text, style = MaterialTheme.typography.bodyMedium)
        val contextFull = code == ApiException.CODE_CONTEXT_FULL
        if (contextFull) {
            Text(
                "会话已占满模型的上下文" + (ctx?.let { "（共 ${it.first} 个 token）" } ?: "") + "。",
                style = MaterialTheme.typography.bodySmall, color = tokens.muted,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(1f))
            when {
                contextFull -> {
                    if (ctx != null) TextButton(onClick = { nav.openSetting(ctx.second.first) }) { Text("提高 ${ctx.second.second}") }
                    TextButton(onClick = { Chat.newConversation() }) { Text("新建对话") }
                }
                code == ApiException.CODE_NO_MODEL -> TextButton(onClick = nav.openModels) { Text("模型") }
            }
            TextButton(onClick = { Chat.clearError() }) { Text("忽略") }
        }
    }
}

/** 选择型回答：列出每个选项及模型给出的概率，被选中的加粗。 */
@Composable
private fun DecisionCard(d: io.bigmoeonedge.app.engine.DecisionUi, onOpenModels: () -> Unit) {
    BmoeCard(padding = 12.dp) {
        Text("请选择：" + d.question, style = MaterialTheme.typography.titleSmall)
        when {
            d.running -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Text("正在读取选项…", style = MaterialTheme.typography.bodySmall, color = tokens.muted)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { Decisions.stop() }) { Text("停止") }
            }
            d.error != null -> {
                Text(d.error, color = tokens.err, style = MaterialTheme.typography.bodySmall)
                if (d.errorCode == ApiException.CODE_NO_MODEL) TextButton(onClick = onOpenModels) { Text("模型") }
            }
            else -> d.scores.forEachIndexed { i, c ->
                val picked = i == d.best
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "${c.label}) ${c.text}", style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (picked) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                    )
                    LinearProgressIndicator(
                        progress = { c.prob.toFloat().coerceIn(0f, 1f) }, color = if (picked) tokens.accent else tokens.muted,
                        trackColor = tokens.surface2, modifier = Modifier.width(72.dp).height(6.dp),
                    )
                    Text(String.format(Locale.US, "%.0f%%", 100.0 * c.prob), style = FigureStyle, modifier = Modifier.width(40.dp))
                }
            }
        }
        if (d.metrics.isNotEmpty()) Text(d.metrics, style = CodeStyle.copy(fontSize = 11.sp), color = tokens.muted)
        if (!d.running) Row { Spacer(Modifier.weight(1f)); TextButton(onClick = { Decisions.dismiss() }) { Text("忽略") } }
    }
}

/** 输入区：引擎工作时显示实时状态行，随后是输入框与发送按钮；回答生成过程中显示停止按钮。 */
@Composable
private fun Composer() {
    val chat by Chat.state.collectAsStateWithLifecycle()
    val decision by Decisions.state.collectAsStateWithLifecycle()
    val engine by Engine.state.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current
    var prompt by rememberSaveable { mutableStateOf("") }
    var choose by rememberSaveable { mutableStateOf(false) }
    var optionsText by rememberSaveable { mutableStateOf("") }

    Surface(color = tokens.surface, shadowElevation = 2.dp) {
        Column(Modifier.fillMaxWidth().imePadding()) {
            HorizontalDivider(color = tokens.line)
            QueueNotice()
            LiveLine()
            if (choose) {
                OutlinedTextField(
                    value = optionsText, onValueChange = { optionsText = it },
                    label = { Text("选项，每行一个") },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 8.dp), minLines = 2, maxLines = 6,
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                var more by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { more = true }) { Icon(Icons.Filled.Add, contentDescription = "更多") }
                    DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                        DropdownMenuItem(
                            text = { Text(if (choose) "返回普通提问" else "从选项中挑选") },
                            leadingIcon = { Icon(Icons.Filled.Build, null) },
                            onClick = { choose = !choose; more = false },
                        )
                    }
                }
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    placeholder = { Text(if (choose) "问题" else "消息") },
                    modifier = Modifier.weight(1f),
                    maxLines = 6,
                    shape = Corner,
                    trailingIcon = if (prompt.isNotEmpty()) ({
                        IconButton(onClick = { prompt = "" }) { Icon(Icons.Filled.Clear, contentDescription = "清除") }
                    }) else null,
                )
                if (chat.running) {
                    FilledTonalButton(onClick = { Chat.stop() }, shape = Corner, contentPadding = PaddingValues(horizontal = 16.dp)) { Text("停止") }
                } else {
                    val options = if (choose) Decide.parseOptions(optionsText) else emptyList()
                    FilledIconButton(
                        shape = Corner,
                        onClick = {
                            // 取消焦点以收起键盘：回答会流入键盘原本遮挡的区域。
                            focus.clearFocus()
                            if (choose) Decisions.choose(prompt, options) else Chat.send(prompt)
                            prompt = ""
                        },
                        enabled = prompt.isNotBlank() && engine.serverUp && !chat.sending && decision?.running != true &&
                            (!choose || options.size >= 2),
                    ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = if (choose) "选择" else "发送") }
                }
            }
        }
    }
}

/**
 * 服务端一次只生成一个回答（turn_queue.h）：别的会话或后台任务占着时，这一条发出去就会排队，
 * 而不是开始。排在队里的请求自己看不到这件事——它占着一个 worker 线程，等轮到自己才有回应——
 * 所以只有这里能提前告诉用户「在等」。
 *
 * 两种等待要分开说：自己这条还没轮到（`Chat.queuedBehind`，发送前读到的位置），和模型正在给
 * 别处写答案（`waiting_turns`，每次有人进出队都会推一个 state 事件）。前者要说明「你前面还有
 * 几个」，后者只需说明「在忙别的」。
 */
@Composable
private fun QueueNotice() {
    val engine by Engine.state.collectAsStateWithLifecycle()
    val chat by Chat.state.collectAsStateWithLifecycle()
    val info = engine.info ?: return
    // Sent, not back yet, and something was already running: this request is the one in line.
    // The count came from the moment of the send, so it stays right while the queue moves, and
    // the first content of this conversation clears it: that is its turn starting.
    val mine = chat.queuedBehind
    val waiting = info.waitingTurns
    // Waiting turns are a reason to say something on their own: the server now publishes the count
    // as a place is taken and as it is given back, so `waiting > 0` can be true for a moment while
    // nothing generates (a waiter left just as the running turn ended). Hiding the notice then
    // would blank the message under the composer mid-change.
    if (!info.generating && mine == 0 && waiting == 0) return
    Text(
        when {
            mine > 0 -> if (mine > 1) "你的请求在排队，前面还有 ${mine - 1} 个" else "你的请求在排队，轮到下一个就回答"
            waiting > 0 -> "模型正在回答，前面还有 $waiting 个请求在排队"
            else -> "模型正在回答别的请求"
        },
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 4.dp),
    )
}

/** 生成速率与耗时分布，一行显示，生成中与生成后都可见；完整卡片见“指标”页。 */
@Composable
private fun LiveLine() {
    val engine by Engine.state.collectAsStateWithLifecycle()
    val t = engine.live
    if (t.step == 0 && t.avgTokensPerSecond <= 0) return
    val b = breakdown(t)
    val loc = Locale.US
    val rate = if (b.isAverage) String.format(loc, "平均 %.1f tok/s", t.avgTokensPerSecond)
    else String.format(loc, "%.1f tok/s", t.tokensPerSecond)
    val parts = mutableListOf(rate)
    if (b.totalMs > 0) {
        parts += String.format(loc, "计算 %.0f%%", 100.0 * b.computeMs / b.totalMs)
        parts += String.format(loc, "闪存等待 %.0f%%", 100.0 * b.flashWaitMs / b.totalMs)
    }
    if (t.cacheHitPct >= 0) parts += String.format(loc, "缓存命中 %.0f%%", t.cacheHitPct)
    engine.cpuTempC?.let { parts += String.format(loc, "%.0f°C", it) }
    if (engine.generating && t.steps > 0) parts += "${t.step}/${t.steps}"

    // The row stays one line where it fits, wraps to three where it does not, and opens everything
    // on a tap: a phone is narrow, and a rate cut off at "计算…" tells nobody anything.
    var open by remember { mutableStateOf(false) }
    Text(
        parts.joinToString(" · "),
        style = FigureStyle, fontSize = 12.sp,
        color = tokens.muted,
        maxLines = 2, overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .padding(horizontal = 16.dp).padding(top = 6.dp)
            .clickable { open = true },
    )

    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(if (b.isAverage) "本轮平均速率" else "当前生成速率") },
            text = {
                Column(
                    Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    SelectionContainer {
                        Text(detailLines(t, b, loc).joinToString("\n"), fontSize = 12.sp, fontFamily = CodeFont)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("关闭") } },
        )
    }
}

/**
 * Every figure one generation reports, grouped and labelled — the same numbers the 指标 page draws
 * as cards, here as text, because that page is a whole screen away from the answer being read.
 * A field the server left unmeasured (-1) is omitted rather than shown as a negative percentage.
 */
private fun detailLines(t: Telemetry, b: Breakdown, loc: Locale): List<String> {
    val out = mutableListOf<String>()
    fun line(label: String, value: String) = out.add(String.format(loc, "%-14s %s", label, value))

    out.add("速率")
    line("解码", String.format(loc, "%.2f tok/s", t.tokensPerSecond))
    if (t.avgTokensPerSecond > 0) line("本轮平均", String.format(loc, "%.2f tok/s", t.avgTokensPerSecond))
    if (t.effectiveTokensPerSecond > 0) line("实际体验", String.format(loc, "%.2f tok/s", t.effectiveTokensPerSecond))
    if (t.prefillTps > 0) line("预填充", String.format(loc, "%.1f tok/s", t.prefillTps))
    if (t.ttftS > 0) line("首 token", String.format(loc, "%.2f s", t.ttftS))

    if (b.totalMs > 0) {
        out.add("")
        out.add("每个 token 的时间")
        line("总耗时", String.format(loc, "%.1f ms", b.totalMs))
        line("计算", String.format(loc, "%.1f ms  %.0f%%", b.computeMs, 100.0 * b.computeMs / b.totalMs))
        line("闪存等待", String.format(loc, "%.1f ms  %.0f%%", b.flashWaitMs, 100.0 * b.flashWaitMs / b.totalMs))
        line("缓存管理", String.format(loc, "%.1f ms  %.0f%%", b.mgmtMs, 100.0 * b.mgmtMs / b.totalMs))
        line("未归因", String.format(loc, "%.1f ms  %.0f%%", b.unattributedMs, 100.0 * b.unattributedMs / b.totalMs))
    }

    if (b.cpuBusyPct >= 0 || b.faultsPerToken >= 0) {
        out.add("")
        out.add("系统")
        if (b.cpuBusyPct >= 0) line("CPU 占用", String.format(loc, "%.0f%%", b.cpuBusyPct))
        if (b.faultsPerToken >= 0) line("缺页/token", String.format(loc, "%.0f 次", b.faultsPerToken))
    }

    if (t.cacheHitPct >= 0 || t.cacheResidentMib >= 0) {
        out.add("")
        out.add("专家缓存")
        if (t.cacheHitPct >= 0) line("命中率", String.format(loc, "%.0f%%", t.cacheHitPct))
        if (t.cacheResidentMib >= 0 && t.cacheBudgetMib > 0) {
            line("常驻", String.format(loc, "%.0f / %.0f MiB", t.cacheResidentMib, t.cacheBudgetMib))
        }
        if (t.readMib >= 0) line("闪存读取", String.format(loc, "%.0f MiB", t.readMib))
    }

    if (t.mtpDrafted > 0) {
        out.add("")
        out.add("投机解码")
        line("起草", "${t.mtpDrafted} 个")
        line("采纳", String.format(loc, "%d 个  %.0f%%", t.mtpAccepted, t.mtpAcceptancePct))
        if (t.mtpTokensPerDecode > 0) line("每次验证", String.format(loc, "%.2f 个 token", t.mtpTokensPerDecode))
        if (t.mtpDraftSPerTok > 0) line("起草耗时", String.format(loc, "%.1f ms/token", 1000.0 * t.mtpDraftSPerTok))
        if (t.loopOverheadSPerTok > 0) line("解码间隙", String.format(loc, "%.1f ms/token", 1000.0 * t.loopOverheadSPerTok))
    }

    if (t.steps > 0) {
        out.add("")
        line("进度", "${t.step} / ${t.steps}")
    }
    return out
}
