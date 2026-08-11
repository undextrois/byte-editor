# Byte v0.1 — Manual Test Checklist

A full pass through everything built and fixed so far. Run through this on
a real terminal after `mvn -f byte/pom.xml clean install`. Check items off
as you go; anything that fails is worth reporting with what you did and
what you expected instead.

Most fixture-based items reference `examples/hello-java/TESTING.md` — this
checklist is the broader pass around those, covering things not tied to a
specific fixture file.

## Startup & layout

- [ ] `byte examples/hello-java` from the workspace root opens directly
      into that project (not the root aggregator).
- [ ] Resize the terminal window smaller, then larger, mid-session — layout
      reflows correctly, no stale/clipped panes.
- [ ] Maximize the window — same check, this was the original bug that
      started this whole thread.
- [ ] Shrink the terminal below ~60x18 — the "needs a bigger terminal"
      message appears instead of a broken layout; growing back restores
      the normal view.

## Keyboard — editing

- [ ] Type, arrow-key navigate, Home/End, PageUp/PageDown all behave
      normally in the editor.
- [ ] Ctrl+S saves (title bar `*` dirty marker clears).
- [ ] Ctrl+Z undoes, Ctrl+Y redoes, across multiple steps.
- [ ] Paste a multi-line block — lands as one undo step (single Ctrl+Z
      reverts the whole paste, not one character at a time).
- [ ] A file with tabs — cursor position via arrow keys and via mouse
      click both land in the visually correct spot.

## Keyboard — F-keys

- [ ] F2 toggles focus between PROJECT and editor (visible via the `*`
      marker in the pane title).
- [ ] F5 builds the **nearest** Maven project to the open file, not the
      workspace root — confirm the build output header shows
      `[examples/hello-java]` when editing a file there.
- [ ] F6 runs the detected main class, same nearest-project scoping.
- [ ] F7 runs `mvn test` against the nearest project.
- [ ] F8 toggles the Problems view; arrow keys navigate entries; Enter on
      an entry jumps the editor to that file/line.
- [ ] F10 quits cleanly — no hung terminal, no leftover process.
- [ ] Esc cancels a running build/run/test (see `SlowLoop.java` /
      `WaitsForInput.java` in TESTING.md) — confirm the process is
      actually dead afterward, not just detached from the UI.
- [ ] Unimplemented F-key slots (F1, F3, F4, F9) render visibly dimmed in
      the footer, not identical to the active ones.

## Mouse

- [ ] Click a file in PROJECT — opens it.
- [ ] Click a directory in PROJECT — expands/collapses it.
- [ ] Click inside the editor — places the cursor at the clicked position
      (including on a line with tabs — should land on the right character,
      not shifted).
- [ ] Click a footer F-key slot — triggers the same action as the keyboard
      shortcut (test at least F5, F6, F10).
- [ ] Mouse wheel over PROJECT scrolls the explorer.
- [ ] Mouse wheel over the editor scrolls it.
- [ ] Mouse wheel over Build Output scrolls it, and does **not** snap back
      to the bottom while a build is still streaming — only resumes
      auto-follow once you scroll back down to the tail yourself.
- [ ] Mouse wheel over the Problems view (when F8 is toggled on) moves the
      selected entry instead.

## Scrollbars

Using `NoisyOutput.java` and the 15 `explorertest/ScrollFixture*.java`
files from TESTING.md:

- [ ] Explorer scrollbar appears only when the file list overflows the
      pane height; a short list shows a plain border, no thumb.
- [ ] Editor scrollbar appears when a file is longer than the visible
      area (open `NoisyOutput.java`); thumb position/size roughly tracks
      where you are in the file.
- [ ] Build Output scrollbar appears after running `NoisyOutput.java`
      (200 lines of output); same fits/overflows behavior.
- [ ] Starting a fresh build/run always resets Build Output back to the
      tail, even if you'd previously scrolled up in an older run's output.

## Build/run correctness

- [ ] `BrokenExample.java.disabled` renamed to `.java`: F5 fails with a
      compiler error, F8 shows it, Enter jumps to the exact broken line.
      Rename back afterward.
- [ ] Build console output is plain readable text — no visible garbage,
      no crash — even though Maven's own progress/color output is
      suppressed at the source (`-B -Dstyle.color=never`).
- [ ] `WaitsForInput.java`: F6 hangs on the prompt as expected (no stdin
      support is a known v0.1 limitation), Esc kills it cleanly.

## Cross-cutting

- [ ] Everything above still holds true after resizing the terminal
      mid-session — no pane desyncs from the resize handling.
- [ ] No zombie `java`/`mvn` processes left running after quitting Byte
      (check with `ps aux | grep -i mvn` after F10) — especially after an
      Esc-cancel earlier in the session.
