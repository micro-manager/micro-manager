/*
 * Project: ASI PLogic Control
 * License: BSD 3-clause, see LICENSE.md
 * Author: Brandon Simpson (brandon@asiimaging.com)
 * Copyright (c) 2024, Applied Scientific Instrumentation
 */

package com.asiimaging.plogic.ui.asigui;

import java.awt.Font;
import javax.swing.JCheckBox;
import javax.swing.SwingConstants;

public class CheckBox extends JCheckBox {

   public static final int LEFT = SwingConstants.LEFT;
   public static final int RIGHT = SwingConstants.RIGHT;

   public CheckBox(final boolean defaultState) {
      super("", defaultState);
      setHorizontalTextPosition(RIGHT);
      setFocusPainted(false);
   }

   public CheckBox(final String text, final boolean defaultState) {
      super(text, defaultState);
      setHorizontalTextPosition(RIGHT);
      setFocusPainted(false);
   }

   public CheckBox(final String text, final boolean defaultState, final int constant) {
      super(text, defaultState);
      setHorizontalTextPosition(constant);
      setFocusPainted(false);
   }

   public CheckBox(
         final String text,
         final int fontSize,
         final boolean defaultState,
         final int constant) {
      super(text, defaultState);
      setFont(new Font(Font.SANS_SERIF, Font.PLAIN, fontSize));
      setHorizontalTextPosition(constant);
      setFocusPainted(false);
   }

   public void registerListener(final Method method) {
      addItemListener(method::run);
   }

}
