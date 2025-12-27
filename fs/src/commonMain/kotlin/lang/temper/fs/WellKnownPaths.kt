package lang.temper.fs

import lang.temper.log.FilePathSegment
import lang.temper.log.dirPath

/**
 * The name of the directory under which we put built files.
 *
 * An advantage of the `.out` suffix is it's ignored by default in
 * [some `.gitignore`](https://tinyurl.com/bbuar6pr) files.
 */
const val TEMPER_OUT_NAME = "temper.out"
val temperOutFilePath = dirPath(TEMPER_OUT_NAME)

/**
 * The name of directory for configuration and external annotation of code.
 *
 * This directory structure is maintained through automated tools and
 * checked into repositories. Because it's to common to delete build
 * directories like `temper.out`, but this shouldn't be, this has an entirely
 * separate directory.
 */
const val TEMPER_KEEP_NAME = "temper.keep"
val temperKeepSegment = FilePathSegment(TEMPER_KEEP_NAME)
val temperKeepFilePath = dirPath(TEMPER_KEEP_NAME)

/** File and directory names to ignore in finding source files. */
val ignoredPaths = setOf(".git", TEMPER_OUT_NAME, TEMPER_KEEP_NAME)
