package io.bigmoeonedge.app

import io.bigmoeonedge.app.data.ConfigGlossary
import io.bigmoeonedge.app.data.Csv
import io.bigmoeonedge.app.data.ParamsZh
import io.bigmoeonedge.app.data.Telemetry
import io.bigmoeonedge.app.data.applyDone
import io.bigmoeonedge.app.data.applyToken
import io.bigmoeonedge.app.data.breakdown
import io.bigmoeonedge.app.data.runTitle
import io.bigmoeonedge.app.data.summaryFields
import io.bigmoeonedge.app.engine.ModelInfo
import io.bigmoeonedge.app.engine.ModelsList
import io.bigmoeonedge.app.engine.ParamSpec
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

/**
 * What the app reads from the server instead of deciding for itself: the flash term and the
 * threads behind the live card, every file of a model, the labels of a run's settings, and a
 * summary's fields by name.
 */
class ServerFactsTest {
    @Test
    fun theFlashBarIsTheTermTheServerChoseAndComputeDividesByItsThreads() {
        val t = Telemetry().applyToken(
            JSONObject("""{"step":3,"steps":10,"wall_ms":100,"io_ms":400,"stall_ms":20,"flash_wait_ms":20,"mgmt_ms":5,"cpu_ms":240}"""),
        )
        t.computeThreads = 4
        t.busyThreads = 8
        val b = breakdown(t)
        // io_ms is summed lane time under overlap (400 > the 100 ms wall): never what the bar shows.
        assertEquals(20.0, b.flashWaitMs, 1e-9)
        assertEquals(60.0, b.computeMs, 1e-9)
        assertEquals(15.0, b.unattributedMs, 1e-9)
        assertEquals(30.0, b.cpuBusyPct, 1e-9)

        t.applyDone(JSONObject("""{"tokens":10,"tok_s":5,"flash_wait_s_tok":0.05,"mgmt_s_tok":0,"cpu_s_tok":0.4}"""))
        val avg = breakdown(t)
        assertTrue(avg.isAverage)
        assertEquals(50.0, avg.flashWaitMs, 1e-9)
        assertEquals(100.0, avg.computeMs, 1e-9)
    }

    @Test
    fun unknownThreadsLeaveTheComputeBarEmptyRatherThanGuessed() {
        val b = breakdown(Telemetry().applyToken(JSONObject("""{"step":1,"wall_ms":100,"flash_wait_ms":10,"cpu_ms":400}""")))
        assertEquals(0.0, b.computeMs, 1e-9)
        assertEquals(-1.0, b.cpuBusyPct, 1e-9)
    }

    @Test
    fun theLoadedModelCarriesItsThreads() {
        val m = ModelInfo.of(JSONObject("""{"name":"m","path":"/p","streaming":true,"compute_threads":6,"busy_threads":10}"""))!!
        assertEquals(6, m.computeThreads)
        assertEquals(10, m.busyThreads)
    }

    @Test
    fun aModelIsEveryFileTheServerListed() {
        val list = ModelsList.of(
            JSONObject(
                """{"models_dir":"/a","scan_dirs":["/b"],"catalog":[],"local":[
                  {"name":"m-00001-of-00002.gguf","path":"/b/m-00001-of-00002.gguf","dir":"/b","bytes":3,"shards":2,
                   "files":["/b/m-00001-of-00002.gguf","/b/m-00002-of-00002.gguf"],"arch":"qwen3moe","streamable":true,"complete":true},
                  {"name":"d.gguf","path":"/a/d.gguf","dir":"/a","bytes":1,"shards":1,"files":["/a/d.gguf"],"arch":"llama","streamable":false,"complete":true}]}""",
            ),
        )
        assertEquals(listOf("/b"), list.scanDirs)
        assertEquals(listOf("/b/m-00001-of-00002.gguf", "/b/m-00002-of-00002.gguf"), list.local[0].files)
        assertEquals("/b", list.local[0].dir)
        assertEquals(setOf("m-00001-of-00002.gguf", "m-00002-of-00002.gguf", "d.gguf"), list.fileNames)
    }

