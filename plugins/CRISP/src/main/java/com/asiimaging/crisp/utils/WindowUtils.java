/*
 * Project: ASI CRISP Control
 * License: BSD 3-clause, see LICENSE.md
 * Author: Brandon Simpson (brandon@asiimaging.com)
 * Copyright (c) 2014-2021, Applied Scientific Instrumentation
 */

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

}
