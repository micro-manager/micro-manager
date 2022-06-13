package de.embl.rieslab.emu.configuration.ui.utils;

//
//
//ColorIcon
//
//Copyright (C) by Andrea Carboni.
//This file may be distributed under the terms of the LGPL license.
//
//

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Insets;
import javax.swing.Icon;

/**
 * Defines an colored icon.
 *
 * @author Andrea Carboni
 */
public class ColorIcon implements Icon {
   private final int iWidth;
   private final int iHeight;

   private Color color;
   private Color border;
   private final Insets insets;

   // ---------------------------------------------------------------------------

   public ColorIcon() {
      this(32, 16);
   }

   // ---------------------------------------------------------------------------

   public ColorIcon(int width, int height) {
      this(width, height, Color.black);
   }

   public ColorIcon(Color value) {
      this(32, 16, value);
   }

   // ---------------------------------------------------------------------------

   public ColorIcon(int width, int height, Color c) {
      iWidth = width;
      iHeight = height;

      color = c;
      border = Color.black;
      insets = new Insets(1, 1, 1, 1);
   }

   // ---------------------------------------------------------------------------

   public Color getColor() {
      return color;
   }

   // ---------------------------------------------------------------------------

   public void setColor(Color c) {
      color = c;
   }

   // ---------------------------------------------------------------------------

   public void setBorderColor(Color c) {
      border = c;
   }

   // ---------------------------------------------------------------------------
   // ---
   // --- Icon interface methods
   // ---
   // ---------------------------------------------------------------------------

   public int getIconWidth() {
      return iWidth;
   }

   // ---------------------------------------------------------------------------

   public int getIconHeight() {
      return iHeight;
   }

   // ---------------------------------------------------------------------------

   public void paintIcon(Component c, Graphics g, int x, int y) {
      g.setColor(border);
      g.drawRect(x, y, iWidth - 1, iHeight - 2);

      x += insets.left;
      y += insets.top;

      int w = iWidth - insets.left - insets.right;
      int h = iHeight - insets.top - insets.bottom - 1;

      g.setColor(color);
      g.fillRect(x, y, w, h);
   }
}
