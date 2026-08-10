# Byte v0.1-alpha.1

This pass stabilizes the original proof of concept without expanding the product scope.

## Fixed

- Single-threaded UI rendering. Build/run workers now post events to the UI loop instead of rendering from background threads.
- Non-blocking input loop so process output redraws while Maven/Java are running.
- Interactive project explorer with keyboard navigation, expand/collapse, and file opening.
- `byte .` now opens the first Java source file it finds while keeping the explorer available.
- Project detection now walks upward to the nearest `pom.xml`, so opening a Java file deep under `src/main/java` still uses the Maven project root.
- Persistent editor viewport with vertical and horizontal scrolling instead of recentering the cursor on every move.
- Tab-aware cursor display columns.
- Ctrl+Z / Ctrl+Y undo and redo are wired into the TUI.
- F7 runs Maven tests.
- F8 shows parsed Maven compiler problems; Enter jumps to the source location.
- F6 runs through Maven's exec plugin so project dependencies are available on the runtime classpath.
- Safer UTF-16 cursor placement when moving vertically across lines containing supplementary Unicode characters.
- Better handling of small terminals and subprocess ANSI/control characters.

## v0.1 keyboard map

- `F2` Explorer / editor focus
- `F5` Maven package
- `F6` Run current Java main class
- `F7` Maven test
- `F8` Problems
- `F10` Quit
- `Ctrl+S` Save
- `Ctrl+Z` Undo
- `Ctrl+Y` Redo
- `Esc` Return to editor / close Problems

Explorer: arrows navigate, Right expands, Left collapses, Enter opens/toggles.
