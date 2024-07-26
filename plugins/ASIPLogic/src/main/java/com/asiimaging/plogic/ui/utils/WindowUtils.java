/*
 * Project: ASI PLogic Control
 * License: BSD 3-clause, see LICENSE.md
 * Author: Brandon Simpson (brandon@asiimaging.com)
 * Copyright (c) 2024, Applied Scientific Instrumentation
 */

package com.asiimaging.plogic.ui.utils;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JFrame;

public final class WindowUtils {

    /**
     * Returns true if the window is displayable and not null.
     *
     * @param frame the frame to check
     * @return true if the window is open
     */
    public static boolean isOpen(final JFrame frame) {
        return frame != null && frame.isDisplayable();
    }

    /**
     * Creates a window event object and dispatches the close event.
     *
     * @param frame the frame to close
     */
    public static void close(final JFrame frame) {
        final WindowEvent windowEvent = new WindowEvent(frame, WindowEvent.WINDOW_CLOSING);
        frame.dispatchEvent(windowEvent);
    }

    /**
     * Registers a listener on the JFrame object that listens for the window closing event.
     *
     * @param frame the frame to register the listener
     * @param method the method to run on the window closing event
     */
    public static void registerWindowClosingEvent(final JFrame frame, final WindowEventMethod method) {
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(final WindowEvent event) {
                method.run(event);
            }
        });
    }

}
