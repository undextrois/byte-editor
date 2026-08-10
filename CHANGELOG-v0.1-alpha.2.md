# Byte v0.1-alpha.2

Bug-fix release focused on the edit/build/run loop and terminal resizing.

## Fixed

- Build, test, and run now operate on the nearest Maven project containing the currently open file instead of always using the workspace root. This fixes F6 when editing nested Maven projects such as `examples/hello-java`.
- The build output now shows which Maven project Byte is executing against.
- Compiler-problem path resolution uses the Maven project that produced the build output.
- Terminal resize events are now explicitly consumed through Lanterna's `doResizeIfNecessary()` on the single UI thread, the screen is cleared, and the layout is redrawn from the new dimensions.

No new v0.2 features were added.
