package lang.temper.log

import lang.temper.common.Log
import kotlin.test.Test
import kotlin.test.assertTrue

class PositionFilterTest {
    @Test
    fun positionFilter() {
        val filter = PositionFilter()
        // Start with something
        assertTrue(filter.allow(Log.Warn, Position(filePath("something"), 10, 30)))
        // Overlaps but not exact
        assertTrue(filter.allow(Log.Warn, Position(filePath("something"), 29, 31)))
        // Exact match but higher level
        assertTrue(filter.allow(Log.Error, Position(filePath("something"), 29, 31)))
        // Repeats excluded
        assertTrue(!filter.allow(Log.Warn, Position(filePath("something"), 29, 31)))
        assertTrue(!filter.allow(Log.Error, Position(filePath("something"), 29, 31)))
        // Different file
        assertTrue(filter.allow(Log.Warn, Position(filePath("other"), 29, 31)))
    }
}
