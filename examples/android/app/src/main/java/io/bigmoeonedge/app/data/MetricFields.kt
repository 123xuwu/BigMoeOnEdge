package io.bigmoeonedge.app.data

/**
 * What each metrics-CSV column means, in one place. The engine writes the columns
 * (core/src/metrics/csv_metrics_sink.cpp); this is the reader's side of that contract, so a column
 * added there and not described here still plots — it just shows up unexplained, which is the right
 * failure. Keep the names in exact sync with the header the engine writes.
 */
enum class Better { LOWER, HIGHER, NEUTRAL }

data class MetricField(
    val name: String,
    val short: String,   // what it is, in a phrase
    val measures: String, // what the number actually counts
    val better: Better,   // and which direction is good — NEUTRAL when "it depends" is the honest answer
)

object MetricFields {
    val all: List<MetricField> = listOf(
        MetricField("turn", "第几轮生成", "会话内从 0 开始的轮次；一个文件跨越所有轮次", Better.NEUTRAL),
        MetricField("step", "token 序号", "本 token 在该轮内的编号，从 1 开始", Better.NEUTRAL),
        MetricField("steps", "请求的 token 数", "该轮的 n_predict 目标值", Better.NEUTRAL),

        MetricField("wall_ms", "token 耗时", "该 token 的全部耗时——唯一直接测量的数字；tok/s = 1000/wall_ms", Better.LOWER),
        MetricField("compute_ms", "计算（残差！）", "开启流式时，是扣除 io/stall 和 mgmt 之后的剩余量——并非直接测量，因此也吸收了缺页与调度停滞，且在 0 处截断。关闭流式时它就是 wall_ms", Better.LOWER),
        MetricField("io_ms", "闪存读取时间", "从闪存读取专家所用时间。串行模式下它是 wall_ms 的一部分；重叠模式下是各通道繁忙时间之和，因此可能超过 wall_ms", Better.LOWER),
        MetricField("stall_ms", "等待闪存", "仅重叠模式：至少有一个计算线程被流式读取阻塞的墙钟时间——是停滞区间的并集，不是每线程平均值，因此重叠的等待只计一次", Better.LOWER),
        MetricField("flash_wait_ms", "闪存等待", "作为 wall_ms 一部分的闪存项，由服务器按本次运行的模式选定：串行取 io_ms，重叠取 stall_ms。即面板上闪存条绘制的内容", Better.LOWER),
        MetricField("mgmt_ms", "缓存维护", "本 token 用于提交、驱逐和重排 LRU 缓存的时间——外加周期性的 dense 驻留探测，引擎把它并入此处", Better.LOWER),

        MetricField("read_bytes", "闪存读取量", "本 token 从闪存读取的专家字节数（非累计）", Better.LOWER),
        MetricField("cache_hit_pct", "命中率（累计！）", "由缓存满足的专家查询占比——从会话开始起平均，且包含预填充（早期占主导），因此变化缓慢，不是逐 token 的值", Better.HIGHER),

        MetricField("majflt", "硬缺页", "内核回收、导致本 token 在计算中途必须从闪存重新读取的页数", Better.LOWER),
        MetricField("majflt_mib", "缺页 MiB", "同样这些缺页折算成内存：majflt × 内核页大小（4 KiB，大页设备为 16 KiB——运行时解析，不做假设）。可直接与 read_bytes 比较——我们主动的读取对内核强制的读取", Better.LOWER),
        MetricField("cpu_ms", "CPU 占用", "所有线程的 CPU 时间。与 wall_ms × 线程数比较：接近 100% 表示计算受限，远低于则说明核心被限频或阻塞", Better.NEUTRAL),

        MetricField("cache_budget_mib", "缓存预算", "本次运行使用的专家缓存大小。运行期间固定（显式的 --cache-mb，或加载时 auto 算出的值）。更大能换来命中率，但占用内存", Better.NEUTRAL),
        MetricField("dense_resident_frac", "仍驻留内存的 dense 权重", "仍驻留的 dense（非专家）权重的估算比例——基于 256 页 mincore 的采样，不是精确比例，每几个 token 刷新一次，因此多数行携带的是上一次采样值。'anon' 模式下采样我们自己的缓冲区（zram 是否占着它？）；mmap/warm 模式下采样 mmap（内核是否在丢弃模型？）。需与 majflt 一起看。-1 仅出现在首次采样之前、关闭流式时，或 /proc 不可读时", Better.HIGHER),

        MetricField("rss_anon_mib", "匿名驻留", "驻留的匿名内存：专家缓存与 KV/激活缓冲区——在默认的 'anon' dense 策略下还包括 dense 权重。在 cache_budget 不变的情况下下降＝内核正在拿走它", Better.NEUTRAL),
        MetricField("rss_file_mib", "文件驻留", "驻留的文件后备内存：mmap 的权重，受压时被直接丢弃（而非换出），因此从不体现在 swap 中。但在默认的 'anon' dense 策略下 dense 权重属于匿名内存而非文件后备——此时这里只剩仍然 mmap 的部分，模型由 rss_anon 承载", Better.NEUTRAL),
        MetricField("swap_mib", "我们在 zram 中的内存", "已压缩进 swap 的匿名内存——可以视为已经失去的缓存", Better.LOWER),
        MetricField("rss_mib", "总驻留", "全部驻留内存（VmRSS）", Better.NEUTRAL),

        MetricField("loop_overhead_ms", "token 之间的时间", "llama_decode 之外的一切：采样、反分词、渲染回答、写入 sink。它落在 wall_ms 之外，因而也在上报的 tok/s 之外，这正是它需要单独一列的原因——只影响这个数字的工作每个 token 都要付一次代价，而在别处看不到。对第一个 token 而言，是从预填充结束到首次解码之间的间隔", Better.LOWER),

        MetricField("drain_ms", "等待上一批", "仅重叠模式：eval 线程等待上一层的读取完成以便复用其槽位的时间——是 compute_ms 的一部分，单独命名是为了让较大的计算残差可以被归因而非靠猜", Better.LOWER),
        MetricField("adopt_ms", "等待接管已提交的读取", "仅 route-ahead 模式：加载在暂存前等待自己已提交的推测读取完成——是 mgmt_ms 的一部分", Better.LOWER),
        MetricField("ra_issue_ms", "发起提前读取", "仅 route-ahead 模式：eval 线程发起已提交选择的读取（settle、residency、retain、prefetch）所用时间——是 compute_ms 的一部分", Better.LOWER),
        MetricField("ra_wd_ms", "看门狗控制", "仅 route-ahead 模式：抽样 fresh-gate 控制在 eval 线程上执行的精确 GEMV——是 compute_ms 的一部分", Better.LOWER),

        MetricField("mem_available_mib", "设备'可用'内存（会撒谎）", "内核声称的空闲内存——它把我们自己 mmap 的权重也算作可回收，因此高估了余量", Better.NEUTRAL),
        MetricField("mem_free_mib", "设备空闲", "真正空闲的 RAM，尚未回收任何内容", Better.NEUTRAL),
        MetricField("swap_free_mib", "swap 空闲", "设备真正用尽空间之前剩余的 zram 容量", Better.NEUTRAL),
    )

