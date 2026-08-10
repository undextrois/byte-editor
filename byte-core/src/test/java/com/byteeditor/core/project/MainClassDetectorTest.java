package com.byteeditor.core.project;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainClassDetectorTest {

    @Test
    void detectsSimpleClassWithPackage() {
        String source = """
                package com.example;

                public class App {
                    public static void main(String[] args) {
                        System.out.println("Hello from Byte!");
                    }
                }
                """;
        Optional<String> result = MainClassDetector.detect(source);
        assertEquals(Optional.of("com.example.App"), result);
    }

    @Test
    void detectsClassWithoutPackage() {
        String source = """
                public class HelloWorld {
                    public static void main(String[] args) {
                        System.out.println("Hello, World!");
                    }
                }
                """;
        assertEquals(Optional.of("HelloWorld"), MainClassDetector.detect(source));
    }

    @Test
    void returnsEmptyWhenNoMainMethod() {
        String source = """
                package com.example;
                public class NotRunnable {
                    void helper() {}
                }
                """;
        assertTrue(MainClassDetector.detect(source).isEmpty());
    }

    @Test
    void returnsEmptyWhenNoPublicClass() {
        String source = """
                class Internal {
                    public static void main(String[] args) {}
                }
                """;
        assertTrue(MainClassDetector.detect(source).isEmpty());
    }

    @Test
    void handlesFinalPublicClass() {
        String source = """
                package a.b;
                public final class Runner {
                    public static void main(String[] args) {}
                }
                """;
        assertEquals(Optional.of("a.b.Runner"), MainClassDetector.detect(source));
    }
}
