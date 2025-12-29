package lang.temper.common

/**
 * Pops up a window with a graph rendered from a DOT file for when printf
 * debugging doesn't cut it.
 */
expect fun showGraphvizFileBestEffort(dotContent: String, title: String?)
