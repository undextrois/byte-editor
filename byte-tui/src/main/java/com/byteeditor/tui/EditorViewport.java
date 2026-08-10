package com.byteeditor.tui;

import com.byteeditor.core.TextBuffer;

/** Tracks the visible editor window independently from the text buffer. */
final class EditorViewport {
    private int topLine;
    private int leftColumn;

    int topLine() {
        return topLine;
    }

    int leftColumn() {
        return leftColumn;
    }

    void reset() {
        topLine = 0;
        leftColumn = 0;
    }

    void scrollLines(int delta, int lineCount, int height) {
        int maxTop = Math.max(0, lineCount - Math.max(1, height));
        topLine = Math.max(0, Math.min(maxTop, topLine + delta));
    }

    void keepCursorVisible(TextBuffer buffer, int contentWidth, int height) {
        if (height <= 0 || contentWidth <= 0) {
            return;
        }

        int row = buffer.getCursorRow();
        if (row < topLine) {
            topLine = row;
        } else if (row >= topLine + height) {
            topLine = row - height + 1;
        }

        int displayColumn = displayColumn(buffer.getLine(row), buffer.getCursorCol());
        if (displayColumn < leftColumn) {
            leftColumn = displayColumn;
        } else if (displayColumn >= leftColumn + contentWidth) {
            leftColumn = displayColumn - contentWidth + 1;
        }
    }

    static int displayColumn(String line, int rawColumn) {
        int display = 0;
        int limit = Math.min(rawColumn, line.length());
        for (int i = 0; i < limit; ) {
            int cp = line.codePointAt(i);
            if (cp == '\t') {
                display += 4 - (display % 4);
            } else {
                display += 1;
            }
            i += Character.charCount(cp);
        }
        return display;
    }

    static int rawColumnForDisplay(String line, int targetDisplayColumn) {
        if (targetDisplayColumn <= 0) {
            return 0;
        }
        int display = 0;
        for (int i = 0; i < line.length(); ) {
            int cp = line.codePointAt(i);
            int width = cp == '\t' ? 4 - (display % 4) : 1;
            if (display + width > targetDisplayColumn) {
                return i;
            }
            display += width;
            i += Character.charCount(cp);
            if (display >= targetDisplayColumn) {
                return i;
            }
        }
        return line.length();
    }

    static String expandTabs(String line) {
        StringBuilder out = new StringBuilder(line.length());
        int display = 0;
        for (int i = 0; i < line.length(); ) {
            int cp = line.codePointAt(i);
            if (cp == '\t') {
                int spaces = 4 - (display % 4);
                out.append(" ".repeat(spaces));
                display += spaces;
            } else {
                out.appendCodePoint(cp);
                display++;
            }
            i += Character.charCount(cp);
        }
        return out.toString();
    }
}
