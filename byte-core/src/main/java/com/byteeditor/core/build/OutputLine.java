package com.byteeditor.core.build;

/** One line of output from an external process, tagged by which stream it came from. */
public record OutputLine(String text, Stream stream) {

    public enum Stream {
        STDOUT,
        STDERR
    }
}
