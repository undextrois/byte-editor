# Byte v0.1-alpha.4

Stability and test-coverage release. No new v0.2 features.

## Fixed

- F6 Run now uses `exec-maven-plugin`'s `exec` goal instead of `java`. Previously the target class's `main()` ran on a thread inside Maven's own JVM with no separate child process, so Esc could kill `mvn` without actually stopping a hung or long-running class. `exec:exec` spawns a real `java` child process that can be terminated.
- All `mvn` invocations now force `-Duser.language=en`, so build/compiler output stays in English regardless of the host machine's locale. (The Problems parser was already locale-agnostic by construction — it only anchors on the `file.java:[line,col]` bracket shape javac always emits, treating the message text after it as opaque — but mixed-language console output was still confusing on a non-English-locale machine.)
- Pane layout geometry (footer row, split heights, explorer width) is now computed in one place (`layout()`) instead of being independently re-derived in `render()`, `layout()`, and `editorHeight()`. Previously these could silently drift out of sync if pane-sizing constants ever changed in only one spot.
- Fixed a crash (`StringIndexOutOfBoundsException`) in the footer's status line on terminals wider than the hardcoded footer string it was being truncated against.
- `Esc` now cancels a running build/run/test in addition to its existing behavior (return focus to editor, close the Problems view). Prints `[INFO] Cancelled.` to Build Output.

## Scrollbars

- Explorer, Editor, and Build Output panes each get a proportional scrollbar thumb on their right border, appearing only when content actually overflows the visible height.
- Build Output gained real scroll-back: mouse wheel scrolls it like a log viewer, and it stops auto-following new output the moment you scroll up, resuming auto-follow only once you scroll back down to the bottom yourself. Starting a new build/run/test always resets it to the tail.

## Known limitation, documented

- Byte does not attach anything to a running process's stdin (no PTY support in v0.1, unchanged from the original scope). A program reading `System.in` will hang with no way to type into it; `Esc` is the way out in the meantime. Demonstrated by `examples/hello-java/WaitsForInput.java`.

## Test coverage

`byte-tui` has a real test suite for the first time (`EditorViewport`, `ExplorerModel` — both previously-untested TUI logic layers). `byte-core` gained coverage for three previously-untested behaviors: `TextBuffer`'s vertical-cursor surrogate-pair safety, `MavenBuildProvider.run()`'s command construction (including the `exec:exec` fix above), and `ProjectDetector.detectNearest()` — including a regression test for the exact nested-Maven-module bug (an outer aggregator `pom.xml` shadowing a nested module's own `pom.xml`) that motivated writing it in the first place.

Full suite: 79 tests across both modules, all passing (`byte-core` 52, `byte-tui` 27).

## Test fixtures

`examples/hello-java` gained purpose-built fixtures for manual testing, documented in `TESTING.md`: `SlowLoop.java` (Esc-cancel), `NoisyOutput.java` (editor + build-output scrollbars), `explorertest/` (explorer scrollbar), `BrokenExample.java.disabled` (Problems view, opt-in, excluded from the default build), `WaitsForInput.java` (stdin limitation).

---

Full manual pass against `MANUAL-TEST-CHECKLIST.md` completed and confirmed green.
