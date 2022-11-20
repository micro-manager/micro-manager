/*
 * Project: ASI Ring TIRF Control
 * License: BSD 3-clause, see LICENSE.md
 * Author: Brandon Simpson (brandon@asiimaging.com)
 * Copyright (c) 2022, Applied Scientific Instrumentation
 */

package com.asiimaging.tirf.ui.components;

import java.awt.Dimension;
import javax.swing.JButton;

public class Button extends JButton {

   private static int defaultWidth = 100;
   private static int defaultHeight = 100;

   public Button(final String text) {
      super(text);
      setAbsoluteSize(defaultWidth, defaultHeight);
      setFocusPainted(false); // remove highlight when clicked
   }

   public Button(final String text, final int width, final int height) {
      super(text);
      setAbsoluteSize(width, height);
      setFocusPainted(false); // remove highlight when clicked
   }

   public static void setDefaultSize(final int width, final int height) {
      defaultWidth = width;
      defaultHeight = height;
   }

   public void setAbsoluteSize(final int width, final int height) {
      final Dimension size = new Dimension(width, height);
      setPreferredSize(size);
      setMinimumSize(size);
      setMaximumSize(size);
   }

   public void registerListener(final Method method) {
      addActionListener(method::run);
   }
}
