package com.byteeditor.tui;

import com.byteeditor.core.TextBuffer;

import java.util.Objects;

/**
 * Dependency-free companion to {@link EditorViewportTest}. Not part of the
 * shipped build; delete once {@code mvn test} is runnable against your
 * Nexus mirror and the real JUnit suite covers this instead.
 */
public final class EditorViewportManualCheck {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        displayColumnCountsPlainCharactersOneForOne();
        displayColumnExpandsTabsToNextStopOfFour();
        displayColumnDoesNotSplitSurrogatePairs();
        rawColumnForDisplayRoundTripsThroughDisplayColumn();
        rawColumnForDisplayClampsBeyondEndOfLine();
        rawColumnForDisplayHandlesZeroAndNegative();
        expandTabsProducesSpacesAtCorrectStops();
        expandTabsPreservesAstralCharacters();
        scrollLinesClampsToValidRange();
        scrollLinesHandlesContentShorterThanViewport();
        keepCursorVisibleScrollsDownWhenCursorBelowViewport();
        keepCursorVisibleScrollsUpWhenCursorAboveViewport();
        keepCursorVisibleScrollsHorizontallyForLongLines();
        keepCursorVisibleIgnoresNonPositiveDimensions();
        resetReturnsToOrigin();

        System.out.println();
        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void check(String name, boolean condition) {
        if (condition) {
            passed++;
            System.out.println("  [PASS] " + name);
        } else {
            failed++;
            System.out.println("  [FAIL] " + name);
        }
    }

    private static void eq(String name, Object expected, Object actual) {
        check(name + " (expected=" + expected + ", actual=" + actual + ")", Objects.equals(expected, actual));
    }

    static void displayColumnCountsPlainCharactersOneForOne() {
        eq("display.plain5", 5, EditorViewport.displayColumn("hello", 5));
        eq("display.plain0", 0, EditorViewport.displayColumn("hello", 0));
    }

    static void displayColumnExpandsTabsToNextStopOfFour() {
        eq("display.tabAlone", 4, EditorViewport.displayColumn("\t", 1));
        eq("display.aThenTab", 4, EditorViewport.displayColumn("a\t", 2));
        eq("display.twoTabs", 8, EditorViewport.displayColumn("\t\t", 2));
    }

    static void displayColumnDoesNotSplitSurrogatePairs() {
        String emoji = new String(Character.toChars(0x1F600));
        String line = "a" + emoji + "b";
        eq("display.beforeEmoji", 1, EditorViewport.displayColumn(line, 1));
        eq("display.afterEmoji", 2, EditorViewport.displayColumn(line, 1 + emoji.length()));
    }

    static void rawColumnForDisplayRoundTripsThroughDisplayColumn() {
        String line = "ab\tcd";
        boolean allMatch = true;
        for (int raw = 0; raw <= line.length(); raw++) {
            int display = EditorViewport.displayColumn(line, raw);
            int backToRaw = EditorViewport.rawColumnForDisplay(line, display);
            if (EditorViewport.displayColumn(line, backToRaw) != display) {
                allMatch = false;
            }
        }
        check("rawColumn.roundTrip", allMatch);
    }

    static void rawColumnForDisplayClampsBeyondEndOfLine() {
        eq("rawColumn.clampEnd", "short".length(), EditorViewport.rawColumnForDisplay("short", 999));
    }

    static void rawColumnForDisplayHandlesZeroAndNegative() {
        eq("rawColumn.zero", 0, EditorViewport.rawColumnForDisplay("anything", 0));
        eq("rawColumn.negative", 0, EditorViewport.rawColumnForDisplay("anything", -5));
    }

    static void expandTabsProducesSpacesAtCorrectStops() {
        eq("expandTabs.single", "    ", EditorViewport.expandTabs("\t"));
        eq("expandTabs.aTabB", "a   b", EditorViewport.expandTabs("a\tb"));
        eq("expandTabs.plain", "hello", EditorViewport.expandTabs("hello"));
    }

    static void expandTabsPreservesAstralCharacters() {
        String emoji = new String(Character.toChars(0x1F600));
        eq("expandTabs.astral", emoji, EditorViewport.expandTabs(emoji));
    }

    static void scrollLinesClampsToValidRange() {
        EditorViewport viewport = new EditorViewport();
        viewport.scrollLines(-100, 50, 10);
        eq("scroll.clampLow", 0, viewport.topLine());

        viewport.scrollLines(1000, 50, 10);
        eq("scroll.clampHigh", 40, viewport.topLine());
    }

    static void scrollLinesHandlesContentShorterThanViewport() {
        EditorViewport viewport = new EditorViewport();
        viewport.scrollLines(5, 3, 10);
        eq("scroll.shortContent", 0, viewport.topLine());
    }

    static void keepCursorVisibleScrollsDownWhenCursorBelowViewport() {
        EditorViewport viewport = new EditorViewport();
        TextBuffer buffer = new TextBuffer();
        for (int i = 0; i < 20; i++) buffer.insertNewline();
        buffer.setCursor(19, 0);
        viewport.keepCursorVisible(buffer, 80, 10);
        eq("keepVisible.scrollDown", 10, viewport.topLine());
    }

    static void keepCursorVisibleScrollsUpWhenCursorAboveViewport() {
        EditorViewport viewport = new EditorViewport();
        TextBuffer buffer = new TextBuffer();
        for (int i = 0; i < 20; i++) buffer.insertNewline();
        buffer.setCursor(19, 0);
        viewport.keepCursorVisible(buffer, 80, 10);

        buffer.setCursor(0, 0);
        viewport.keepCursorVisible(buffer, 80, 10);
        eq("keepVisible.scrollUp", 0, viewport.topLine());
    }

    static void keepCursorVisibleScrollsHorizontallyForLongLines() {
        EditorViewport viewport = new EditorViewport();
        TextBuffer buffer = TextBuffer.fromString("x".repeat(200));
        buffer.setCursor(0, 150);
        viewport.keepCursorVisible(buffer, 80, 10);
        check("keepVisible.horizontal", viewport.leftColumn() <= 150 && 150 < viewport.leftColumn() + 80);
    }

    static void keepCursorVisibleIgnoresNonPositiveDimensions() {
        EditorViewport viewport = new EditorViewport();
        TextBuffer buffer = TextBuffer.fromString("hello");
        buffer.setCursor(0, 3);
        viewport.keepCursorVisible(buffer, 0, 0);
        eq("keepVisible.ignoreZeroTop", 0, viewport.topLine());
        eq("keepVisible.ignoreZeroLeft", 0, viewport.leftColumn());
    }

    static void resetReturnsToOrigin() {
        EditorViewport viewport = new EditorViewport();
        viewport.scrollLines(1000, 50, 10);
        viewport.reset();
        eq("reset.top", 0, viewport.topLine());
        eq("reset.left", 0, viewport.leftColumn());
    }
}
