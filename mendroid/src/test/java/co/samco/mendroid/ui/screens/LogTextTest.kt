package co.samco.mendroid.ui.screens

import android.app.Application
import android.widget.Magnifier
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.SelectionState
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performFirstLinkClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import co.samco.mendroid.model.LogLine
import co.samco.mendroid.viewmodel.DecryptedLogViewModel.Companion.asViewData
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], shadows = [LogTextTest.MagnifierShadow::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LogTextTest {
    // Robolectric creates no magnifier Surface; dismissing its popup would throw after selection.
    @Implements(Magnifier::class)
    class MagnifierShadow {
        @Implementation fun dismiss() = Unit
    }

    @get:Rule val compose = createComposeRule()
    private val ids = listOf("57395938485923", "1235808528302530", "57395938485923")
    private val line = LogLine("ordinary words: ${ids.joinToString(",")}", null).asViewData(0)

    @Test fun onlyAttachmentIdsAreLinksWithExplicitReadableStyles() {
        val style = SpanStyle(color = Color.White, background = Color.Black, textDecoration = TextDecoration.None)
        val clicked = mutableListOf<String>()
        val text = annotatedLogText(line.text, style, clicked::add)
        assertEquals("ordinary words: ${ids.joinToString(",")}", text.text)
        val links = text.getLinkAnnotations(0, text.length)
        assertEquals(ids, links.map { text.text.substring(it.start, it.end) })
        assertTrue(links.all { it.start >= "ordinary words: ".length })
        assertTrue(links.all { it.item.styles?.style == style })
        links.forEach { it.item.linkInteractionListener!!.onClick(it.item) }
        assertEquals(ids, clicked)
    }

    @Test fun everyCommaSeparatedAttachmentIsClickableIncludingRepeatedIdsAndWrappedLinks() {
        val clicked = mutableListOf<String>()
        compose.setContent {
            MaterialTheme {
                SelectionContainer {
                    Box(Modifier.width(180.dp)) { LogText(line, clicked::add) }
                }
            }
        }
        val text = compose.onNodeWithText("ordinary words:", substring = true, useUnmergedTree = true)
        val links = annotatedLogText(line.text, SpanStyle(), {}).getLinkAnnotations(0, Int.MAX_VALUE)
        links.forEach { link -> text.performFirstLinkClick { it.start == link.start } }
        compose.runOnIdle { assertEquals(ids, clicked) }
    }

    @Test fun longPressSelectsOrdinaryTextInsideAClickableFilteredRow() {
        val selection = SelectionState()
        var rowClicked = false
        val clicked = mutableListOf<String>()
        compose.setContent {
            MaterialTheme {
                SelectionContainer(state = selection) {
                    Box(Modifier.width(220.dp).clickable { rowClicked = true }) { LogText(line, clicked::add) }
                }
            }
        }
        val text = compose.onNodeWithText("ordinary words:", substring = true, useUnmergedTree = true)
        val results = mutableListOf<TextLayoutResult>()
        text.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        text.performTouchInput { longClick(results.single().getBoundingBox(2).center) }
        compose.runOnIdle {
            assertEquals("ordinary", selection.selectedTexts.single().text)
            assertFalse(rowClicked)
            assertTrue(clicked.isEmpty())
        }
    }
}
