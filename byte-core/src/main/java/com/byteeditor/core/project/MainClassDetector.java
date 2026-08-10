package com.byteeditor.core.project;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects a fully-qualified runnable class name from Java source text.
 *
 * <p>v0.1 deliberately does not parse the file into an AST — it looks for
 * {@code public static void main(String...} and the enclosing public class
 * name, plus an optional {@code package} declaration. This is enough to
 * drive F6 "Run" for the common case (a single top-level public class per
 * file) without pulling in a compiler front end. A file with multiple
 * classes, or a main method on a non-public class, is out of scope for v0.1
 * — see the spec's discussion of a {@code .byte.json} run configuration for
 * where this should go next.
 */
public final class MainClassDetector {

    private static final Pattern PACKAGE_DECL = Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;");
    private static final Pattern PUBLIC_CLASS_DECL =
            Pattern.compile("(?m)public\\s+(?:final\\s+|abstract\\s+)?class\\s+(\\w+)");
    private static final Pattern MAIN_METHOD =
            Pattern.compile("public\\s+static\\s+void\\s+main\\s*\\(\\s*String");

    private MainClassDetector() {
    }

    /**
     * Returns the fully-qualified class name to run, if the given source
     * text looks like a runnable entry point; otherwise empty.
     */
    public static Optional<String> detect(String sourceCode) {
        if (!MAIN_METHOD.matcher(sourceCode).find()) {
            return Optional.empty();
        }
        Matcher classMatcher = PUBLIC_CLASS_DECL.matcher(sourceCode);
        if (!classMatcher.find()) {
            return Optional.empty();
        }
        String className = classMatcher.group(1);

        Matcher packageMatcher = PACKAGE_DECL.matcher(sourceCode);
        return Optional.of(packageMatcher.find()
                ? packageMatcher.group(1) + "." + className
                : className);
    }
}
