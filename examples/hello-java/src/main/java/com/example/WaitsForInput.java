package com.example;

import java.util.Scanner;

/**
 * Test fixture documenting a known v0.1 limitation — see TESTING.md.
 *
 * <p>Byte does not attach anything to a running process's stdin (full PTY
 * support was cut from v0.1 scope from day one). If a program you F6-run
 * calls {@link Scanner#nextLine()} or otherwise reads from
 * {@link System#in}, it will block forever with no visible sign of why —
 * Byte has no way to type into it, since keystrokes go to the editor
 * buffer, not the running child process.
 *
 * <p>This is expected, not a bug. The way out is Esc: it kills the hung
 * process outright (see {@link com.byteeditor.core.build.BuildProcess#kill()}).
 */
public class WaitsForInput {
    public static void main(String[] args) {
        System.out.println("Waiting for a line of input that Byte cannot send...");
        System.out.println("(Press Esc now to confirm it can still be cancelled.)");
        String line = new Scanner(System.in).nextLine();
        System.out.println("Got: " + line);
    }
}
