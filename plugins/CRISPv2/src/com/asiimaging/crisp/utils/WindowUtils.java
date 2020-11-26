///////////////////////////////////////////////////////////////////////////////
// AUTHOR:       Brandon Simpson
//
// COPYRIGHT:    Applied Scientific Instrumentation, 2020
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

package com.asiimaging.crisp.utils;

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
        if (frame != null && frame.isDisplayable()) {
            return true;
        } else {
            return false;
        }
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
     * Calls dispose on the frame if not null.
     * 
     * @param frame the frame to dispose
     */
    public static void dispose(final JFrame frame) {
        if (frame != null) {
            frame.dispose();
        }
    }
}