    private val byName = all.associateBy { it.name }
    fun of(name: String): MetricField? = byName[name]
}

/** One fact of the CSV preamble the app writes from the server's `/api/info`, with what it means. */
data class PreambleFact(val key: String, val label: String, val what: String)

/**
 * The preamble's first line: what the server said about the loaded session (`/api/info`, its
 * `model`), which no parameter holds. These are this app's own file format, so it describes them;
 * every other preamble key is a setting, and the parameter table explains those ([ConfigGlossary]).
 */
object PreambleFacts {
    const val MODEL = "model"
    const val STREAMING = "streaming"
    // What files the app recorded before it wrote its own preamble carry for the same fact.
    const val STREAMING_BEFORE = "moe_stream"

    val all: List<PreambleFact> = listOf(
        PreambleFact("engine", "引擎构建", "产生这些数据行的引擎版本。不同构建之间的运行不可直接比较"),
        PreambleFact(MODEL, "Model", "该 gguf，按文件名"),
        PreambleFact("arch", "架构", "gguf 声明的架构；哪些张量参与流式读取是由其形状决定的，而不是由它决定"),
        PreambleFact("n_ctx", "上下文", "本会话预留的上下文窗口"),
        PreambleFact("n_expert_used", "激活专家", "每个 token 路由到的专家数，已考虑任何覆盖设置"),
        PreambleFact(STREAMING, "流式加载", "专家是否从闪存流式读取；关闭即 mmap 基线，流式运行以它为对照"),
        PreambleFact("compute_threads", "计算线程", "计算类数字所除以的线程数"),
        PreambleFact("busy_threads", "忙碌线程", "一次解码中所有忙碌的线程，重叠模式下含读取通道：CPU 忙碌率的分母"),
    )

    private val byKey = all.associateBy { it.key }
    fun label(key: String): String? = byKey[key]?.label
}
