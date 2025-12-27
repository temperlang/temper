package lang.temper.cli.repl

enum class ReplSeparator(
    val keyword: String,
    val description: String,
) {
    Newline("newline", "a line break outside brackets ends a chunk"),
    SemiSemi("semisemi", "like Ocaml, two semicolons (`;;`) end a chunk"),
    Eof("eof", "chunks are not broken; the end of file ends the only chunk"),
    ;

    override fun toString(): String = keyword // Shows up for default value in `temper help repl`.

    companion object {
        val default = Newline
    }
}

enum class ReplPrompt(
    val keyword: String,
    val description: String,
) {
    Enable("normal", "display a prompt before user input"),
    Disable("hide", "hide the prompt, e.g. for batch processes"),
    ;

    override fun toString(): String = keyword // Shows up for default value in `temper help repl`.

    companion object {
        val default = Enable
    }
}
