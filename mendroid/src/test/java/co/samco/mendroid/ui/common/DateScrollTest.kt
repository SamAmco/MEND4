package co.samco.mendroid.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class DateScrollTest {
    @Test
    fun emptyAndSingleEntryListsStayAtZero() {
        assertEquals(0, dateScrollIndex(1f, 100f, 0))
        assertEquals(0, dateScrollIndex(1f, 100f, 1))
    }

    @Test
    fun draggingUsesTheScrollableRangeRatherThanTotalItemCount() {
        // Ten entries fit in the viewport of a 100-entry log.
        assertEquals(45, dateScrollIndex(0.5f, 90f, 100))
    }

    @Test
    fun bottomRevealsTheEntireLastEntry() {
        assertEquals(99, dateScrollIndex(1f, 90f, 100))
    }

    @Test
    fun draggingBeyondEitherEndStaysWithinTheList() {
        assertEquals(0, dateScrollIndex(-1f, 90f, 100))
        assertEquals(99, dateScrollIndex(2f, 90f, 100))
    }

    @Test
    fun shrinkingTheListClampsAnOldScrollRange() {
        assertEquals(9, dateScrollIndex(0.5f, 90f, 10))
    }
}
