/*
 * Project: ASI Ring TIRF Control
 * License: BSD 3-clause, see LICENSE.md
 * Author: Brandon Simpson (brandon@asiimaging.com)
 * Copyright (c) 2022, Applied Scientific Instrumentation
 */

package com.asiimaging.tirf.ui.components;

import java.awt.Dimension;
import java.awt.Insets;
import javax.swing.ImageIcon;
import javax.swing.JToggleButton;

public class ToggleButton extends JToggleButton {

   private static int defaultWidth = 200;
   private static int defaultHeight = 30;

   private final String onText;
   private final String offText;
   private final ImageIcon onImage;
   private final ImageIcon offImage;

   public ToggleButton(final String offText, final String onText,
                       final ImageIcon offImage, final ImageIcon onImage) {
      this.offText = offText;
      this.onText = onText;
      this.offImage = offImage;
      this.onImage = onImage;
      setSize(defaultWidth, defaultHeight);
      init();
   }

   public ToggleButton(final String offText, final String onText,
                       final ImageIcon offImage, final ImageIcon onImage,
                       final int width, final int height) {
      this.offText = offText;
      this.onText = onText;
      this.offImage = offImage;
      this.onImage = onImage;
      setSize(width, height);
      init();
   }

   public static void setDefaultSize(final int width, final int height) {
      defaultWidth = width;
      defaultHeight = height;
   }

   private void init() {
      setState(false); // set the initial state
      setMargins(1, 1, 1, 1);
      setFocusPainted(false); // remove focus highlight
   }

   public void setState(boolean state) {
      setSelected(state);
      if (state) {
         setText(onText);
         setIcon(onImage);
      } else {
         setText(offText);
         setIcon(offImage);
      }
   }

   public void setMargins(int top, int left, int bottom, int right) {
      setMargin(new Insets(top, left, bottom, right));
   }

   @Override
   public void setSize(final int width, final int height) {
      final Dimension size = new Dimension(width, height);
      setPreferredSize(size);
      setMinimumSize(size);
      setMaximumSize(size);
   }

   public void registerListener(final Method method) {
      addActionListener(event -> {
         setState(isSelected());
         method.run(event);
      });
   }

}