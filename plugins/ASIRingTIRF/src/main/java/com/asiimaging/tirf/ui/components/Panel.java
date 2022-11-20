/*
 * Project: ASI Ring TIRF Control
 * License: BSD 3-clause, see LICENSE.md
 * Author: Brandon Simpson (brandon@asiimaging.com)
 * Copyright (c) 2022, Applied Scientific Instrumentation
 */

package com.asiimaging.tirf.ui.components;

import javax.swing.JPanel;
import net.miginfocom.swing.MigLayout;

public class Panel extends JPanel {

   private static String defaultLayout = "";
   private static String defaultCols = "";
   private static String defaultRows = "";

   public Panel() {
      setMigLayout(defaultLayout, defaultCols, defaultRows);
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

   public static void setMigLayoutDefault(final String layout, final String cols,
                                          final String rows) {
      Panel.defaultLayout = layout;
      Panel.defaultCols = cols;
      Panel.defaultRows = rows;
   }

}
