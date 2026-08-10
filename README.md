# Byte

A fast, retro terminal development workbench: **edit → build → diagnose → run** without leaving the terminal.

Byte v0.1 focuses on one workflow end-to-end: open a Java/Maven project, edit source, build it, inspect compiler problems, and run it from a keyboard-driven TUI.

## Project layout

```text
byte/
├── pom.xml
├── byte-core/          # UI-agnostic editor/project/build logic
├── byte-tui/           # Lanterna UI and application entry point
└── examples/hello-java # smoke-test Maven project
```

The important architectural boundary is simple: **byte-core does not depend on Lanterna**.

## Implemented in v0.1-alpha.1

- Independent `TextBuffer` with save, undo/redo, cursor movement, and Unicode-safe edits.
- Maven project detection, including walking upward to the nearest `pom.xml` when a nested source file is opened directly.
- Keyboard-driven project explorer with expand/collapse and file opening.
- Persistent editor viewport with vertical/horizontal scrolling and tab-aware cursor placement.
- Maven `package` and `test` execution with live stdout/stderr capture.
- Single-threaded UI event loop; background processes never render directly.
- Compiler problem parsing and an F8 Problems view with jump-to-source.
- Run current Java main class through Maven Exec so project dependencies are available.
- Retro three-pane Lanterna interface.

## Controls

```text
F2       Explorer / editor focus
F5       mvn package
F6       Run current Java main class
F7       mvn test
F8       Problems
F10      Quit
Ctrl+S   Save
Ctrl+Z   Undo
Ctrl+Y   Redo
Esc      Return to editor / close Problems
```

Explorer controls: Up/Down navigate, Right expands, Left collapses, Enter opens a file or toggles a directory.

## Build

Requires Java 17+ and Maven with access to your configured Maven repositories.

```bash
mvn -f byte/pom.xml clean package
```

The shaded executable is:

```text
byte/byte-tui/target/byte.jar
```

## Smoke test

```bash
cd byte/examples/hello-java
java -jar ../../byte-tui/target/byte.jar .
```

Then use only Byte:

```text
open App.java
edit
Ctrl+S
F5
F8 if compilation fails
F6 when the build succeeds
```

## Deliberate v0.1 cuts

- Single open buffer; no tabs yet.
- No syntax highlighting yet.
- No full terminal emulator / PTY — concretely, this means a running
  process's stdin is never connected to anything. A program that calls
  `Scanner.nextLine()` or otherwise reads `System.in` will hang with no way
  to type into it (keystrokes go to the editor, not the child process).
  `Esc` reliably kills a hung process in the meantime — see
  `examples/hello-java/WaitsForInput.java` for a fixture that demonstrates
  exactly this.
- No LSP, autocomplete, debugger, Git client, plugins, or AI.
- No project-wide search yet.

Those stay out until the core edit/build/diagnose/run workflow is stable.

## Verification for this alpha

The revised `byte-core` and `byte-tui` sources were compiled with `javac --release 17`. The existing shaded JAR supplied with the POC was updated with the newly compiled classes, and a small core smoke test verified nearest-Maven-project detection and Unicode cursor safety. Maven itself was not available in the repair environment, so run the normal Maven test suite on your development machine as the final verification step:

```bash
mvn -f byte/pom.xml clean test
```

## Mouse support (v0.1-alpha.3)

Byte remains keyboard-first, but basic mouse input is supported: click files/directories in the project explorer, click to position the editor cursor, use the wheel to scroll the pane under the pointer, and click enabled function-key actions in the footer. On terminals that reserve mouse input for applications, hold the terminal's usual modifier (commonly Shift) when you want to select/copy terminal text instead.
