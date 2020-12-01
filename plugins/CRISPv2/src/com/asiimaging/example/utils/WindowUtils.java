package com.asiimaging.example.utils;

import java.awt.event.WindowEvent;

import javax.swing.JFrame;

public final class WindowUtils {

    /**
     * Returns true if the window is displayable and not null.
     * 
     * @param frame the {@link JFrame} to check
     * @return true if the frame is open
     */
    public static boolean isOpen(final JFrame frame) {
        if (frame != null && frame.isDisplayable()) {
            return true;
        } else {
            return false;
        }
    }
    
    /**
     * Close the frame by sending a window closing event.
     * 
     * @param frame the {@link JFrame} to close
     */
    public static void close(final JFrame frame) {
        frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING));
    }
    
    /**
     * Calls dispose on the frame if not null.
     * 
     * @param frame the {@link JFrame} to dispose
     */
    public static void dispose(final JFrame frame) {
        if (frame != null) {
            frame.dispose();
        }
    }
}
