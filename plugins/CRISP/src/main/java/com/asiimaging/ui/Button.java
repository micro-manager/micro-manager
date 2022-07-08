/*
 * Project: ASI CRISP Control
 * License: BSD 3-clause, see LICENSE.md
 * Author: Brandon Simpson (brandon@asiimaging.com)
 * Copyright (c) 2014-2021, Applied Scientific Instrumentation
 */
package com.asiimaging.ui;

import java.awt.Dimension;
import java.awt.Font;

import javax.swing.Icon;
import javax.swing.JButton;

public class Button extends JButton {

    private static int defaultWidth = 100;
    private static int defaultHeight = 100;

    public Button(final Icon icon) {
        super(icon);
        setAbsoluteSize(defaultWidth, defaultHeight);
        setFocusPainted(false); // remove highlight when clicked
    }

    public Button(final Icon icon, final int width, final int height) {
        super(icon);
        setAbsoluteSize(width, height);
        setFocusPainted(false); // remove highlight when clicked
    }

    public Button(final String text) {
        super(text);
        setAbsoluteSize(defaultWidth, defaultHeight);
        setFocusPainted(false); // remove highlight when clicked
    }

    public Button(final String text, final int width, final int height) {
        super(text);
        setAbsoluteSize(width, height);
        setFocusPainted(false); // remove highlight when clicked
    }

    public static void setDefaultSize(final int width, final int height) {
        defaultWidth = width;
        defaultHeight = height;
    }

    public void setAbsoluteSize(final int width, final int height) {
        final Dimension size = new Dimension(width, height);
        setPreferredSize(size);
        setMinimumSize(size);
        setMaximumSize(size);
    }

    public void setBoldFont(final int size) {
        setFont(new Font(Font.SANS_SERIF, Font.BOLD, size));
    }

    public void registerListener(final Method method) {
        addActionListener(method::run);
    }
}
