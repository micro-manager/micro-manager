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
