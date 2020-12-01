package com.asiimaging.example.utils;

/**
 * Methods taken from the {@code Objects} class.
 * <p>
 * Provides support for methods from JDK6+.
 */
public final class ObjectUtils {
    
    public static <T> T requireNonNull(final T obj) {
        if (obj == null) {
            throw new NullPointerException();
        }
        return obj;
    }
    
    public static <T> T requireNonNull(final T obj, final String message) {
        if (obj == null) {
            throw new NullPointerException(message);
        }
        return obj;
    }
}
