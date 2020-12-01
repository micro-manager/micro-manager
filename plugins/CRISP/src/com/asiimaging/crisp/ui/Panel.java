///////////////////////////////////////////////////////////////////////////////
// AUTHOR:       Brandon Simpson
//
// COPYRIGHT:    Applied Scientific Instrumentation, 2020
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

package com.asiimaging.crisp.ui;

import javax.swing.JPanel;

import net.miginfocom.swing.MigLayout;

@SuppressWarnings("serial")
public class Panel extends JPanel {

    private static String layout = "";
    private static String cols = "";
    private static String rows = "";
    
    public Panel() {
        super();
        setMigLayout(layout, cols, rows);
    }
    
    /**
     * Set the layout using MigLayout.
     * 
     * @param layout the layout constraints
     * @param columns the column constraints
     * @param rows the row constraints
     */
    public void setMigLayout(final String layout, final String cols, final String rows) {
        setLayout(new MigLayout(layout, cols, rows));
    }

    public static void setMigLayoutDefaults(final String layout, final String cols, final String rows) {
        Panel.layout = layout;
        Panel.cols = cols;
        Panel.rows = rows;
    }
    
}
