/*
 * Project: ASI PLogic Control
 * License: BSD 3-clause, see LICENSE.md
 * Author: Brandon Simpson (brandon@asiimaging.com)
 * Copyright (c) 2024, Applied Scientific Instrumentation
 */

package com.asiimaging.plogic.ui.asigui;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.border.TitledBorder;

import net.miginfocom.swing.MigLayout;
import org.micromanager.internal.dialogs.ComponentTitledBorder;

import java.awt.Color;
import java.awt.Font;

public class Panel extends JPanel {

    private static String defaultLayout = "";
    private static String defaultCols = "";
    private static String defaultRows = "";

    public Panel() {
        setMigLayout(defaultLayout, defaultCols, defaultRows);
    }

    public Panel(final String text) {
        setMigLayout(defaultLayout, defaultCols, defaultRows);
        final TitledBorder titledBorder = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.gray), text);
        titledBorder.setTitleJustification(TitledBorder.CENTER);
        titledBorder.setTitleFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        setBorder(titledBorder);
    }

    public Panel(final String text, int side) { // side = TitledBorder.CENTER, etc
        setMigLayout(defaultLayout, defaultCols, defaultRows);
        final TitledBorder titledBorder = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.gray), text);
        titledBorder.setTitleJustification(side);
        titledBorder.setTitleFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        setBorder(titledBorder);
    }

    public Panel(final boolean border) {
        setMigLayout(defaultLayout, defaultCols, defaultRows);
        if (border) {
            setBorder(BorderFactory.createLineBorder(Color.gray));
        }
    }

    public Panel(final CheckBox checkBox) {
        setMigLayout(defaultLayout, defaultCols, defaultRows);
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

    public static void setMigLayoutDefault(final String layout, final String cols, final String rows) {
        Panel.defaultLayout = layout;
        Panel.defaultCols = cols;
        Panel.defaultRows = rows;
    }

}
