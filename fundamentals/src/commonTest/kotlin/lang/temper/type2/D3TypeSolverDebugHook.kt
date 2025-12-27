package lang.temper.type2

import lang.temper.common.Flushable
import lang.temper.common.ForwardOrBack
import lang.temper.common.LeftOrRight
import lang.temper.common.Style
import lang.temper.common.minimalUnquotedJsonEscaper
import lang.temper.format.OutputToken
import lang.temper.format.OutputTokenType
import lang.temper.format.TokenSerializable
import lang.temper.format.TokenSink
import lang.temper.format.toStringViaTokenSink
import lang.temper.log.Position
import lang.temper.value.TemperFormattingHints

/**
 * Portion of a D3 HTML file before the list of JS string lists
 * each representing lines in a Graphviz digraph.
 *
 * Followed by [d3HtmlSuffix]
 */
private val d3HtmlPrefix = $$"""
    |<!DOCTYPE html>
    |<meta charset="utf-8">
    |<title>Temper Type Solver</title>
    |<body>
    |<script src="https://d3js.org/d3.v7.min.js"></script>
    |<script src="https://unpkg.com/@hpcc-js/wasm@2.20.0/dist/graphviz.umd.js"></script>
    |<script src="https://unpkg.com/d3-graphviz@5.6.0/build/d3-graphviz.js"></script>
    |<button id="go-back">&#x23ee;&#xfe0f;</button>
    |<button id="playing-or-paused"></button>
    |<button id="go-forward">&#x23ed;&#xfe0f;</button>
    |<div id="graph" style="text-align: center"></div>
    |<script type="module">
    |
    |let playingOrPausedButton = document.querySelector('#playing-or-paused');
    |
    |// Manage pausing and unpausing
    |let state = { paused: false, dotIndex: 0 };
    |
    |function setState({ paused = state.paused, dotIndex = state.dotIndex }) {
    |  state = { paused, dotIndex };
    |  location.hash = `#${ paused ? 'paused' : 'playing' },${dotIndex}`;
    |  playingOrPausedButton.textContent = paused
    |      ? '\u25b6\ufe0f' : '\u23f8\ufe0f'; // Pause or play emoji
    |}
    |function getStateFromHash() {
    |  let [, pauseStr, dotIndexStr] = /^(?:#(?:(paused)|[^,]*),(\d+))?/.exec(location.hash || '');
    |  return { paused: !!pauseStr, dotIndex: +(dotIndexStr) || 0 };
    |}
    |
    |setState(getStateFromHash());
    |
    |playingOrPausedButton.addEventListener('click', () => {
    |  setState({ paused: !state.paused });
    |  maybeRenderNext();
    |});
    |document.querySelector('#go-back').addEventListener('click', () => {
    |  setState({ dotIndex: Math.max(0, state.dotIndex - 1) });
    |  render();
    |});
    |document.querySelector('#go-forward').addEventListener('click', () => {
    |  maybeRenderNext(true);
    |});
    |
    |// Animation management
    |let graphviz = d3.select("#graph").graphviz()
    |    .transition(() =>
    |        d3.transition("main")
    |            .ease(d3.easeLinear)
    |            .delay(500) // milliseconds
    |            .duration(1500)
    |    )
    |    .logEvents(true)
    |    .on("initEnd", render);
    |
    |function render() {
    |  let dotLines = dots[state.dotIndex];
    |  let dot = dotLines.join('');
    |  graphviz
    |      .renderDot(dot)
    |      .on("end", maybeRenderNext);
    |}
    |
    |function maybeRenderNext(evenIfPaused = false) {
    |  if ((evenIfPaused || !state.paused) && state.dotIndex + 1 < dots.length) {
    |    setState({ dotIndex: state.dotIndex + 1 });
    |    render();
    |  }
    |}
    |
    |let dots = [
    |
""".trimMargin()

