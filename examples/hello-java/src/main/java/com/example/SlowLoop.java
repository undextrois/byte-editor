package com.example;

/**
 * Test fixture for Byte's Esc-cancel behavior — see TESTING.md.
 *
 * <p>Open this file and press F6 to run it. It prints an incrementing
 * counter once a second and never stops on its own — that's the point.
 * Press Esc and confirm: a "[INFO] Cancelled." line appears immediately,
 * followed shortly by "Process finished with exit code ...". If Esc
 * doesn't produce both lines, or the counter keeps ticking after Cancelled
 * appears, that's a real bug worth reporting.
 */
public class SlowLoop {
    public static void main(String[] args) throws InterruptedException {
        int tick = 0;
        while (true) {
            System.out.println("tick " + tick++);
            Thread.sleep(500);
        }
    }
}
