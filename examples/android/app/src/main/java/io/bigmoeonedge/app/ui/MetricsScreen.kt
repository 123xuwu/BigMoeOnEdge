package io.bigmoeonedge.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.bigmoeonedge.app.data.Better
import io.bigmoeonedge.app.data.ConfigGlossary
import io.bigmoeonedge.app.data.Csv
import io.bigmoeonedge.app.data.PreambleFacts
import io.bigmoeonedge.app.data.MetricFields
import io.bigmoeonedge.app.data.fmt
import io.bigmoeonedge.app.data.pearson
import io.bigmoeonedge.app.data.runTitle
import io.bigmoeonedge.app.data.shareCsv
import io.bigmoeonedge.app.data.breakdown
import io.bigmoeonedge.app.engine.Engine
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * 读回本应用根据服务端 token 事件写出的逐 token CSV（每个已加载会话一个，见 MetricsRecorder）：查看其中一个、分享一个或把两个文件的同一列画在一起作对比。
 * session, see MetricsRecorder): view one, share one, or plot one column of two against each other.
 *
 * 对比视图才是重点。像“这个设置抑制了缺页风暴”这样的说法，不是靠摘要里的一个
 * 数字就能定论的——它要靠把关闭该特性的一次运行与开启该特性的一次运行中同一列，
 * 画在同一坐标轴上看，才能定论。
 *
 * 本文件只负责视图。CSV 的读取与解析、运行命名、图表算术和分享意图都在
 * [MetricsCsv.kt] —— 这里不直接接触任何文件。
 */
// 当前屏幕在展示什么。picked = 查看单个文件；correlating = 叠加单个文件自身的列；
// comparing = 比较两个文件的同一列；glossary = 字段说明。
private enum class View { LIST, FILE, CORRELATE, COMPARE, GLOSSARY }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetricsScreen(modifier: Modifier = Modifier, onBack: (() -> Unit)? = null) {
    val context = LocalContext.current
    var picked by remember { mutableStateOf<File?>(null) }
    var selection by remember { mutableStateOf<List<File>>(emptyList()) }
    var view by remember { mutableStateOf(View.LIST) }
    var refresh by remember { mutableStateOf(0) }
    val files = remember(refresh) {
        File(context.getExternalFilesDir(null), "metrics")
            .listFiles { f -> f.name.endsWith(".csv") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    // 返回会往列表退一级；列表就是这个屏幕的首页。
    fun back() {
        when (view) {
            View.CORRELATE -> view = View.FILE
            View.FILE, View.COMPARE, View.GLOSSARY -> view = View.LIST
            View.LIST -> onBack?.invoke()
        }
    }
    // 系统返回键走同样的路径，而在列表页则退回到打开本屏幕的地方。
    BackHandler(enabled = view != View.LIST || onBack != null) { back() }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (view) {
                            View.COMPARE -> "比较文件"
                            View.CORRELATE -> "字段相关性"
                            View.GLOSSARY -> "字段含义"
                            View.FILE -> picked?.name ?: "指标"
                            View.LIST -> "指标"
                        },
                        maxLines = 1,
                    )
                },
                navigationIcon = {
                    if (view != View.LIST || onBack != null) {
                        IconButton(onClick = { back() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    } else {
                        Box(Modifier.padding(start = 16.dp)) { BrandMark(wordmark = false) }
                    }
                },
                actions = {
                    if (view == View.FILE) {
                        TextButton(shape = Corner, onClick = { view = View.CORRELATE }) { Text("字段相关性") }
                        picked?.let { f -> TextButton(shape = Corner, onClick = { shareCsv(context, listOf(f)) }) { Text("分享") } }
                    }
                    if (view == View.LIST || view == View.FILE || view == View.COMPARE) {
                        TextButton(shape = Corner, onClick = { view = View.GLOSSARY }) { Text("?") }
                    }
                },
            )
        },
        bottomBar = {
            if (view == View.LIST && selection.isNotEmpty()) {
                BottomAppBar {
                    Spacer(Modifier.width(12.dp))
                    Text("已选择 ${selection.size} 个", modifier = Modifier.weight(1f), fontSize = 13.sp)
                    TextButton(shape = Corner, onClick = { shareCsv(context, selection) }) { Text("分享") }
                    TextButton(shape = Corner,
                        onClick = { view = View.COMPARE },
                        enabled = selection.size == 2,
                    ) { Text("比较") }
                    Spacer(Modifier.width(8.dp))
                }
            }
        },
    ) { padding ->
        val m = Modifier.padding(padding)
        when (view) {
            View.COMPARE -> CompareView(selection[0], selection[1], m)
            View.CORRELATE -> picked?.let { CorrelateView(it, m) }
            View.GLOSSARY -> GlossaryView(m)
            View.FILE -> picked?.let { CsvView(it, m) }
            View.LIST -> FileList(
                files = files,
                selection = selection,
                modifier = m,
                onPick = { picked = it; view = View.FILE },
                onToggle = { f ->
                    selection = when {
                        selection.contains(f) -> selection - f
                        selection.size < 2 -> selection + f
                        else -> listOf(selection[1], f) // 第三次选择会替换掉最旧的那个
                    }
                },
                onDelete = { f -> f.delete(); selection = selection - f; refresh++ },
            )
        }
    }
}