private val d3HtmlSuffix = """
    |];
    |</script>
    |</body>
""".trimMargin()

/**
 * Outputs an animated graph using https://github.com/magjac/d3-graphviz
 */
class D3TypeSolverDebugHook(
    private val out: Appendable,
) : TypeSolverDebugHook, AutoCloseable {
    override fun begin() {
        out.append(d3HtmlPrefix)
    }

    override fun end() {
        out.append(d3HtmlSuffix)
        (out as? Flushable)?.flush()
    }

    private val stepBuffer = mutableListOf<String>()
    override fun beginStep() {
        stepBuffer.clear()
        stepBuffer.add("digraph  {")
        stepBuffer.add("    node [style=\"filled\"]\n")
    }

    override fun node(x: TypeSolverDebugHook.DebugHookNode) {
        stepBuffer.add(
            buildString {
                val key = x.key
                val description = x.description
                append("    $key [shape=\"box\"")
                append("; label=<<TABLE><TR><TD COLSPAN=\"2\">")
                if (description != null) {
                    append(graphvizHtml(description))
                } else {
                    append(graphvizHtml(key))
                }
                append("</TD></TR>")
                // Output rows for solution and bounds
                val solution = x.solution
                if (solution != null) {
                    append("<TR><TD COLSPAN=\"2\">")
                    append(graphvizHtml(solution))
                    append("</TD></TR>")
                }
                val groups = listOf(
                    ">:" to x.lowerBounds,
                    "=" to x.commonBounds,
                    "<:" to x.upperBounds,
                )
                for ((op, bounds) in groups) {
                    val nBounds = bounds.count()
                    var hasOne = false
                    for (b in bounds) {
                        append("<TR>")
                        if (!hasOne) {
                            hasOne = true
                            append("<TD ROWSPAN=\"$nBounds\" VALIGN=\"TOP\">")
                            append(graphvizHtml(OutputToken(op, OutputTokenType.Punctuation)))
                            append("</TD>")
                        }
                        append("<TD>")
                        append(graphvizHtml(b))
                        append("</TD></TR>")
                    }
                }

                append("</TABLE>>") // Ends HTML label

                val details = x.details
                if (details != null) {
                    append(
                        "; tooltip=<${graphvizHtml(
                            toStringViaTokenSink { details.renderTo(it) },
                        )}>",
                    )
                }
                if (x.styles.isDirty) {
                    append("; fillcolor=lightpink")
                }
                append("]\n")
            },
        )
    }

    override fun edge(x: TypeSolverDebugHook.DebugHookEdge) {
        val aNodeKey = x.aNodeKey
        val bNodeKey = x.bNodeKey
        val dir = when (x.dir) { // https://graphviz.org/docs/attrs/dir/
            ForwardOrBack.Forward -> null
            ForwardOrBack.Back -> "back"
            null -> "none"
        }
        val description = x.description

        stepBuffer.add(
            buildString {
                append("    ")
                append(aNodeKey)
                append(" -> ")
                append(bNodeKey)
                var inAttributes = false
                fun attr(name: String, valueMaker: () -> Unit) {
                    if (!inAttributes) {
                        append(" [")
                        inAttributes = true
                    } else {
                        append("; ")
                    }
                    append(name)
                    append('=')
                    valueMaker()
                }
                if (description != null) {
                    attr("label") {
                        append('<')
                        append(graphvizHtml(description))
                        append('>')
                    }
                }
                if (dir != null) {
                    attr("dir") { append(dir) }
                }
                if (x.styles.isDirty) {
                    attr("color") { append("red") }
                }
                if (inAttributes) {
                    append(']')
                }
                append('\n')
            },
        )
    }

    override fun endStep() {
        stepBuffer.add("}") // matches `digraph  {` from beginStep

        out.append("  [\n") // JS like `  [ "line1", "line2", ],`
        for (line in stepBuffer) {
            out.append("    '").append(minimalUnquotedJsonEscaper.escape(line)).append("',\n")
        }
        stepBuffer.clear()
        out.append("  ],\n")
    }

    override fun close() {
        (out as? AutoCloseable)?.close()
    }
}

