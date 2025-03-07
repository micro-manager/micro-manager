/*
 * Project: ASI PLogic Control
 * License: BSD 3-clause, see LICENSE.md
 * Author: Brandon Simpson (brandon@asiimaging.com)
 * Copyright (c) 2024, Applied Scientific Instrumentation
 */

package com.asiimaging.plogic.ui.asigui;

import java.awt.Font;
import java.util.ArrayList;
import javax.swing.ButtonGroup;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.SwingConstants;
import net.miginfocom.swing.MigLayout;

public class RadioButton extends JPanel {

   // constants
   public static final int VERTICAL = 0;
   public static final int HORIZONTAL = 1;
   public static final int LEFT = SwingConstants.LEFT;
   public static final int RIGHT = SwingConstants.RIGHT;

   private String layoutStyle_;
   private final ButtonGroup buttonGroup_;
   private final ArrayList<JRadioButton> buttons_;

   public RadioButton(final String[] names, final String selected) {
      setMigLayout("", "", "");
      buttonGroup_ = new ButtonGroup();
      buttons_ = new ArrayList<>();
      setLayoutStyle(HORIZONTAL, LEFT);
      addButtons(names);
      setSelected(selected, true);
   }

   public RadioButton(final String[] names,
                      final String selected,
                      final int type,
                      final int alignment) {
      setMigLayout("", "", "");
      buttonGroup_ = new ButtonGroup();
      buttons_ = new ArrayList<>();
      setLayoutStyle(type, alignment);
      addButtons(names);
      setSelected(selected, true);
   }

   public void setMigLayout(final String a, final String b, final String c) {
      setLayout(new MigLayout(a, b, c));
   }

   public void setEnabled(final boolean state) {
      for (final JRadioButton button : buttons_) {
         button.setEnabled(state);
      }
   }

   private void addRadioButton(final String text) {
      final JRadioButton button = new JRadioButton(text);
      final Font font = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
      button.setFocusPainted(false); // remove focus highlight when clicked
      button.setFont(font);
      buttonGroup_.add(button);
      buttons_.add(button);
      add(button, layoutStyle_);
   }

   private void addButtons(final String[] names) {
      for (final String name : names) {
         addRadioButton(name);
      }
      buttons_.trimToSize();
   }

   private void setLayoutStyle(final int type, final int alignment) {
      layoutStyle_ = (type == RadioButton.VERTICAL) ? "wrap" : "";
      layoutStyle_ = (alignment == RadioButton.LEFT)
            ? "left, " + layoutStyle_ : "right, " + layoutStyle_;
   }

   public void setSelected(final String text, final boolean state) {
      for (final JRadioButton button : buttons_) {
         if (button.getText().equals(text)) {
            button.setSelected(state);
         }
      }
   }

   public String getSelectedButtonText() {
      for (final JRadioButton button : buttons_) {
         if (button.isSelected()) {
            return button.getText();
         }
      }
      return "";
   }

   public void registerListener(final Method method) {
      for (final JRadioButton button : buttons_) {
         button.addActionListener(method::run);
      }
   }
}