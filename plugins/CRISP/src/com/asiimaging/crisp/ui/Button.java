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

import java.awt.Dimension;
import java.awt.Font;

import javax.swing.JButton;

@SuppressWarnings("serial")
public class Button extends JButton {
    
    private static int defaultWidth = 100;
    private static int defaultHeight = 100;
    
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
    
}
