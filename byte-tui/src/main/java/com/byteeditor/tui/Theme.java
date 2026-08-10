package com.byteeditor.tui;

import com.googlecode.lanterna.TextColor;

/**
 * The Midnight-Commander-inspired palette: deep blue background, bright cyan
 * borders, yellow accents, high-contrast text. Uses explicit RGB values
 * (rather than the 8-color ANSI enum) to reproduce the classic CGA/EGA
 * "blue screen" look consistently across terminal emulators, since ANSI's
 * named colors are frequently remapped by the user's terminal theme and
 * would otherwise wash the retro aesthetic out entirely.
 */
final class Theme {

    private Theme() {
    }

    static final TextColor BACKGROUND = new TextColor.RGB(0, 0, 170);
    static final TextColor BORDER = new TextColor.RGB(85, 255, 255);
    static final TextColor TEXT = new TextColor.RGB(255, 255, 255);
    static final TextColor ACCENT = new TextColor.RGB(255, 255, 85);
    static final TextColor DIM_TEXT = new TextColor.RGB(85, 85, 255);
    static final TextColor OUTPUT_TEXT = new TextColor.RGB(85, 255, 85);
    static final TextColor ERROR_TEXT = new TextColor.RGB(255, 85, 85);

    static final TextColor FKEY_NUM_BG = new TextColor.RGB(85, 255, 255);
    static final TextColor FKEY_NUM_FG = new TextColor.RGB(0, 0, 0);
    static final TextColor FKEY_LABEL_BG = new TextColor.RGB(170, 170, 170);
    static final TextColor FKEY_LABEL_FG = new TextColor.RGB(0, 0, 0);
    static final TextColor FKEY_LABEL_BG_DISABLED = new TextColor.RGB(0, 0, 170);
    static final TextColor FKEY_LABEL_FG_DISABLED = new TextColor.RGB(85, 85, 255);

    // Single-line box-drawing glyphs.
    static final char TOP_LEFT = '┌';
    static final char TOP_RIGHT = '┐';
    static final char BOTTOM_LEFT = '└';
    static final char BOTTOM_RIGHT = '┘';
    static final char HORIZONTAL = '─';
    static final char VERTICAL = '│';
    static final char SCROLLBAR_THUMB = '█';
}