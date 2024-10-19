/*
 * Project: ASI PLogic Control
 * License: BSD 3-clause, see LICENSE.md
 * Author: Brandon Simpson (brandon@asiimaging.com)
 * Copyright (c) 2024, Applied Scientific Instrumentation
 */

package com.asiimaging.plogic.ui.asigui;

import java.awt.Color;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.border.TitledBorder;
import net.miginfocom.swing.MigLayout;
import org.micromanager.internal.dialogs.ComponentTitledBorder;

public class Panel extends JPanel {

   public static final int BORDER_LEFT = TitledBorder.LEFT;

   private static String defaultLayout_ = "";
   private static String defaultCols_ = "";
   private static String defaultRows_ = "";

   public Panel() {
      setMigLayout(defaultLayout_, defaultCols_, defaultRows_);
   }

   public Panel(final String text) {
      setMigLayout(defaultLayout_, defaultCols_, defaultRows_);
      final TitledBorder titledBorder = BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(Color.gray), text);
      titledBorder.setTitleJustification(TitledBorder.CENTER);
      titledBorder.setTitleFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
      setBorder(titledBorder);
   }

   public Panel(final String text, int side) { // side = TitledBorder.CENTER, etc
      setMigLayout(defaultLayout_, defaultCols_, defaultRows_);
      final TitledBorder titledBorder = BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(Color.gray), text);
      titledBorder.setTitleJustification(side);
      titledBorder.setTitleFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
      setBorder(titledBorder);
   }

   public Panel(final boolean border) {
      setMigLayout(defaultLayout_, defaultCols_, defaultRows_);
      if (border) {
         setBorder(BorderFactory.createLineBorder(Color.gray));
      }
   }

   public Panel(final CheckBox checkBox) {
      setMigLayout(defaultLayout_, defaultCols_, defaultRows_);
      final ComponentTitledBorder border = new ComponentTitledBorder(checkBox, this,
            BorderFactory.createLineBorder(Color.gray));
      setBorder(border);
   }

   /**
    * Set the layout using MigLayout.
    *
    * @param layout the layout constraints
    * @param cols the column constraints
    * @param rows the row constraints
    */
   public void setMigLayout(final String layout, final String cols, final String rows) {
      setLayout(new MigLayout(layout, cols, rows));
   }

   public static void setMigLayoutDefault(
         final String layout,
         final String cols,
         final String rows) {
      Panel.defaultLayout_ = layout;
      Panel.defaultCols_ = cols;
      Panel.defaultRows_ = rows;
   }

}
