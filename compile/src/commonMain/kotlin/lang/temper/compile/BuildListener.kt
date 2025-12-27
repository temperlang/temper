package lang.temper.compile

interface BuildListener {
    /** Call when processing of a build starts */
    fun processing() {
        // Do nothing by default.
    }

    /** Call when backends finish producing artifacts */
    fun translated(translations: List<LibraryTranslation>)
}
