/*
 * Project: ASI Ring TIRF Control
 * License: BSD 3-clause, see LICENSE.md
 * Author: Brandon Simpson (brandon@asiimaging.com)
 * Copyright (c) 2022, Applied Scientific Instrumentation
 */

package com.asiimaging.tirf.ui.components;

import java.awt.Font;
import javax.swing.JCheckBox;
import javax.swing.SwingConstants;

public class CheckBox extends JCheckBox {

   public static final int LEFT = SwingConstants.LEFT;
   public static final int RIGHT = SwingConstants.RIGHT;

   public CheckBox(String text, int fontSize, boolean defaultState) {
      super(text, defaultState);
      setFont(new Font(Font.MONOSPACED, Font.PLAIN, fontSize));
      setHorizontalTextPosition(LEFT);
      setFocusPainted(false);
   }

   public CheckBox(String text, int fontSize, boolean defaultState, int constant) {
      super(text, defaultState);
      setFont(new Font(Font.MONOSPACED, Font.PLAIN, fontSize));
      setHorizontalTextPosition(constant);
      setFocusPainted(false);
   }

   public void registerListener(final Method method) {
      addItemListener(method::run);
   }

}
