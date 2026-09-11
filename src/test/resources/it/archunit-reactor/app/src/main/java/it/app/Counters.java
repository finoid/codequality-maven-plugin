package it.app;

/**
 * Violates the rule the library registers through the service loader.
 */
public class Counters {
    public static int counter;

    private Counters() {
    }
}
