package com.byteeditor.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * An independent, UI-agnostic text buffer for Byte.
 *
 * <p>This class is the single source of truth for document content and cursor
 * position. It knows nothing about Lanterna, terminals, or rendering — it can
 * be unit tested in complete isolation and swapped under a different TUI
 * layer without change.
 *
 * <p>Lines are stored as plain {@link String}s (UTF-16). All cursor movement
 * and character-level edits are code-point aware so that surrogate pairs
 * (e.g. emoji, characters outside the Basic Multilingual Plane) are never
 * split in half by an edit or a cursor step.
 *
 * <p>Undo/redo is implemented as line-range snapshots rather than per-character
 * diffing: every edit records the contiguous slice of lines it replaced and
 * the slice it produced, plus cursor position before/after. This is simple to
 * reason about and correct by construction; it is intentionally not
 * memory-optimal for pathological cases (e.g. a single line many megabytes
 * long) — acceptable for v0.1's target of ordinary source files.
 */
public final class TextBuffer {

    /** Hard cap on undo history depth to bound memory use. */
    private static final int MAX_UNDO_DEPTH = 2000;

    private final List<String> lines = new ArrayList<>();
    private int cursorRow;
    private int cursorCol;

    private final Deque<EditSnapshot> undoStack = new ArrayDeque<>();
    private final Deque<EditSnapshot> redoStack = new ArrayDeque<>();

    private boolean dirty = false;
    private Path sourcePath;

    public TextBuffer() {
        lines.add("");
    }

    // ------------------------------------------------------------------
    // Construction / persistence
    // ------------------------------------------------------------------

    public static TextBuffer fromString(String content) {
        TextBuffer buf = new TextBuffer();
        buf.loadContent(content);
        return buf;
    }

    public static TextBuffer fromFile(Path path) throws IOException {
        TextBuffer buf = new TextBuffer();
        String content = Files.readString(path, StandardCharsets.UTF_8);
        buf.loadContent(content);
        buf.sourcePath = path;
        buf.dirty = false;
        return buf;
    }

    private void loadContent(String content) {
        lines.clear();
        // Normalize CRLF / lone CR to LF for internal storage.
        String normalized = content.replace("\r\n", "\n").replace("\r", "\n");
        if (normalized.isEmpty()) {
            lines.add("");
        } else {
            String[] split = normalized.split("\n", -1);
            Collections.addAll(lines, split);
        }
        cursorRow = 0;
        cursorCol = 0;
        undoStack.clear();
        redoStack.clear();
    }

    public void saveToFile(Path path) throws IOException {
        Files.writeString(path, getText(), StandardCharsets.UTF_8);
        this.sourcePath = path;
        this.dirty = false;
    }

    public void markSaved() {
        this.dirty = false;
    }

    public boolean isDirty() {
        return dirty;
    }

    public Path getSourcePath() {
        return sourcePath;
    }

    public String getText() {
        return String.join("\n", lines);
    }

    // ------------------------------------------------------------------
    // Read access
    // ------------------------------------------------------------------

    public int getLineCount() {
        return lines.size();
    }

    public String getLine(int row) {
        return lines.get(row);
    }

    public int getCursorRow() {
        return cursorRow;
    }

    public int getCursorCol() {
        return cursorCol;
    }

    // ------------------------------------------------------------------
    // Cursor movement (code-point aware)
    // ------------------------------------------------------------------

    public void setCursor(int row, int col) {
        cursorRow = clamp(row, 0, lines.size() - 1);
        cursorCol = safeUtf16Column(lines.get(cursorRow), col);
    }

    public void moveLeft() {
        String line = lines.get(cursorRow);
        if (cursorCol > 0) {
            cursorCol -= Character.charCount(line.codePointBefore(cursorCol));
        } else if (cursorRow > 0) {
            cursorRow--;
            cursorCol = lines.get(cursorRow).length();
        }
    }

