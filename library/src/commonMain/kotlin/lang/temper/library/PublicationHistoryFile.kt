package lang.temper.library

import lang.temper.common.MimeType
import lang.temper.log.FilePathSegment

/**
 * A publication history file is a file that appears in a
 * [library root][LibraryConfiguration.libraryRoot], so is a sibling of the library configuration
 * file.
 *
 * After each publication, the publication history file should be checked into version control in
 * the main branch so that the code repository has a complete, historical record of all
 * publications, and so that tools that can fetch the library's source also has a means to access
 * the historical record.
 *
 * It's owned by the publication tool and has the form:
 *
 *     {
 *         "schema": "iglu:dev.temperlang/publication-history-schema/1.0.0",  // Boiler-plate
 *         "library-name": "my-library",                                      // Temper library name
 *         "history": {                                                       // Boiler-plate
 *             "js": [                                                        // BackendId
 *                 {
 *                     "temper-version": "1.0.0",                             // Semver
 *                     "target-identifier": "my-library:1.0.1",               // Backend specific
 *                     "time-stamp": "2021-11-10T17:44:30Z"                   // ISO timestamp
 *                 },
 *                 ...                                                        // More records
 *             ],
 *             ...                                                            // More backends
 *         }
 *     }
 *
 * The `"schema"` property is per
 * https://snowplowanalytics.com/blog/2014/05/15/introducing-self-describing-jsons/#sdj
 *
 * The `"library-name"` property corresponds to [LibraryConfiguration.libraryName].
 *
 * The `"history"` value is an object whose keys are [lang.temper.name.BackendId.uniqueId]s.
 * For each backend, there is an array of *publication record entries* that the publication tool
 * prepends to.
 *
 * Each publication record entry has these keys:
 *
 * - `"temper-version"`: a [semantic version](https://semver.org/)
 * - `"target-identifier"`: a JSON value that is produced by and has a meaning to the backend
 *   identified by the backend id whose list the publication record is in.
 *   The example shows a simple string, but backend's may make use of arbitrarily detailed JSON.
 * - `"time-stamp"`: an ISO 8601 Date Time in UTC with 'Z' suffix specifying wall clock time when
 *   the publication happened.
 *
 * There may be one publication record entry whose `"time-stamp"` value is not an ISO 8601 Date Time
 * and is instead `null`.  This is the *local publication record*.  If installing locally for
 * testing then use this entry.  This is also how we associate a group of libraries with a name
 * we own but have yet to publish to so that we can publish a `v1.0.0` of multiple co-dependent
 * libraries together.
 */
object PublicationHistoryFile {
    val fileName = FilePathSegment("publication-history.json")
    val mimeType = MimeType.json

    const val KEY_SCHEMA = "schema"
    const val KEY_LIBRARY_NAME = "library-name"
    const val KEY_HISTORY = "history"
    const val KEY_TEMPER_VERSION = "temper-version"
    const val KEY_TARGET_IDENTIFIER = "target-identifier"
    const val KEY_TIME_STAMP = "time-stamp"
}
