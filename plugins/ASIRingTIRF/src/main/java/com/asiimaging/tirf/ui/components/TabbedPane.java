/*
 * Project: ASI Ring TIRF Control
 * License: BSD 3-clause, see LICENSE.md
 * Author: Brandon Simpson (brandon@asiimaging.com)
 * Copyright (c) 2022, Applied Scientific Instrumentation
 */

package com.asiimaging.tirf.ui.components;

import java.awt.Dimension;
import java.awt.Insets;
import javax.swing.JTabbedPane;
import javax.swing.UIManager;

public class TabbedPane extends JTabbedPane {

   public TabbedPane(final int width, final int height) {
      super(JTabbedPane.TOP);
      setAbsoluteSize(width, height);
      setFocusable(false); // TODO: only a solution if you don't need key presses?
      // makes the ui look nice
      Insets insets = UIManager.getInsets("TabbedPane.contentBorderInsets");
      insets.top = -1;
      insets.bottom = 1;
      insets.right = 1;
      insets.left = 1;
      UIManager.put("TabbedPane.contentBorderInsets", insets);
   }

   public void setAbsoluteSize(final int width, final int height) {
      final Dimension size = new Dimension(width, height);
      setPreferredSize(size);
      setMinimumSize(size);
      setMaximumSize(size);
   }

}
