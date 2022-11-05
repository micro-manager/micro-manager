/*
 * Project: ASI Ring TIRF Control
 * License: BSD 3-clause, see LICENSE.md
 * Author: Brandon Simpson (brandon@asiimaging.com)
 * Copyright (c) 2022, Applied Scientific Instrumentation
 */
package com.asiimaging.tirf.ui.components;

import javax.swing.JLabel;

import java.awt.*;

public class Label extends JLabel {

    private static Color defaultTextColor = Color.WHITE;

    private static int defaultFontSize = 12;
    private static int defaultFontStyle = Font.PLAIN;
    private static String defaultFontFamily = Font.SANS_SERIF;

    public Label(final String text) {
        super(text);
        setForeground(defaultTextColor);
        setFont(new Font(defaultFontFamily, defaultFontStyle, defaultFontSize));
    }

    public Label(final String text, final int size) {
        super(text);
        setForeground(defaultTextColor);
        setFont(new Font(defaultFontFamily, defaultFontStyle, size));
    }

    public Label(final String text, final int style, final int size) {
        super(text);
        setForeground(defaultTextColor);
        setFont(new Font(defaultFontFamily, style, size));
    }

    public Label(final String text, final String name, final int style, final int size) {
        super(text);
        setForeground(defaultTextColor);
        setFont(new Font(name, style, size));
    }

    public void setDefaultTextColor(final Color color) {
        defaultTextColor = color;
    }
    public void setDefaultFontSize(final int size) {
        defaultFontSize = size;
    }

    public void setDefaultFontStyle(final int style) {
        defaultFontStyle = style;
    }

    public void setDefaultFontFamily(final String name) {
        defaultFontFamily = name;
    }
}
