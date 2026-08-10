# Test fixtures for Byte

These files exist purely to exercise specific Byte features — they aren't
part of the "real" example. `App.java` remains the canonical golden-path
smoke test from the main README (open → edit → Ctrl+S → F5 → BUILD SUCCESS
→ F6). Don't touch `App.java` or `pom.xml` when using these.

## `SlowLoop.java` — F6 Run + Esc-cancel

Open it, press F6. It prints an incrementing counter forever. Press Esc.
Expected: a `[INFO] Cancelled.` line appears immediately in Build Output,
followed shortly by `Process finished with exit code ...`. The counter
should stop ticking the moment you press Esc, not before, not after.

## `NoisyOutput.java` — editor scrollbar + build/run output scrollbar

228 lines of source — won't fit in the editor pane on its own, so opening it
should show the editor's scrollbar thumb. Press F6 to run it: it prints 200
lines fast, more than the Build Output pane can show at once, so that
scrollbar should appear too.

While it's still an unread wall of output, try scrolling the Build Output
pane up with the mouse wheel mid-run (or right after it finishes) — it
should stay put where you scrolled to, not get yanked back to the bottom.
Scroll back down to the very bottom and it should resume auto-following the
tail on the next run.

## `explorertest/ScrollFixture01.java` .. `ScrollFixture15.java` — explorer scrollbar

15 empty placeholder classes, no other purpose. Expand `explorertest` in the
PROJECT pane; if your terminal is short enough that all entries don't fit,
the explorer's scrollbar thumb should appear. Arrow keys and mouse wheel
should both scroll it.

## `BrokenExample.java.disabled` — F8 Problems

Disabled by extension so it's excluded from the default build. To use:

```bash
mv BrokenExample.java.disabled BrokenExample.java
```

Open it in Byte, press F5. The missing semicolon on the `total` line should
produce a compiler error. Press F8 — the Problems list should show it; Enter
on that entry should jump straight to the broken line. Rename it back to
`.disabled` (or delete it) when done so it doesn't linger in the build.

## Quick pass, all together

1. `SlowLoop.java` → F6 → Esc mid-run → confirm cancel.
2. `NoisyOutput.java` → open (editor scrollbar) → F6 (output scrollbar) →
   scroll up mid-run → confirm it doesn't snap back down on its own.
3. Expand `explorertest/` → confirm explorer scrollbar appears and scrolls.
4. Rename `BrokenExample.java.disabled` → `.java` → F5 → F8 → Enter on the
   error → confirm it jumps to the right line → rename back.
