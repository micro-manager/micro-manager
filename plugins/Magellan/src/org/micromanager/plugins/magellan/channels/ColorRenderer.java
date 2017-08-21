///////////////////////////////////////////////////////////////////////////////
//FILE:          ColorRenderer.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nenad Amodaj, nenad@amodaj.com, November 10, 2005
//
// COPYRIGHT:    University of California, San Francisco, 2006
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
//
// CVS:          $Id: ColorRenderer.java 12224 2013-11-27 07:20:28Z nico $
//
package org.micromanager.plugins.magellan.channels;

import java.awt.Color;
import java.awt.Component;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.border.Border;
import javax.swing.table.TableCellRenderer;

/**
 * Color chooser cell renderer. 
 */
public class ColorRenderer extends JLabel implements TableCellRenderer {
   private static final long serialVersionUID = -2539377758420096159L;
   Border unselectedBorder = null;
    Border selectedBorder = null;
    boolean isBordered = true;

    public ColorRenderer(boolean isBordered) {
        this.isBordered = isBordered;
        setOpaque(true); //MUST do this for background to show up.
    }

   @Override
    public Component getTableCellRendererComponent(
                            JTable table, Object color,
                            boolean isSelected, boolean hasFocus,
                            int row, int column) {

        // https://stackoverflow.com/a/3055930
        if (color == null) {
           return null;
        }

        Color newColor = (Color)color;
        if (table.isEnabled()) {
            setBackground(newColor);
        } else {
            Color dimColor = mixColors(newColor,table.getBackground(),0.5);
            setBackground(dimColor);
        }
        if (isBordered) {
            if (isSelected) {
                if (selectedBorder == null) {
                    selectedBorder = BorderFactory.createMatteBorder(2,5,2,5,
                                              table.getSelectionBackground());
                }
                setBorder(selectedBorder);
            } else {
                if (unselectedBorder == null) {
                    unselectedBorder = BorderFactory.createMatteBorder(2,5,2,5,
                                              table.getBackground());
                }
                setBorder(unselectedBorder);
            }
        }
        
        setToolTipText("RGB value: " + newColor.getRed() + ", "
                                     + newColor.getGreen() + ", "
                                     + newColor.getBlue());
        return this;
    }

    private Color mixColors(Color fgColor, Color bgColor, double transparency) {
        return new Color( (int) (fgColor.getRed() * transparency + bgColor.getRed() * (1-transparency)),
                          (int) (fgColor.getGreen() * transparency + bgColor.getGreen() * (1-transparency)),
                          (int) (fgColor.getBlue() * transparency + bgColor.getBlue() * (1-transparency))
                          );
    }
}
