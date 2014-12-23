package org.micromanager.imagedisplay;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;

import java.lang.Math;

import javax.swing.JToggleButton;

/**
 * This class is simply a JToggleButton whose text is rotated 90 degrees.
 */
class VerticalButton extends JToggleButton {
   private String label_;
   /**
    * Create a standard toggle button with label, so that it derives an
    * appropriate size, then blank the label and flip the size so width is
    * height and vice versa.
    */
   public VerticalButton(String label) {
      super(label);
      Dimension size = getMinimumSize();
      setText("");
      setMinimumSize(new Dimension(size.height, size.width));
      // Don't grow the button's width; it looks weird.
      setMaximumSize(new Dimension(size.height, 32767));
      label_ = label;
   }

   /**
    * We paint the normal toggle button, of course, but then we also paint
    * the label, rotated 90 degrees.
    */
   @Override
   public void paint(Graphics g) {
      super.paint(g);
      Graphics2D g2d = (Graphics2D) g.create();
      // Derive the horizontal and vertical positions based on the 
      // dimensions of the button and the font statistics.
      Rectangle bounds = getBounds();
      double xCenter = bounds.width / 2.0;
      double yCenter = bounds.height / 2.0;
      Font font = getFont();
      Rectangle2D labelBounds = font.getStringBounds(label_, 
            new FontRenderContext(new AffineTransform(), true, false));

      // Note swapping of width/height due to the 90-degree rotation.
      // Unsure why we need a divisor of 4 for the width, but it looks off
      // if we use 2.
      double xOffset = xCenter - (labelBounds.getHeight() / 4.0);
      double yOffset = yCenter - (labelBounds.getWidth() / 2.0);
      g2d.translate(xOffset, yOffset);
      g2d.rotate(Math.PI / 2);
      g2d.setColor(Color.black);
      g2d.drawString(label_, 0, 0);
   }
}
