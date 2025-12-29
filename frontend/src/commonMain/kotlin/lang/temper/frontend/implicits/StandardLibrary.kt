package lang.temper.frontend.implicits

import lang.temper.fs.FileSystem

/** Returns a file system providing the standard library, which is available by import. */
expect fun accessStd(): FileSystem?
