# Byte v0.1-alpha.3

Focused stability/interaction update; no new architecture or v0.2 features.

## Fixed
- Terminal resize now forces one complete repaint after Lanterna applies the new terminal size. This avoids the UI appearing frozen/stale after mouse-driven window resizing on some terminal emulators.
- Editor cursor/focus remains usable after resize; a click in the editor explicitly returns focus to the editor.

## Mouse support
- Enabled basic Lanterna mouse capture.
- Click a file in PROJECT to open it.
- Click a directory to expand/collapse it.
- Click inside the editor to place the cursor and focus the editor.
- Mouse wheel scrolls the pane under the pointer.
- Click F2/F5/F6/F7/F8/F10 on the footer to invoke the same actions as the keyboard keys.

Keyboard-first behavior remains unchanged; mouse support is optional.