@Composable
private fun FileList(
    files: List<File>,
    selection: List<File>,
    modifier: Modifier,
    onPick: (File) -> Unit,
    onToggle: (File) -> Unit,
    onDelete: (File) -> Unit,
) {
    val stamp = SimpleDateFormat("d MMM HH:mm:ss", Locale.getDefault())
    // 一次运行的标识，而不是它的文件名：模型的名称首字母加上每一轮的 tok/s，
    // 从序言和摘要中读出。每份文件列表只计算一次——文件既小又少。
    val titles = remember(files) { files.associateWith { runTitle(it) } }
    LazyColumn(modifier.fillMaxSize()) {
        item { LiveCard() }
        if (files.isEmpty()) {
            item {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("还没有指标文件。", fontWeight = FontWeight.Medium)
                    Text(
                        "在 设置 → 本应用 → 指标 CSV 中开启，然后运行一次提示词。每加载一个模型" +
                            "就会写出一个文件，覆盖其中的每一轮。勾选两个即可对比它们。",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        items(files) { f ->
            val title = titles[f].orEmpty()
            ListItem(
                leadingContent = {
                    Checkbox(checked = selection.contains(f), onCheckedChange = { onToggle(f) })
                },
                headlineContent = {
                    Text(title.ifEmpty { f.name }, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                },
                supportingContent = {
                    // 先是时间戳 + 大小，然后是纯文件名（标识现在已经是标题了）。
                    // 文件名会去掉固定的 "bmoe-" 前缀和 ".csv" 后缀——它只是为了
                    // 匹配分享出去的文件，而时间戳本身已经带了日期。
                    val short = f.name.removePrefix("bmoe-").removeSuffix(".csv")
                    Text(
                        "${stamp.format(Date(f.lastModified()))} · ${f.length() / 1024} KiB · $short",
                        fontSize = 11.sp, maxLines = 1,
                    )
                },
                trailingContent = { TextButton(shape = Corner, onClick = { onDelete(f) }) { Text("删除") } },
                modifier = Modifier.clickable(onClick = { onPick(f) }),
            )
            HorizontalDivider()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CsvView(file: File, modifier: Modifier) {
    val glossary = rememberGlossary()
    // 每个 token 一行——最坏也就几千行，每行约 112 字节——所以整个文件可以直接放进内存，
    // 流式读取并无收益。
    val csv = remember(file) { Csv.read(file) }
    // 表头和每一行共用同一个横向滚动，这样各列才能保持对齐。每个
    // 可滚动的 Row 都读写这同一份状态，所以拖动其中任意一个，它们会一起移动。
    val hscroll = rememberScrollState()
    // 状态放在这里而不是放进 lazy item，这样把配置卡片滚出屏幕后
    // 它不会又折叠回去。
    var showConfig by remember(file) { mutableStateOf(false) }
    // 整个视图就是一个纵向滚动区。配置卡片、逐轮摘要和说明文字都是
    // lazy item，而不是列表上方的固定块——固定块会随轮数增加而增长出一张张摘要
    // 卡片，最终把最后的 token 行挤出屏幕，而且没有任何办法
    // 能滚动到它们（#53）。作为 item，它们会随内容一起滚走；列标题则通过
    // stickyHeader 固定不动。
    LazyColumn(modifier.fillMaxSize()) {
        // 先呈现这次运行是什么，再呈现它做了什么。配置未知的文件，就是一个
        // 其数字无法与任何东西比较的文件。
        if (csv.info.isNotEmpty()) {
            item {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        // 会话的关键事实一览，来自服务端在加载时的报告。
                        Text(csv.label(glossary), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Text(
                            PreambleFacts.all.filter { it.key != PreambleFacts.MODEL }
                                .mapNotNull { f -> csv.info[f.key]?.let { "${f.label} ${glossary.value(f.key, it)}" } }
                                .joinToString(" · "),
                            fontSize = 11.sp,
                        )
                        // 上面这几行只是一瞥，不是完整记录：它们只列出序言中
                        // 三十来个设置里的寥寥数个，而它们刻意略去的那些
// （尤其是专家卸载，它改变的是输出而非仅仅速度）恰恰是一次已保存的
                        // 运行最难以其他方式说明的东西。
                        // 其余部分。文件里的一切都在一次点击之外，并随着引擎增加
                        // 新的开关，始终只差一次点击。见 #136。
                        val rows = remember(csv, glossary) { glossary.rows(csv.info) }
                        TextButton(shape = Corner,
                            onClick = { showConfig = !showConfig },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                        ) {
                            Text(
                                if (showConfig) "隐藏完整配置"
                                else "完整配置（${rows.size} 项设置）",
                                fontSize = 11.sp,
                            )
                        }
                        if (showConfig) ConfigRows(rows, MaterialTheme.colorScheme.primary, emptySet())
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
        items(csv.summaries) { s ->
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                Text(
                    s.removePrefix("# summary ").replace(" ", "   "),
                    fontFamily = CodeFont, fontSize = 11.sp,
                    modifier = Modifier.padding(10.dp),
                )
            }
            Spacer(Modifier.height(4.dp))
        }
        item {
            Text(
                "${csv.rows.size} 个 token · ${csv.header.size} 列 · 横向滚动可查看全部",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        // 表头会固定在列表顶部，而行在它下面滚动。外层包一个
        // 不透明的 Surface，这样 token 行不会透出来；同时携带共享的横向滚动状态，
        // 让它和各行同步横向滑动。
        stickyHeader {
            Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(Modifier.horizontalScroll(hscroll).padding(horizontal = 8.dp)) {
                        csv.header.forEach { h -> Cell(h, bold = true) }
                    }
                    HorizontalDivider()
                }
            }
        }
        items(csv.rows) { r ->
            Row(Modifier.horizontalScroll(hscroll).padding(horizontal = 8.dp)) { r.forEach { v -> Cell(v) } }
        }
    }
}

@Composable
private fun Cell(text: String, bold: Boolean = false) {
    Text(
        text,
        fontFamily = CodeFont,
        fontSize = 11.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        maxLines = 1,
        modifier = Modifier.width(88.dp).padding(vertical = 3.dp, horizontal = 2.dp),
    )
}

// ── compare ─────────────────────────────────────────────────────────────────────────────────────

@Composable
private fun CompareView(fa: File, fb: File, modifier: Modifier) {
    val glossary = rememberGlossary()
    val a = remember(fa) { Csv.read(fa) }
    val b = remember(fb) { Csv.read(fb) }
    // 只保留两个文件都有的列：把一条序列画在空白上，算不上比较。
    val metrics = remember(a, b) { a.plottable().filter { it in b.plottable() } }
    // 默认打开一列真正会动的指标。以前这里默认用 cache_budget_mib，那时还有
    // 调控器会在压力下让它变动；现在预算在一次运行内是固定的，所以那个默认值
    // 会让视图打开时是一条平线。命中率才是配置变化最先反映出来的地方。
    var metric by remember(metrics) {
        mutableStateOf(metrics.firstOrNull { it == "cache_hit_pct" } ?: metrics.firstOrNull() ?: "")
    }
    var expanded by remember { mutableStateOf(false) }

    val colorA = MaterialTheme.colorScheme.primary
    val colorB = MaterialTheme.colorScheme.error

    if (metrics.isEmpty()) {
        Box(modifier.fillMaxSize().padding(12.dp)) { Text("这两个文件没有共同的数值列。", fontSize = 13.sp) }
        return
    }

    // 内容比屏幕高（图表 + 图例 + 两份完整配置），所以可以滚动。
    val changed = remember(a, b) { (a.info.keys + b.info.keys).filter { a.info[it] != b.info[it] }.toSet() }
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box {
            OutlinedButton(shape = Corner, onClick = { expanded = true }) { Text(metric.ifEmpty { "选择一列" }) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                metrics.forEach { m ->
                    DropdownMenuItem(
                        text = { Text(m, fontFamily = CodeFont, fontSize = 13.sp) },
                        onClick = { metric = m; expanded = false },
                    )
                }
            }
        }
        FieldNote(metric)

        val sa = a.column(metric).orEmpty()
        val sb = b.column(metric).orEmpty()
        LineChart(sa, sb, colorA, colorB, Modifier.fillMaxWidth().height(240.dp))

        Legend(fa.name, a.label(glossary), colorA, sa)
        Legend(fb.name, b.label(glossary), colorB, sb)
        Text(
            "x = 文件内的 token 序号（各轮首尾相接）。两条曲线共用一个 y 轴刻度，" +
                "这是让比较有意义的唯一方式。",
            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // 每次运行的完整配置，改动的设置会高亮——不是只给差异，这样
        // 保持不变的设置（两边都是 dense=warm？）依然可以读到。
        Text("配置", fontSize = 13.sp, fontWeight = FontWeight.Bold)
        FullConfig(a, colorA, changed)
        FullConfig(b, colorB, changed)
    }
}

@Composable
private fun Legend(name: String, label: String, color: Color, series: List<Float?>) {
    val vals = series.filterNotNull()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(Modifier.size(12.dp).padding(top = 3.dp)) { drawRect(color) }
        Column {
            // 先显示配置，再显示文件名：这是哪一次运行比它碰巧叫什么
            // 更重要。
            if (label.isNotEmpty()) Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Text(name, fontFamily = CodeFont, fontSize = 10.sp, maxLines = 1,
                 color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                if (vals.isEmpty()) "无数据" else
                    "n=${vals.size}  min=${fmt(vals.min())}  mean=${fmt(vals.average().toFloat())}  max=${fmt(vals.max())}",
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The parameter table as the preamble's glossary; empty (raw key names) until the engine answers. */
@Composable
private fun rememberGlossary(): ConfigGlossary {
    val engine by Engine.state.collectAsStateWithLifecycle()
    return remember(engine.params) { ConfigGlossary(engine.params.orEmpty()) }
}

/**
 * 单个文件的完整运行配置——序言中的每个字段，无论是否存在。
 * `changed` 标记与其他文件不同的设置，这样“什么变了”依然一目了然，
 * 同时也不会隐藏“什么没变”。
 */
@Composable
private fun FullConfig(csv: Csv, accent: Color, changed: Set<String>) {
    val glossary = rememberGlossary()
    if (csv.info.isEmpty()) {
        Text("此文件没有运行头信息——其配置未知。",
             fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
        return
    }
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Canvas(Modifier.size(12.dp)) { drawRect(accent) }
                Text(csv.info["model"] ?: "run", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            ConfigRows(glossary.rows(csv.info), accent, changed)
        }
    }
}

/**
 * 一次运行的全部配置行，[changed] 中的项用 [accent] 高亮。由单文件
 * 视图与对比视图共用，这样两者对“文件自称的配置”永远不会给出不同说法。
 */
@Composable
private fun ConfigRows(rows: List<Triple<String, String, String>>, accent: Color, changed: Set<String>) {
    rows.forEach { (key, label, value) ->
        val isChanged = key in changed
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("$label:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                 modifier = Modifier.weight(1f))
            Text(
                value,
                fontSize = 11.sp,
                fontFamily = CodeFont,
                // 被改动的设置正是看它的理由；让它成为视线落点。
                fontWeight = if (isChanged) FontWeight.Bold else FontWeight.Normal,
                color = if (isChanged) accent else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * 一条序列绘制成描边路径，在 [n] 个 x 槽位上映射到 y 范围 [lo]..[hi]。
 *
 * 这个范围从哪来，正是此处两张图表的全部差别——两文件对比共用一个
 * 范围，相关性叠加为每条序列单独取范围——所以它由调用方决定。它们的共同点是：
 * null 就是断点，断点绝不桥接，因为跨缺失数据插值出来的线，是对并不存在的
 * 数据的谎言。
 */
private fun DrawScope.drawSeries(values: List<Float?>, n: Int, lo: Float, hi: Float, color: Color) {
    val span = (hi - lo).takeIf { it > 1e-6f } ?: 1f
    val path = Path()
    var started = false
    values.forEachIndexed { i, v ->
        if (v == null) return@forEachIndexed
        val px = size.width * i / (n - 1).toFloat()
        val py = size.height - (v - lo) / span * size.height
        if (started) path.lineTo(px, py) else { path.moveTo(px, py); started = true }
    }
    if (started) drawPath(path, color, style = Stroke(width = 3f))
}

/**
 * 两条序列共用一对坐标轴，手工绘制，而不是为了单独一张图引入图表库——
 * 两者共用一个 y 轴范围：两条自动缩放的轴会让任意两次运行看起来都一样，
 * 那正是这个任务的反面。
 */
@Composable
private fun LineChart(a: List<Float?>, b: List<Float?>, colorA: Color, colorB: Color, modifier: Modifier) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    val all = (a + b).filterNotNull()
    if (all.isEmpty()) {
        Box(modifier) { Text("没有可绘制的数据", fontSize = 12.sp) }
        return
    }
    var lo = all.min()
    var hi = all.max()
    if (hi - lo < 1e-6f) { hi += 1f; lo -= 1f } // 平直的序列是一个事实，不是除以零
    val n = maxOf(a.size, b.size, 2)

    Canvas(modifier) {
        // 三条网格线：足以读出数值水平，又少到不至于变成牢笼。
        listOf(0f, 0.5f, 1f).forEach { t ->
            val yy = size.height * t
            drawLine(grid, Offset(0f, yy), Offset(size.width, yy), strokeWidth = 1f)
        }
        // 两者共用一个范围——这个共享刻度本身就是比较。
        drawSeries(a, n, lo, hi, colorA)
        drawSeries(b, n, lo, hi, colorB)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(fmt(lo), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(fmt(hi), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ── 字段说明 ─────────────────────────────────────────────────────────────────────────────────
/** “越低越好” / “越高越好” / “视情况”，带颜色，三个字符加一个词。 */

@Composable
private fun BetterBadge(better: Better) {
    val (text, color) = when (better) {
        Better.LOWER -> "↓ 越低越好" to MaterialTheme.colorScheme.primary
        Better.HIGHER -> "↑ 越高越好" to MaterialTheme.colorScheme.primary
        Better.NEUTRAL -> "– 视情况" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(text, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = color)
}

/** The one-line meaning of the currently plotted column, under a chart or a picker. */
@Composable
private fun FieldNote(name: String) {
    val f = MetricFields.of(name) ?: return
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(f.short, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            BetterBadge(f.better)
        }
        Text(f.measures, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun GlossaryView(modifier: Modifier) {
    val glossary = rememberGlossary()
    LazyColumn(modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        // 分两半，按读取文件的顺序：序言说明这次运行是用什么配置跑起来的，
        // 各列说明它之后测到了什么。
        item {
            GlossarySection(
                "逐 token 列",
                "每个生成的 token 一行。\"视情况\"表示两个方向都不能简单说好——更" +
                    "大的缓存能换来命中，但也会招致回收之争，而 mem_available 并不诚实。",
                top = 10.dp,
            )
        }
        items(MetricFields.all) { f ->
            Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(f.name, fontFamily = CodeFont, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    BetterBadge(f.better)
                }
                Text(f.short, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Text(f.measures, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider()
        }
        // 配置那一半。只展示一次运行的完整序言（#136）仍只是一半答案，因为
        // 其中列出的设置若不加解释——有几项单看键名根本猜不出来，还有一项
        // （predict_spec_max）当成裸数字读会主动误导人。
        item {
            GlossarySection(
                "运行配置",
                "每个文件顶部的序言：说明这次运行用什么配置跑起来，相对于" +
                    "它测到了什么。引擎、卸载或预测方式不同的两个文件，是" +
                    "两次实验，而不是一次比较。",
                top = 24.dp,
            )
        }
        items(PreambleFacts.all) { f ->
            GlossaryEntry(f.key, f.label, f.what)
        }
        // 每一项设置都来自参数表：它的标签和帮助文本就是引擎自己的表述，
        // 所以读者顺着某项设置去 设置 页或代码里看，能找到同一句话。
        if (glossary.params.isEmpty()) {
            item {
                Text(
                    "这些设置的说明来自引擎的参数表，参数表会在引擎启动后送达。",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(glossary.params) { p ->
            GlossaryEntry(ConfigGlossary.preambleKey(p.key), p.labelText + if (p.lossy) "（会改变回复）" else "", p.helpText)
        }
    }
}

@Composable
private fun GlossaryEntry(key: String, label: String, what: String) {
    Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(key, fontFamily = CodeFont, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        if (what.isNotEmpty()) Text(what, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider()
}

/** A glossary section heading and its one-paragraph framing, so both halves are introduced alike. */
@Composable
private fun GlossarySection(title: String, blurb: String, top: androidx.compose.ui.unit.Dp) {
    Column(Modifier.padding(top = top, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text(blurb, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ── 相关性：把单个文件自身的多列归一化后画在同一坐标轴上 ─────────────────────────────────────────

/**
 * 把一个文件的多列各自压缩到 0..1 后叠加显示，这样即使单位不同，也能比较它们的
 * 形状。它回答的问题是“这个上升时，那个是否也上升”——例如 rss_anon 是否恰好在
 * majflt 尖峰时下降，这说明缺页是缓存被夺走造成的，而不是模型。对第一个选中的
 * 列计算皮尔逊 r，可以把它量化成一个数字。
 *
 * 归一化是逐列的 min..max：这是有意丢弃量纲。平直线意味着该列没有变化，
 * 而不是它的值为零——图例会保留真实的最小/最大值，所以信息没有丢失。
 */
@Composable
private fun CorrelateView(file: File, modifier: Modifier) {
    val csv = remember(file) { Csv.read(file) }
    val cols = remember(csv) { csv.plottable() }
    // 从内存之争开始：内核收回了多少（rss_anon_mib）、这以重读为代价换来了什么
    // （majflt_mib），以及密集集合有多少存活下来（dense_resident_frac）——这正是
    // 用来区分“缺页来自模型”和“缺页来自缓存”的依据。第三项以前是
    // cache_budget_mib，那时还有调控器会在压力下改变它；现在一次运行内它是固定的，
    // 所以它会让这个视图打开时是一条平线。
    var chosen by remember(cols) {
        mutableStateOf(
            cols.filter { it in setOf("majflt_mib", "rss_anon_mib", "dense_resident_frac") }.ifEmpty { cols.take(2) }
        )
    }
    var expanded by remember { mutableStateOf(false) }

    val palette = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.error,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.inversePrimary,
    )

    if (cols.isEmpty()) {
        Box(modifier.fillMaxSize().padding(12.dp)) { Text("此文件没有可做相关性的数值列。", fontSize = 13.sp) }
        return
    }
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (csv.info.isNotEmpty()) Text(csv.label(rememberGlossary()), fontSize = 12.sp, fontWeight = FontWeight.Medium)

        Box {
            OutlinedButton(shape = Corner, onClick = { expanded = true }) { Text("字段（${chosen.size})") }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                cols.forEach { c ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Checkbox(checked = c in chosen, onCheckedChange = null)
                                Spacer(Modifier.width(6.dp))
                                Text(c, fontFamily = CodeFont, fontSize = 13.sp)
                            }
                        },
                        // 上限为调色板的大小：线比颜色多就不是图表，而是一团乱麻。
                        onClick = {
                            chosen = if (c in chosen) chosen - c
                            else if (chosen.size < palette.size) chosen + c else chosen
                        },
                    )
                }
            }
        }

        val series = chosen.map { it to (csv.column(it) ?: emptyList()) }
        MultiLineChart(series.map { it.second }, palette, Modifier.fillMaxWidth().height(240.dp))

        // 每条序列相对第一个选中项（枢轴）的皮尔逊 r。|r|→1 是紧密
        // 相关，符号表示方向，约等于 0 表示两者独立变动。
        val pivot = series.firstOrNull()
        chosen.forEachIndexed { i, name ->
            val col = MetricFields.of(name)
            val vals = series[i].second.filterNotNull()
            val r = if (pivot != null && i > 0) pearson(pivot.second, series[i].second) else null
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Canvas(Modifier.size(12.dp).padding(top = 3.dp)) { drawRect(palette[i % palette.size]) }
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(name, fontFamily = CodeFont, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        if (i == 0) Text("枢轴", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                        else if (r != null) Text("r=${fmt(r)} ${corrWord(r)}", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                    if (col != null) Text(col.short, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (vals.isNotEmpty()) Text(
                        "min=${fmt(vals.min())}  max=${fmt(vals.max())}",
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Text(
            "每条线各自压缩到自己的 0..1，所以只有形状可比——图例保留了" +
                "真实范围。r 是相对第一个字段（枢轴）计算的：接近 ±1 时它们同向而动，接近 0 则不相关。",
            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // 这个文件来自哪次运行，完整呈现——不知道产生这些数据的配置，
        // 相关系数毫无意义。
        Text("配置", fontSize = 13.sp, fontWeight = FontWeight.Bold)
        FullConfig(csv, MaterialTheme.colorScheme.primary, emptySet())
    }
}

/** How to say a correlation out loud, so the number is not the only cue. */
private fun corrWord(r: Float): String = when {
    abs(r) >= 0.8f -> if (r > 0) "同向变动" else "反向变动"
    abs(r) >= 0.4f -> if (r > 0) "弱同向" else "弱反向"
    else -> "不相关"
}

/**
 * 同一坐标轴上的 N 条序列，每条独立归一化到 0..1。不同于两文件对比（那里共用
 * 一个刻度，因为在那儿量值本身就是比较对象），这里的量值在构造上就互不相同——
 * cache_budget 以 GiB 计，而 dense_resident_frac 在 [0,1]——所以每条各用自己的刻度，
 * 只声称它们的形状能对得上。
 */
@Composable
private fun MultiLineChart(series: List<List<Float?>>, palette: List<Color>, modifier: Modifier) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    if (series.all { it.filterNotNull().isEmpty() }) {
        Box(modifier) { Text("请至少选择一个字段", fontSize = 12.sp) }
        return
    }
    val n = maxOf(series.maxOfOrNull { it.size } ?: 2, 2)
    // 逐序列的最小/最大值，只计算一次：归一化就是每列内部的 min..max。
    val ranges = series.map { s -> s.filterNotNull().let { v -> (v.minOrNull() ?: 0f) to (v.maxOrNull() ?: 1f) } }

    Canvas(modifier) {
        val w = size.width; val h = size.height
        listOf(0f, 0.5f, 1f).forEach { t -> drawLine(grid, Offset(0f, h * t), Offset(w, h * t), strokeWidth = 1f) }
        series.forEachIndexed { si, s ->
            // 每条序列用自己的范围：这里只声称形状能对得上。
            val (lo, hi) = ranges[si]
            drawSeries(s, n, lo, hi, palette[si % palette.size])
        }
    }
}

/**
 * 实时卡片：当前（或最近一次）生成的速率，以及每个 token 的时间去向，来自
 * 服务端的 token 与 done 事件。与桌面端 UI 相同的四个术语：计算、flash
 * 等待、缓存管理，以及无人认领的部分（CPU 外时间：zram、抢占、频率上限）。
 */
@Composable
private fun LiveCard() {
    val engine by Engine.state.collectAsStateWithLifecycle()
    val t = engine.live
    val loc = Locale.US
    ElevatedCard(Modifier.fillMaxWidth().padding(12.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val model = engine.info?.model
            if (model == null) {
                Text("未加载模型。", fontWeight = FontWeight.Medium)
                return@Column
            }
            if (t.step == 0 && t.avgTokensPerSecond <= 0) {
                Text("${model.name}：尚未生成任何内容。", fontWeight = FontWeight.Medium)
                return@Column
            }
            val done = t.avgTokensPerSecond > 0
            Text(
                if (done) String.format(loc, "%.2f tok/s   avg (%d tokens)", t.avgTokensPerSecond, t.step)
                else String.format(loc, "%.2f tok/s   (token %d/%d)", t.tokensPerSecond, t.step, t.steps),
                fontWeight = FontWeight.Bold, fontSize = 18.sp,
            )
            if (!model.streaming) {
                Text(
                    "mmap 基线模式：模型通过操作系统页缓存读取，所以在该模式下无法观察到" +
                        "逐 token 的 flash I/O、计算拆分和缓存命中。",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }
            val b = breakdown(t)
            val suffix = if (b.isAverage) " avg" else ""
            if (b.wallMs > 0.0) {
                Text(String.format(loc, "%.0f ms/token  →  %.2f tok/s", b.wallMs, 1000.0 / b.wallMs), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
            MeterRow("计算$suffix", b.computeMs, b.totalMs, MaterialTheme.colorScheme.primary)
            MeterRow("flash 等待$suffix", b.flashWaitMs, b.totalMs, MaterialTheme.colorScheme.tertiary)
            MeterRow("缓存管理$suffix", b.mgmtMs, b.totalMs, MaterialTheme.colorScheme.secondary)
            MeterRow("未归因$suffix", b.unattributedMs, b.totalMs, MaterialTheme.colorScheme.outline)
            val hit = if (t.cacheHitPct >= 0) String.format(loc, "命中 %.0f%%", t.cacheHitPct) else "命中 —"
            val diag = buildString {
                if (b.cpuBusyPct >= 0.0) {
                    append(String.format(loc, "CPU %.0f%% busy", b.cpuBusyPct))
                    if (b.faultsPerToken >= 0.0) append(String.format(loc, "  ·  %.0f faults/tok", b.faultsPerToken))
                    append("  ·  ")
                }
                append(hit)
            }
            Text(diag, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            engine.cpuTempC?.let { Text(String.format(loc, "CPU %.1f°C", it), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (done) {
                val prefill = if (t.prefillTps > 0) String.format(loc, "prefill %.1f tok/s", t.prefillTps) else ""
                val ttft = if (t.ttftS >= 0) String.format(loc, "TTFT %.2fs", t.ttftS) else ""
                if (prefill.isNotEmpty() || ttft.isNotEmpty()) Text(listOf(prefill, ttft).filter { it.isNotEmpty() }.joinToString("   ·   "), fontSize = 13.sp)
                val streamed = if (t.readMib >= 0) String.format(loc, "streamed %.0f MB", t.readMib) else ""
                val cache = if (t.cacheResidentMib >= 0) String.format(loc, "cache %.0f/%.0f MiB", t.cacheResidentMib, t.cacheBudgetMib) else ""
                if (streamed.isNotEmpty() || cache.isNotEmpty()) Text(listOf(streamed, cache).filter { it.isNotEmpty() }.joinToString("   ·   "), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (t.mtpDecodes > 0 && t.mtpDrafted > 0) {
                    Text(
                        String.format(loc, "guessing: %d/%d kept (%.0f%%), %.2f tok per pass, %.2f tok/s real",
                            t.mtpAccepted, t.mtpDrafted, t.mtpAcceptancePct, t.mtpTokensPerDecode, t.effectiveTokensPerSecond),
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun MeterRow(label: String, value: Double, total: Double, color: Color) {
    val frac = if (total > 0) (value / total).toFloat().coerceIn(0f, 1f) else 0f
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, fontSize = 12.sp, modifier = Modifier.width(96.dp))
        LinearProgressIndicator(progress = { frac }, color = color, modifier = Modifier.weight(1f).height(8.dp))
        Text(String.format(Locale.US, "%.0f ms", value), fontSize = 12.sp, modifier = Modifier.width(56.dp))
    }
}
