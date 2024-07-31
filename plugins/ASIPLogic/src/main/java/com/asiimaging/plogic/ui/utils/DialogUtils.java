/*
 * Project: ASI PLogic Control
 * License: BSD 3-clause, see LICENSE.md
 * Author: Brandon Simpson (brandon@asiimaging.com)
 * Copyright (c) 2024, Applied Scientific Instrumentation
 */

package com.asiimaging.plogic.ui.utils;

import javax.swing.JComponent;
import javax.swing.JOptionPane;

public class DialogUtils {

   public static void showMessage(
         final JComponent component,
         final String title,
         final String message) {
      JOptionPane.showMessageDialog(component, message, title, JOptionPane.INFORMATION_MESSAGE);
   }

   public static boolean showConfirmDialog(
         final JComponent component,
         final String title,
         final String message) {
      return JOptionPane.showConfirmDialog(component, message, title, JOptionPane.YES_NO_OPTION) == 0;
   }

}