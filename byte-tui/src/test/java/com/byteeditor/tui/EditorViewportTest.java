package com.byteeditor.tui;

import com.byteeditor.core.TextBuffer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EditorViewportTest {

    @Test
    void displayColumnCountsPlainCharactersOneForOne() {
        assertEquals(5, EditorViewport.displayColumn("hello", 5));
        assertEquals(0, EditorViewport.displayColumn("hello", 0));
    }

    @Test
    void displayColumnExpandsTabsToNextStopOfFour() {
        // "\t" at column 0 -> stop at 4. "a\t" -> 'a' at 0, tab expands 1->4.
        assertEquals(4, EditorViewport.displayColumn("\t", 1));
        assertEquals(4, EditorViewport.displayColumn("a\t", 2));
        // Two tabs from column 0: 0->4, 4->8.
        assertEquals(8, EditorViewport.displayColumn("\t\t", 2));
    }

    @Test
    void displayColumnDoesNotSplitSurrogatePairs() {
        String emoji = new String(Character.toChars(0x1F600));
        String line = "a" + emoji + "b";
        // 'a' -> display 1; emoji counts as ONE display column, not two,
        // even though it's two UTF-16 chars.
        assertEquals(1, EditorViewport.displayColumn(line, 1));
        assertEquals(2, EditorViewport.displayColumn(line, 1 + emoji.length()));
    }

    @Test
    void rawColumnForDisplayRoundTripsThroughDisplayColumn() {
        String line = "ab\tcd";
        for (int raw = 0; raw <= line.length(); raw++) {
            int display = EditorViewport.displayColumn(line, raw);
            int backToRaw = EditorViewport.rawColumnForDisplay(line, display);
            // Not always exactly equal (multiple raw columns can map to the
            // same display column across a tab stop isn't possible here, but
            // rounding at tab boundaries is), so assert the round-trip lands
            // on a raw column whose display column matches, which is the
            // actual contract mouse-click positioning depends on.
            assertEquals(display, EditorViewport.displayColumn(line, backToRaw));
        }
    }

    @Test
    void rawColumnForDisplayClampsBeyondEndOfLine() {
        assertEquals("short".length(), EditorViewport.rawColumnForDisplay("short", 999));
    }

    @Test
    void rawColumnForDisplayHandlesZeroAndNegative() {
        assertEquals(0, EditorViewport.rawColumnForDisplay("anything", 0));
        assertEquals(0, EditorViewport.rawColumnForDisplay("anything", -5));
    }

    @Test
    void expandTabsProducesSpacesAtCorrectStops() {
        assertEquals("    ", EditorViewport.expandTabs("\t"));
        assertEquals("a   b", EditorViewport.expandTabs("a\tb")); // 'a' at 0, tab 1->4, 'b' at 4
        assertEquals("hello", EditorViewport.expandTabs("hello"));
    }

    @Test
    void expandTabsPreservesAstralCharacters() {
        String emoji = new String(Character.toChars(0x1F600));
        assertEquals(emoji, EditorViewport.expandTabs(emoji));
    }

    @Test
    void scrollLinesClampsToValidRange() {
        EditorViewport viewport = new EditorViewport();
        viewport.scrollLines(-100, 50, 10); // can't scroll above line 0
        assertEquals(0, viewport.topLine());

        viewport.scrollLines(1000, 50, 10); // can't scroll past lineCount - height
        assertEquals(40, viewport.topLine());
    }

    @Test
    void scrollLinesHandlesContentShorterThanViewport() {
        EditorViewport viewport = new EditorViewport();
        viewport.scrollLines(5, 3, 10); // 3 lines fit entirely in a 10-row view
        assertEquals(0, viewport.topLine());
    }

    @Test
    void keepCursorVisibleScrollsDownWhenCursorBelowViewport() {
        EditorViewport viewport = new EditorViewport();
        TextBuffer buffer = new TextBuffer();
        for (int i = 0; i < 20; i++) {
            buffer.insertNewline();
        }
        buffer.setCursor(19, 0);

        viewport.keepCursorVisible(buffer, 80, 10);
        // Cursor at row 19 must be within [topLine, topLine + 10).
        assertEquals(10, viewport.topLine());
    }

    @Test
    void keepCursorVisibleScrollsUpWhenCursorAboveViewport() {
        EditorViewport viewport = new EditorViewport();
        TextBuffer buffer = new TextBuffer();
        for (int i = 0; i < 20; i++) {
            buffer.insertNewline();
        }
        buffer.setCursor(19, 0);
        viewport.keepCursorVisible(buffer, 80, 10); // scrolls down first

        buffer.setCursor(0, 0);
        viewport.keepCursorVisible(buffer, 80, 10);
        assertEquals(0, viewport.topLine());
    }

    @Test
    void keepCursorVisibleScrollsHorizontallyForLongLines() {
        EditorViewport viewport = new EditorViewport();
        TextBuffer buffer = TextBuffer.fromString("x".repeat(200));
        buffer.setCursor(0, 150);

        viewport.keepCursorVisible(buffer, 80, 10);
        // Cursor display column 150 must be within [leftColumn, leftColumn + 80).
        assertEquals(true, viewport.leftColumn() <= 150 && 150 < viewport.leftColumn() + 80);
    }

    @Test
    void keepCursorVisibleIgnoresNonPositiveDimensions() {
        EditorViewport viewport = new EditorViewport();
        TextBuffer buffer = TextBuffer.fromString("hello");
        buffer.setCursor(0, 3);
        viewport.keepCursorVisible(buffer, 0, 0); // must not throw or change state
        assertEquals(0, viewport.topLine());
        assertEquals(0, viewport.leftColumn());
    }

    @Test
    void resetReturnsToOrigin() {
        EditorViewport viewport = new EditorViewport();
        viewport.scrollLines(1000, 50, 10);
        viewport.reset();
        assertEquals(0, viewport.topLine());
        assertEquals(0, viewport.leftColumn());
    }
}
