package com.byteeditor.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextBufferTest {

    @Test
    void newBufferHasSingleEmptyLine() {
        TextBuffer buf = new TextBuffer();
        assertEquals(1, buf.getLineCount());
        assertEquals("", buf.getLine(0));
        assertFalse(buf.isDirty());
    }

    @Test
    void insertSingleCharacter() {
        TextBuffer buf = new TextBuffer();
        buf.insertChar('A');
        assertEquals("A", buf.getLine(0));
        assertEquals(0, buf.getCursorRow());
        assertEquals(1, buf.getCursorCol());
        assertTrue(buf.isDirty());
    }

    @Test
    void insertBuildsUpAWord() {
        TextBuffer buf = new TextBuffer();
        for (char c : "Byte".toCharArray()) {
            buf.insertChar(c);
        }
        assertEquals("Byte", buf.getLine(0));
        assertEquals(4, buf.getCursorCol());
    }

    @Test
    void insertNewlineSplitsLine() {
        TextBuffer buf = TextBuffer.fromString("HelloWorld");
        buf.setCursor(0, 5);
        buf.insertNewline();
        assertEquals(2, buf.getLineCount());
        assertEquals("Hello", buf.getLine(0));
        assertEquals("World", buf.getLine(1));
        assertEquals(1, buf.getCursorRow());
        assertEquals(0, buf.getCursorCol());
    }

    @Test
    void backspaceAtStartOfLineMergesWithPrevious() {
        TextBuffer buf = TextBuffer.fromString("Hello\nWorld");
        buf.setCursor(1, 0);
        buf.backspace();
        assertEquals(1, buf.getLineCount());
        assertEquals("HelloWorld", buf.getLine(0));
        assertEquals(0, buf.getCursorRow());
        assertEquals(5, buf.getCursorCol());
    }

    @Test
    void backspaceOnEmptyBufferAtOriginDoesNothing() {
        TextBuffer buf = new TextBuffer();
        buf.backspace();
        assertEquals(1, buf.getLineCount());
        assertEquals("", buf.getLine(0));
        assertEquals(0, buf.getCursorRow());
        assertEquals(0, buf.getCursorCol());
    }

    @Test
    void deleteForwardAtEndOfLineMergesWithNext() {
        TextBuffer buf = TextBuffer.fromString("Hello\nWorld");
        buf.setCursor(0, 5);
        buf.deleteForward();
        assertEquals(1, buf.getLineCount());
        assertEquals("HelloWorld", buf.getLine(0));
        assertEquals(0, buf.getCursorRow());
        assertEquals(5, buf.getCursorCol());
    }

    @Test
    void deleteForwardAtVeryEndDoesNothing() {
        TextBuffer buf = TextBuffer.fromString("End");
        buf.setCursor(0, 3);
        buf.deleteForward();
        assertEquals("End", buf.getLine(0));
    }

    @Test
    void undoReversesInsert() {
        TextBuffer buf = new TextBuffer();
        buf.insertChar('X');
        assertEquals("X", buf.getLine(0));
        buf.undo();
        assertEquals("", buf.getLine(0));
        assertEquals(0, buf.getCursorCol());
        assertFalse(buf.canUndo());
    }

    @Test
    void redoReappliesUndoneEdit() {
        TextBuffer buf = new TextBuffer();
        buf.insertChar('X');
        buf.undo();
        buf.redo();
        assertEquals("X", buf.getLine(0));
        assertEquals(1, buf.getCursorCol());
    }

    @Test
    void newEditAfterUndoClearsRedoStack() {
        TextBuffer buf = new TextBuffer();
        buf.insertChar('A');
        buf.undo();
        buf.insertChar('B');
        assertFalse(buf.canRedo());
        assertEquals("B", buf.getLine(0));
    }

    @Test
    void undoAcrossLineSplitAndMergeRoundTrips() {
        TextBuffer buf = TextBuffer.fromString("HelloWorld");
        buf.setCursor(0, 5);
        buf.insertNewline();
        assertEquals(2, buf.getLineCount());

        buf.undo();
        assertEquals(1, buf.getLineCount());
        assertEquals("HelloWorld", buf.getLine(0));
        assertEquals(0, buf.getCursorRow());
        assertEquals(5, buf.getCursorCol());
    }

    @Test
    void multipleUndoRedoCyclesPreserveHistory() {
        TextBuffer buf = new TextBuffer();
        buf.insertChar('A');
        buf.insertChar('B');
        buf.insertChar('C');
        assertEquals("ABC", buf.getLine(0));

        buf.undo();
        buf.undo();
        assertEquals("A", buf.getLine(0));

        buf.redo();
        assertEquals("AB", buf.getLine(0));

        buf.insertChar('Z');
        assertEquals("ABZ", buf.getLine(0));
        assertFalse(buf.canRedo());
    }

    @Test
    void insertTextPastesMultilineBlockAsOneUndoStep() {
        TextBuffer buf = TextBuffer.fromString("headtail");
        buf.setCursor(0, 4);
        buf.insertText("A\nB\nC");

        assertEquals(3, buf.getLineCount());
        assertEquals("headA", buf.getLine(0));
        assertEquals("B", buf.getLine(1));
        assertEquals("Ctail", buf.getLine(2));
        assertEquals(2, buf.getCursorRow());
        assertEquals(1, buf.getCursorCol());

        buf.undo();
        assertEquals(1, buf.getLineCount());
        assertEquals("headtail", buf.getLine(0));
    }

    @Test
    void insertTextHandlesHugePasteEfficientlyAndCorrectly() {
        TextBuffer buf = new TextBuffer();
        StringBuilder sb = new StringBuilder();
        int lineCountExpected = 5000;
        for (int i = 0; i < lineCountExpected; i++) {
            sb.append("line-").append(i);
            if (i < lineCountExpected - 1) {
                sb.append('\n');
            }
        }
        buf.insertText(sb.toString());

        assertEquals(lineCountExpected, buf.getLineCount());
        assertEquals("line-0", buf.getLine(0));
        assertEquals("line-4999", buf.getLine(4999));

        buf.undo();
        assertEquals(1, buf.getLineCount());
        assertEquals("", buf.getLine(0));
    }

    @Test
    void insertTextNormalizesCrlfAndLoneCr() {
        TextBuffer buf = new TextBuffer();
        buf.insertText("a\r\nb\rc");
        assertEquals(3, buf.getLineCount());
        assertEquals("a", buf.getLine(0));
        assertEquals("b", buf.getLine(1));
        assertEquals("c", buf.getLine(2));
    }

    @Test
    void cursorMovementNeverSplitsSurrogatePair() {
        // U+1F600 GRINNING FACE requires a surrogate pair in UTF-16.
        String emoji = new String(Character.toChars(0x1F600));
        TextBuffer buf = TextBuffer.fromString("a" + emoji + "b");

        buf.setCursor(0, 0);
        buf.moveRight(); // past 'a'
        assertEquals(1, buf.getCursorCol());

        buf.moveRight(); // past the full emoji surrogate pair, not just half
        assertEquals(1 + emoji.length(), buf.getCursorCol());

        buf.moveLeft(); // back over the full emoji
        assertEquals(1, buf.getCursorCol());
    }

    @Test
    void insertCharAcceptsFullCodePointForAstralCharacters() {
        TextBuffer buf = new TextBuffer();
        int codePoint = 0x1F600; // outside the BMP, needs 2 UTF-16 chars
        buf.insertChar(codePoint);

        String expected = new String(Character.toChars(codePoint));
        assertEquals(expected, buf.getLine(0));
        assertEquals(expected.length(), buf.getCursorCol());
    }

    @Test
    void backspaceDoesNotSplitSurrogatePair() {
        String emoji = new String(Character.toChars(0x1F600));
        TextBuffer buf = TextBuffer.fromString(emoji);
        buf.setCursor(0, emoji.length());
        buf.backspace();
        assertEquals("", buf.getLine(0));
    }

    @Test
    void deleteForwardDoesNotSplitSurrogatePair() {
        String emoji = new String(Character.toChars(0x1F600));
        TextBuffer buf = TextBuffer.fromString(emoji + "x");
        buf.setCursor(0, 0);
        buf.deleteForward();
        assertEquals("x", buf.getLine(0));
    }

    @Test
    void getTextRoundTripsThroughMultipleLines() {
        String original = "line one\nline two\nline three";
        TextBuffer buf = TextBuffer.fromString(original);
        assertEquals(original, buf.getText());
    }

    @Test
    void emptyFileLoadsAsSingleEmptyLine() {
        TextBuffer buf = TextBuffer.fromString("");
        assertEquals(1, buf.getLineCount());
        assertEquals("", buf.getLine(0));
    }

    @Test
    void cursorClampsToValidRangeOnSetCursor() {
        TextBuffer buf = TextBuffer.fromString("short\nlonger line here");
        buf.setCursor(0, 999);
        assertEquals(5, buf.getCursorCol());

        buf.setCursor(999, 0);
        assertEquals(1, buf.getCursorRow());

        buf.setCursor(-5, -5);
        assertEquals(0, buf.getCursorRow());
        assertEquals(0, buf.getCursorCol());
    }

    @Test
    void moveUpAndDownClampColumnToShorterLines() {
        TextBuffer buf = TextBuffer.fromString("short\na very long line of text");
        buf.setCursor(1, 20);
        buf.moveUp();
        assertEquals(0, buf.getCursorRow());
        assertEquals(5, buf.getCursorCol()); // clamped to "short".length()
    }

    @Test
    void saveClearsDirtyFlag() throws Exception {
        java.nio.file.Path tmp = java.nio.file.Files.createTempFile("byte-test", ".txt");
        try {
            TextBuffer buf = new TextBuffer();
            buf.insertChar('X');
            assertTrue(buf.isDirty());

            buf.saveToFile(tmp);
            assertFalse(buf.isDirty());
            assertEquals("X", java.nio.file.Files.readString(tmp));
        } finally {
            java.nio.file.Files.deleteIfExists(tmp);
        }
    }

    // ------------------------------------------------------------------
    // Vertical cursor movement must not split a UTF-16 surrogate pair.
    //
    // Horizontal movement/insert/delete were already covered above, but
    // moveUp/moveDown/setCursor had a separate bug: moving vertically
    // between lines of different lengths could clamp the cursor to a
    // column that lands in the middle of a surrogate pair on the
    // destination line, since the target column comes from the *previous*
    // line's position, not the destination line's own code points. Fixed
    // via the private safeUtf16Column helper; tested here through the
    // public API it backs.
    // ------------------------------------------------------------------

    @Test
    void moveDownLandsBeforeSurrogatePairInsteadOfSplittingIt() {
        String emoji = new String(Character.toChars(0x1F600));
        TextBuffer buf = TextBuffer.fromString("xx\n" + "a" + emoji + "b");
        buf.setCursor(0, 2); // "xx", column 2 = end of line, valid position
        buf.moveDown(); // line 1 = "a"+emoji+"b"; column 2 would split the pair
        assertEquals(1, buf.getCursorRow());
        assertEquals(1, buf.getCursorCol()); // backed up to before the pair
    }

    @Test
    void moveUpLandsBeforeSurrogatePairInsteadOfSplittingIt() {
        String emoji = new String(Character.toChars(0x1F600));
        TextBuffer buf = TextBuffer.fromString("a" + emoji + "b\nxx");
        buf.setCursor(1, 2); // "xx", column 2 = end of line, valid position
        buf.moveUp(); // line 0 = "a"+emoji+"b"; column 2 would split the pair
        assertEquals(0, buf.getCursorRow());
        assertEquals(1, buf.getCursorCol());
    }

    @Test
    void setCursorDirectlyAvoidsSplittingASurrogatePair() {
        String emoji = new String(Character.toChars(0x1F600));
        TextBuffer buf = TextBuffer.fromString("a" + emoji + "b");
        buf.setCursor(0, 2); // would land between the pair's two chars
        assertEquals(1, buf.getCursorCol());
    }

    @Test
    void setCursorAtSafePositionsAroundSurrogatePairIsUnaffected() {
        String emoji = new String(Character.toChars(0x1F600));
        TextBuffer buf = TextBuffer.fromString("a" + emoji + "b"); // length 4
        buf.setCursor(0, 0);
        assertEquals(0, buf.getCursorCol());
        buf.setCursor(0, 1); // right before the pair
        assertEquals(1, buf.getCursorCol());
        buf.setCursor(0, 3); // right after the pair
        assertEquals(3, buf.getCursorCol());
        buf.setCursor(0, 4); // end of line
        assertEquals(4, buf.getCursorCol());
    }

    @Test
    void pageDownAvoidsSplittingSurrogatePairViaSetCursor() {
        // pageUp/pageDown both route through setCursor, so this exercises
        // the same fix through a different public entry point.
        String emoji = new String(Character.toChars(0x1F600));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5; i++) sb.append("xx\n");
        sb.append("a").append(emoji).append("b");
        TextBuffer buf = TextBuffer.fromString(sb.toString());
        buf.setCursor(0, 2);
        buf.pageDown(5); // lands on the emoji line at column 2 -> splits pair
        assertEquals(5, buf.getCursorRow());
        assertEquals(1, buf.getCursorCol());
    }
}