    private val table = ParamSpec.parse(
        JSONArray(
            """[
              {"key":"cache-mb","label":"Expert cache","type":"int","group":"c","group_label":"C","level":"basic","scope":"session","help":"how big","flag":"--cache-mb","unit":"MiB","default":"auto"},
              {"key":"overlap","label":"Overlap","type":"bool","group":"c","group_label":"C","level":"advanced","scope":"session","help":"","flag":"--overlap","default":true},
              {"key":"drop-cold-frac","label":"Drop cold experts","type":"float","group":"x","group_label":"X","level":"experimental","scope":"session","help":"","flag":"--drop","lossy":true,"default":0},
              {"key":"spec","label":"Speculation","type":"choice","group":"x","group_label":"X","level":"experimental","scope":"session","help":"","flag":"--spec","lossy":true,"default":"off"}
            ]""",
        ),
    )

    @Test
    fun aRunsSettingsAreLabelledAndOrderedByTheParameterTable() {
        val g = ConfigGlossary(table)
        val info = linkedMapOf(
            "engine" to "0.30.0", "model" to "q.gguf", "streaming" to "1",
            "spec" to "off", "overlap" to "1", "cache_mb" to "2048", "drop_cold_frac" to "0.25", "newer_knob" to "7",
        )
        val rows = g.rows(info)
        assertEquals(listOf("engine", "model", "streaming", "cache_mb", "overlap", "drop_cold_frac", "spec", "newer_knob"), rows.map { it.first })
        // Both columns are the row's as the app shows it: the label is the Chinese ParamsZh carries
        // (labelText), and a switch's value is the same choice table's on/off word, which
        // `ConfigGlossary.value` already applies. The raw fields the server sent stay English, and
        // are checked below.
        assertEquals(
            Triple("overlap", ParamsZh.label("overlap", "Overlap"), ParamsZh.choice("on", "on")),
            rows[4],
        )
        assertEquals(Triple("cache_mb", ParamsZh.label("cache-mb", "Expert cache"), "2048 MiB"), rows[3])
        // A key the table does not know is still shown, under its own name.
        assertEquals(Triple("newer_knob", "newer_knob", "7"), rows.last())
        // The raw fields ParamsZh keys on are never rewritten: `label` is what a lookup reads and
        // what the server sent, so translating it in place would break the lookup itself.
        assertEquals("Overlap", g.spec("overlap")!!.label)
        assertEquals("Expert cache", g.spec("cache_mb")!!.label)
        // The identity names what changes the reply and is off its default, and nothing else.
        // The name comes from this table's own row, so the assertion holds whether or not the
        // Chinese table happens to carry this key: `labelText` falls back to the server's label.
        val dropCold = g.spec("drop_cold_frac")!!.labelText
        assertEquals("q.gguf · streamed · $dropCold 0.25", Csv(emptyList(), emptyList(), emptyList(), info).label(g))
        // A file recorded before the app wrote its own preamble names the same fact moe_stream.
        val older = linkedMapOf("model" to "q.gguf", "moe_stream" to "0")
        assertEquals("q.gguf · mmap baseline", Csv(emptyList(), emptyList(), emptyList(), older).label(g))
    }

    @Test
    fun aSummaryIsReadByFieldName() {
        val f = summaryFields("# summary turn=2 tokens=40 tok/s=3.250 cache_hit_pct=81.0 cancelled=1")
        assertEquals("3.250", f["tok/s"])
        assertEquals("2", f["turn"])
        assertEquals("1", f["cancelled"])

        val csv = Files.createTempFile("bmoe", ".csv").toFile()
        try {
            csv.writeText(
                "# bmoe_metrics v=app engine=1 model=Qwen3.gguf\nstep,wall_ms,turn\n1,10,1\n" +
                    "# summary turn=1 tokens=1 tok/s=1.45\n# summary turn=2 tok/s=0.75 tokens=1\n",
            )
            assertEquals("Q · 1.45→0.75 t/s", runTitle(csv))
        } finally {
            csv.delete()
        }
    }
}
