package com.byteeditor.core.build;

import java.nio.file.Path;

/** A single diagnostic (compile error/warning) parsed from build output, with a jump target. */
public record Problem(Path file, int line, int column, String message) {

    @Override
    public String toString() {
        return file.getFileName() + " " + line + ":" + column + "   " + message;
    }
}
