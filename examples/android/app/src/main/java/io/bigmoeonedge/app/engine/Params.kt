package io.bigmoeonedge.app.engine

import io.bigmoeonedge.app.data.ParamsZh
import org.json.JSONArray
import org.json.JSONObject

private inline fun String.indexOfFirst(from: Int, predicate: (Char) -> Boolean): Int {
    for (i in from until length) if (predicate(this[i])) return i
    return -1
}

/**
 * One row of the engine's parameter table, as `GET /api/params` describes it. The settings screen
 * is rendered from these and nothing else: it knows no parameter by name, so a knob added to the
 * engine appears here with no app change. Same schema the web UI renders from.
 */
data class ParamSpec(
    val key: String,
    val label: String,
    val type: String, // bool | int | float | choice | path | text
    val group: String,
    val groupLabel: String,
    val level: String, // basic | advanced | experimental | debug
    /** A settings form offers this row (`form`); a server that does not say offers its basic rows. */
    val form: Boolean,
    val scope: String, // session | request
    val help: String,
    val flag: String,
    val min: Double?,
    val max: Double?,
    val unit: String?,
    val acceptsAuto: Boolean,
    val lossy: Boolean,
    val choices: List<Pair<String, String>>, // value to label
    val default: Any?,
    /** What a person usually sets: guidance, not a limit. */
    val typical: ClosedFloatingPointRange<Double>? = null,
    /** Values that mean something other than their number, as the value's text to its name (0 to off). */
    val special: List<Pair<String, String>> = emptyList(),
    /** The group's place in the form (`group_order`); null from a server that does not send it. */
    val groupOrder: Int? = null,
    /** The group's one line of help (`group_help`). */
    val groupHelp: String = "",
    /** The group is set with a control of its own (the model, from Models): shown, not edited. */
    val groupReadOnly: Boolean = false,
) {
    /** The name of [value] when it is a special one ("off" for 0), or null. */
    fun specialLabel(value: Any?): String? {
        val text = paramValueText(value)
        return special.firstOrNull { it.first == text }?.second
    }

    // The engine's table is English, and so is everything it prints (`--help`, the CLI, logs):
    // translating it there would translate those too. The app therefore keeps `label`, `help`,
    // `groupLabel`, `groupHelp`, `choices` and `special` exactly as the server sent them — they are
    // what a key is looked up by — and renders them through ParamsZh here, at the edge. The `*Text`
    // members below are what the screens show; the raw ones stay for tests and for lookups.

    /** The row's label as this app shows it. */
    val labelText: String get() = ParamsZh.label(key, label)

    /** The row's help as this app shows it. */
    val helpText: String get() = ParamsZh.help(key, help)

    /** The row's help as this app shows it, split the way the screens show it. */
    fun helpPartsText(): Pair<String, String> = helpPartsOf(helpText)

    /** A choice's label as this app shows it ([value] is what the flag accepts). */
    /**
     * A choice's label as this app shows it, or [value] itself when this row does not offer it.
     *
     * The lookup is bounded by this row's own `choices`, and that bound is what keeps it honest: a
     * value like `auto` or `off` means different things on different knobs (a cache size, a prefix
     * cache, a device choice), so a table keyed by the value alone would read one knob's wording on
     * another. A row that does not offer the value gets it back unchanged, which is also what a row
     * whose choices the server did not send does.
     */
    fun choiceText(value: String): String {
        val offered = choices.firstOrNull { it.first == value }?.second ?: return value
        return ParamsZh.choice(value, offered)
    }

    /** The group's section title as this app shows it (keyed by the stable group name). */
    val groupLabelText: String get() = ParamsZh.group(group, groupLabel.ifEmpty { group })

    /** The group's one line of help as this app shows it. */
    val groupHelpText: String get() = ParamsZh.groupHelp(group, groupHelp)

    /** The typical range in words, "usually 2 to 8" (the unit is the field's suffix), or null for a row with none. */
    fun typicalText(): String? = typical?.let { r ->
        val lo = paramValueText(r.start)
        val hi = paramValueText(r.endInclusive)
        "usually " + if (lo == hi) lo else "$lo to $hi"
    }

    /** The help's first sentence, always shown, and the rest, shown on demand. Same rule as the web UI. */
    fun helpParts(): Pair<String, String> = helpPartsOf(help)

    /** [helpParts] for any text, so a translated help is split by the same rule. */
    private fun helpPartsOf(help: String): Pair<String, String> {
        val text = help.trim()
        var depth = 0
        for (i in text.indices) {
            when (text[i]) {
                '(' -> depth++
                ')' -> depth--
                '.', '!', '?' -> {
                    val next = i + 1
                    if (depth <= 0 && next >= 24 && next < text.length && text[next].isWhitespace()) {
                        val start = text.indexOfFirst(next) { !it.isWhitespace() }
                        if (start > 0 && (text[start].isUpperCase() || text[start].isDigit())) {
                            return text.substring(0, next) to text.substring(start)
                        }
                    }
                }
            }
        }
        return text to ""
    }

    companion object {
        fun parse(arr: JSONArray): List<ParamSpec> = (0 until arr.length()).map { i -> of(arr.getJSONObject(i)) }

        fun of(o: JSONObject): ParamSpec {
            val choices = o.optJSONArray("choices")?.let { c ->
                (0 until c.length()).map { i ->
                    val x = c.getJSONObject(i)
                    x.optString("value") to x.optString("label").ifEmpty { x.optString("value") }
                }
            }.orEmpty()
            val typical = o.optJSONObject("typical")?.let { r ->
                if (r.has("min") && r.has("max")) r.getDouble("min")..r.getDouble("max") else null
            }
            val special = o.optJSONArray("special")?.let { s ->
                (0 until s.length()).map { i ->
                    val x = s.getJSONObject(i)
                    paramValueText(x.opt("value")) to x.optString("label")
                }
            }.orEmpty()
            return ParamSpec(
                key = o.getString("key"),
                label = o.optString("label", o.getString("key")),
                type = o.optString("type", "text"),
                group = o.optString("group", ""),
                groupLabel = o.optString("group_label", o.optString("group", "")),
                level = o.optString("level", "basic"),
                form = if (o.has("form") && !o.isNull("form")) o.getBoolean("form") else o.optString("level", "basic") == "basic",
                scope = o.optString("scope", "session"),
                help = o.optString("help", ""),
                flag = o.optString("flag", ""),
                min = if (o.has("min") && !o.isNull("min")) o.getDouble("min") else null,
                max = if (o.has("max") && !o.isNull("max")) o.getDouble("max") else null,
                unit = o.optString("unit").takeIf { it.isNotEmpty() },
                acceptsAuto = o.optBoolean("accepts_auto", false),
                lossy = o.optBoolean("lossy", false),
                choices = choices,
                default = if (o.has("default") && !o.isNull("default")) o.get("default") else null,
                typical = typical,
                special = special,
                groupOrder = if (o.has("group_order") && !o.isNull("group_order")) o.optInt("group_order") else null,
                groupHelp = o.optString("group_help", ""),
                groupReadOnly = o.optBoolean("group_read_only", false),
            )
        }

        /**
         * Groups in the table's group order (`group_order`, else where a group first appears), each
         * with its rows in the table's order.
         */
        fun grouped(specs: List<ParamSpec>): List<Pair<String, List<ParamSpec>>> {
            val order = mutableListOf<String>()
            val by = mutableMapOf<String, MutableList<ParamSpec>>()
            for (s in specs) {
                if (s.group !in by) {
                    order += s.group
                    by[s.group] = mutableListOf()
                }
                by.getValue(s.group) += s
            }
            // sortedBy is stable: groups without an order keep where they first appeared.
            return order.sortedBy { g -> by.getValue(g).first().groupOrder ?: Int.MAX_VALUE }
                .map { g -> (by.getValue(g).first().groupLabel) to by.getValue(g) }
        }
    }
}

/** A value as the config carries it, rendered for a field: numbers plain, `auto` as is, bools as is. */
fun paramValueText(v: Any?): String = when (v) {
    null -> ""
    is Boolean -> v.toString()
    is Number -> if (v is Double && v == Math.floor(v) && !v.isInfinite()) v.toLong().toString() else v.toString()
    else -> v.toString()
}

/**
 * What a field's text becomes on the wire: the JSON type the schema says (`bool`, `int`, `float`)
 * or a string (`choice`, `path`, `text`, and the literal `auto` where the row accepts it). Null when
 * the text is not a value of that type: the field shows the problem and nothing is sent.
 */
fun paramValueFromText(spec: ParamSpec, text: String): Any? {
    val t = text.trim()
    return when (spec.type) {
        "bool" -> t.toBooleanStrictOrNull()
        "int" -> if (spec.acceptsAuto && t == "auto") "auto" else t.toLongOrNull()
        "float" -> t.toDoubleOrNull()
        else -> t
    }
}