private fun graphvizHtml(ts: TokenSerializable) = buildString {
    val sink = TemperFormattingHints.makeFormattingTokenSink(
        // Convert a sequence of tokens to HTML that is styled within the limits of graphviz
        object : TokenSink {
            override fun position(pos: Position, side: LeftOrRight) = Unit

            var endLinePending = false
            override fun endLine() {
                endLinePending = true
            }

            override fun emit(token: OutputToken) {
                if (token.type != OutputTokenType.NotEmitted) {
                    if (token.type != OutputTokenType.Space || token.text.isNotBlank()) {
                        styleBefore(token.type.style)
                    }
                    if (endLinePending) {
                        append("<BR>")
                        endLinePending = false
                    }
                    val isVarName = token.type == OutputTokenType.Name &&
                        token.text.startsWith(VAR_PREFIX_CHAR)
                    if (isVarName) { append("<I>") }
                    graphvizHtmlTo(token.text, this@buildString)
                    if (isVarName) { append("</I>") }
                }
            }

            override fun finish() {
                endLinePending = false // Drop trailing endLines
                styleFlush()
            }

            private fun colorAndTags(style: Style) = when (style) {
                Style.LogDebugOutput,
                Style.LogVerboseOutput,
                Style.LogInfoOutput,
                Style.LogWarningOutput,
                Style.LogErrorOutput,
                Style.NormalOutput,
                Style.ErrorOutput,
                Style.SystemOutput,
                Style.UserInput,
                -> null to emptyList()
                Style.CommentToken -> "darkblue" to listOf("I")
                Style.ErrorToken -> "red" to listOf()
                Style.IdentifierToken -> null to emptyList()
                Style.KeyWordToken -> "darkblue" to listOf("B")
                Style.NumberToken,
                Style.ValueToken,
                -> "darkmagenta" to listOf()
                Style.QuotedStringToken -> "darkgreen" to listOf()
                Style.PunctuationToken -> "darkblue" to listOf()
            }

            private var lastStyle: Style? = null
            private fun styleBefore(style: Style) {
                if (style == lastStyle) { return }
                lastStyle?.let { styleAfter(it) } // Close any style before opening a new one.
                lastStyle = style

                val (color, tags) = colorAndTags(style)
                for (tag in tags) {
                    append("<$tag>")
                }
                if (color != null) {
                    append("<FONT color=\"$color\">")
                }
            }

            private fun styleAfter(style: Style) {
                val (color, tags) = colorAndTags(style)
                if (color != null) { append("</FONT>") }
                for (tag in tags.asReversed()) {
                    append("</$tag>")
                }
            }

            private fun styleFlush() {
                lastStyle?.let { styleAfter(it) }
                lastStyle = null
            }
        },
        singleLine = false,
    )
    ts.renderTo(sink)
    sink.finish()
}

private fun graphvizHtml(s: String) = buildString {
    graphvizHtmlTo(s, this)
}

private fun graphvizHtmlTo(s: String, out: StringBuilder) {
    for (c in s) {
        // Graphviz recognizes a specific set of HTML escapes.
        // https://graphviz.org/doc/info/shapes.html#html says
        // > As HTML strings are processed like HTML input, any use of the `"`,
        // > `&`, `<`, and `>` characters in literal text or in attribute values
        // > need to be replaced by the corresponding escape sequence.
        // > For example, if you want to use `&` in a href value, this should be
        // > represented as `&amp;`.
        when (c) {
            '"' -> out.append("&quot;")
            '&' -> out.append("&amp;")
            '<' -> out.append("&lt;")
            '>' -> out.append("&gt;")
            else -> out.append(c)
        }
    }
}
