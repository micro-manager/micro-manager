/*
 * Project: ASI CRISP Control
 * License: BSD 3-clause, see LICENSE.md
 * Author: Brandon Simpson (brandon@asiimaging.com)
 * Copyright (c) 2014-2024, Applied Scientific Instrumentation
 */

package com.asiimaging.ui;

import javax.swing.JPanel;
import net.miginfocom.swing.MigLayout;

public class Panel extends JPanel {

   private static String defaultLayout = "";
   private static String defaultCols = "";
   private static String defaultRows = "";

   public Panel() {
      setMigLayout(defaultLayout, defaultCols, defaultRows);
   }

   public Panel(final MigLayout layout) {
      setLayout(layout);
   }

   private Panel(final String layout, final String cols, final String rows) {
      setMigLayout(layout, cols, rows);
   }

   public static Panel createFromMigLayout(final String layout, final String cols,
                                           final String rows) {
      return new Panel(layout, cols, rows);
   }

   /**
    * Set the layout using MigLayout.
    *
    * @param layout the layout constraints
    * @param cols   the column constraints
    * @param rows   the row constraints
    */
   public void setMigLayout(final String layout, final String cols, final String rows) {
      setLayout(new MigLayout(layout, cols, rows));
   }

   public static void setDefaultMigLayout(final String layout, final String cols,
                                          final String rows) {
      Panel.defaultLayout = layout;
      Panel.defaultCols = cols;
      Panel.defaultRows = rows;
   }

}
