package co.samco.mendroid.viewmodel

import co.samco.mendroid.model.LogLine
import co.samco.mendroid.viewmodel.DecryptedLogViewModel.Companion.asViewData
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class SplitLogTest {

    @Test
    fun scrollDatePreservesTheEntryDateAndOriginalIndex() {
        val dateTime = LocalDateTime.of(2024, 2, 29, 23, 59)
        val viewData = LogLine("hello", dateTime).asViewData(123)
        assertEquals(dateTime.toLocalDate(), viewData.date)
        assertEquals(123, viewData.index)
        assertEquals(null, LogLine("undated", null).asViewData(0).date)
    }


    @Test
    fun testSplitLogText() {
        assertEquals(
            listOf(
                TextPart("hello ", TextType.PLAIN),
                TextPart("57395938485923", TextType.FILE_ID),
                TextPart(" world ", TextType.PLAIN),
                TextPart("1235808528302530", TextType.FILE_ID),
                TextPart(" it's me", TextType.PLAIN)
            ),
            LogLine(
                text = "hello 57395938485923 world 1235808528302530 it's me",
                dateTime = null
            ).asViewData(0).text
        )
    }

    @Test
    fun testSplitLogTextNoIds() {
        assertEquals(
            listOf(
                TextPart("hello world it's me", TextType.PLAIN)
            ),
            LogLine(
                text = "hello world it's me",
                dateTime = null
            ).asViewData(0).text
        )
    }

    @Test
    fun testSplitLogIdAtStart() {
        assertEquals(
            listOf(
                TextPart("57395938485923", TextType.FILE_ID),
                TextPart(" world it's me", TextType.PLAIN)
            ),
            LogLine(
                text = "57395938485923 world it's me",
                dateTime = null
            ).asViewData(0).text
        )
    }

    @Test
    fun testSplitLogIdAtEnd() {
        assertEquals(
            listOf(
                TextPart("hello world ", TextType.PLAIN),
                TextPart("57395938485923", TextType.FILE_ID)
            ),
            LogLine(
                text = "hello world 57395938485923",
                dateTime = null
            ).asViewData(0).text
        )
    }

}