    public void moveRight() {
        String line = lines.get(cursorRow);
        if (cursorCol < line.length()) {
            cursorCol += Character.charCount(line.codePointAt(cursorCol));
        } else if (cursorRow < lines.size() - 1) {
            cursorRow++;
            cursorCol = 0;
        }
    }

    public void moveUp() {
        if (cursorRow > 0) {
            cursorRow--;
            cursorCol = safeUtf16Column(lines.get(cursorRow), cursorCol);
        } else {
            cursorCol = 0;
        }
    }

    public void moveDown() {
        if (cursorRow < lines.size() - 1) {
            cursorRow++;
            cursorCol = safeUtf16Column(lines.get(cursorRow), cursorCol);
        } else {
            cursorCol = lines.get(cursorRow).length();
        }
    }

    public void moveHome() {
        cursorCol = 0;
    }

    public void moveEnd() {
        cursorCol = lines.get(cursorRow).length();
    }

    public void pageUp(int pageSize) {
        setCursor(cursorRow - pageSize, cursorCol);
    }

    public void pageDown(int pageSize) {
        setCursor(cursorRow + pageSize, cursorCol);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static int safeUtf16Column(String line, int requested) {
        int col = clamp(requested, 0, line.length());
        if (col > 0 && col < line.length()
                && Character.isHighSurrogate(line.charAt(col - 1))
                && Character.isLowSurrogate(line.charAt(col))) {
            col--;
        }
        return col;
    }

    // ------------------------------------------------------------------
    // Edits
    // ------------------------------------------------------------------

    /** Inserts a single Unicode code point at the cursor. Routes newline to {@link #insertNewline()}. */
    public void insertChar(int codePoint) {
        if (codePoint == '\n') {
            insertNewline();
            return;
        }
        String before = lines.get(cursorRow);
        String inserted = new String(Character.toChars(codePoint));
        String after = before.substring(0, cursorCol) + inserted + before.substring(cursorCol);

        int newCol = cursorCol + inserted.length();
        applyLineRangeEdit(
                cursorRow, 1, List.of(before), List.of(after),
                cursorRow, cursorCol,
                cursorRow, newCol);
    }

    /**
     * Inserts arbitrary text (e.g. a clipboard paste) at the cursor. Handles
     * embedded newlines by splitting into multiple resulting lines, and
     * normalizes CRLF/CR. This is a single atomic, single undo-step edit
     * regardless of how large the pasted text is.
     */
    public void insertText(String text) {
        if (text.isEmpty()) {
            return;
        }
        String normalized = text.replace("\r\n", "\n").replace("\r", "\n");
        String before = lines.get(cursorRow);
        String head = before.substring(0, cursorCol);
        String tail = before.substring(cursorCol);

        String[] pieces = normalized.split("\n", -1);
        List<String> after = new ArrayList<>(pieces.length);
        if (pieces.length == 1) {
            after.add(head + pieces[0] + tail);
        } else {
            after.add(head + pieces[0]);
            for (int i = 1; i < pieces.length - 1; i++) {
                after.add(pieces[i]);
            }
            after.add(pieces[pieces.length - 1] + tail);
        }

        int newRow = cursorRow + pieces.length - 1;
        int newCol = pieces.length == 1
                ? cursorCol + pieces[0].length()
                : pieces[pieces.length - 1].length();

        applyLineRangeEdit(
                cursorRow, 1, List.of(before), after,
                cursorRow, cursorCol,
                newRow, newCol);
    }

    /** Splits the current line at the cursor (Enter key). */
    public void insertNewline() {
        String before = lines.get(cursorRow);
        String head = before.substring(0, cursorCol);
        String tail = before.substring(cursorCol);

        applyLineRangeEdit(
                cursorRow, 1, List.of(before), List.of(head, tail),
                cursorRow, cursorCol,
                cursorRow + 1, 0);
    }

    /** Deletes the code point before the cursor (Backspace). May merge with the previous line. */
    public void backspace() {
        if (cursorCol > 0) {
            String before = lines.get(cursorRow);
            int prevCharCount = Character.charCount(before.codePointBefore(cursorCol));
            int deleteFrom = cursorCol - prevCharCount;
            String after = before.substring(0, deleteFrom) + before.substring(cursorCol);

            applyLineRangeEdit(
                    cursorRow, 1, List.of(before), List.of(after),
                    cursorRow, cursorCol,
                    cursorRow, deleteFrom);
        } else if (cursorRow > 0) {
            String prevLine = lines.get(cursorRow - 1);
            String curLine = lines.get(cursorRow);
            String merged = prevLine + curLine;
            int mergeCol = prevLine.length();

            applyLineRangeEdit(
                    cursorRow - 1, 2, List.of(prevLine, curLine), List.of(merged),
                    cursorRow, cursorCol,
                    cursorRow - 1, mergeCol);
        }
        // else: at (0,0), nothing to do.
    }

    /** Deletes the code point at the cursor (Delete key). May merge with the next line. */
    public void deleteForward() {
        String line = lines.get(cursorRow);
        if (cursorCol < line.length()) {
            int charCount = Character.charCount(line.codePointAt(cursorCol));
            String after = line.substring(0, cursorCol) + line.substring(cursorCol + charCount);

            applyLineRangeEdit(
                    cursorRow, 1, List.of(line), List.of(after),
                    cursorRow, cursorCol,
                    cursorRow, cursorCol);
        } else if (cursorRow < lines.size() - 1) {
            String nextLine = lines.get(cursorRow + 1);
            String merged = line + nextLine;

            applyLineRangeEdit(
                    cursorRow, 2, List.of(line, nextLine), List.of(merged),
                    cursorRow, cursorCol,
                    cursorRow, cursorCol);
        }
        // else: at end of last line, nothing to do.
    }

    // ------------------------------------------------------------------
    // Undo / redo
    // ------------------------------------------------------------------

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    public void undo() {
        if (undoStack.isEmpty()) {
            return;
        }
        EditSnapshot snap = undoStack.pop();
        replaceRange(snap.startLine, snap.after.size(), snap.before);
        cursorRow = snap.cursorRowBefore;
        cursorCol = snap.cursorColBefore;
        redoStack.push(snap);
        dirty = true;
    }

    public void redo() {
        if (redoStack.isEmpty()) {
            return;
        }
        EditSnapshot snap = redoStack.pop();
        replaceRange(snap.startLine, snap.before.size(), snap.after);
        cursorRow = snap.cursorRowAfter;
        cursorCol = snap.cursorColAfter;
        undoStack.push(snap);
        dirty = true;
    }

    private void applyLineRangeEdit(
            int startLine, int oldCount, List<String> before, List<String> after,
            int cursorRowBefore, int cursorColBefore,
            int cursorRowAfter, int cursorColAfter) {

        replaceRange(startLine, oldCount, after);

        EditSnapshot snap = new EditSnapshot();
        snap.startLine = startLine;
        snap.before = before;
        snap.after = after;
        snap.cursorRowBefore = cursorRowBefore;
        snap.cursorColBefore = cursorColBefore;
        snap.cursorRowAfter = cursorRowAfter;
        snap.cursorColAfter = cursorColAfter;

        undoStack.push(snap);
        if (undoStack.size() > MAX_UNDO_DEPTH) {
            undoStack.removeLast();
        }
        redoStack.clear();

        cursorRow = cursorRowAfter;
        cursorCol = cursorColAfter;
        dirty = true;
    }

    private void replaceRange(int startLine, int countToRemove, List<String> replacement) {
        for (int i = 0; i < countToRemove; i++) {
            lines.remove(startLine);
        }
        lines.addAll(startLine, replacement);
    }

    /** Immutable record of one atomic edit, sufficient to reverse or reapply it. */
    private static final class EditSnapshot {
        int startLine;
        List<String> before;
        List<String> after;
        int cursorRowBefore;
        int cursorColBefore;
        int cursorRowAfter;
        int cursorColAfter;
    }
}
