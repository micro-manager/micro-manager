
package org.micromanager.imagedisplay;

import java.awt.BasicStroke;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.GeneralPath;

/**
 * This class displays a little play/pause icon with a single-character label,
 * and is used for handling animation of an AxisScroller.
 */
public class ScrollbarAnimateIcon extends Canvas {
   private static final int WIDTH = 24, HEIGHT = 14;
   private BasicStroke stroke = new BasicStroke(2f);
   private String label_;
   private boolean isAnimated_;

   public ScrollbarAnimateIcon(String axis) {
      setSize(WIDTH, HEIGHT);
      // Only use the first letter of the axis.
      label_ = axis.substring(0, 1);
      isAnimated_ = false;
   }

   public void setIsAnimated(boolean isAnimated) {
      isAnimated_ = isAnimated;
      repaint();
   }

   public boolean getIsAnimated() {
      return isAnimated_;
   }

   /** Overrides Component getPreferredSize(). */
   @Override
   public Dimension getPreferredSize() {
      return new Dimension(WIDTH, HEIGHT);
   }
   
   @Override
   public void paint(Graphics g) {
      g.setColor(Color.white);
      g.fillRect(0, 0, WIDTH, HEIGHT);
      Graphics2D g2d = (Graphics2D)g;
      g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      drawPlayPauseButton(g2d);
      drawLetter(g);
   }
   
   private void drawCenteredLetter(Graphics g) {
      g.setFont(new Font("SansSerif", Font.PLAIN, 14));
      g.setColor(Color.black);
      g.drawString(label_, 8, 12);
   }
   
   private void drawLetter(Graphics g) {
      g.setFont(new Font("SansSerif", Font.PLAIN, 14));
      g.setColor(Color.black);
      g.drawString(label_, 4, 12);
   }

   private void drawPlayPauseButton(Graphics2D g) {
      if (isAnimated_) {
         // Draw a pause button
         g.setColor(Color.red);
         g.setStroke(stroke);
         g.drawLine(15, 3, 15, 11);
         g.drawLine(20, 3, 20, 11);
      } else {
         // Draw a play button
         g.setColor(new Color(0,150,0));
         GeneralPath path = new GeneralPath();
         path.moveTo(15f, 2f);
         path.lineTo(22f, 7f);
         path.lineTo(15f, 12f);
         path.lineTo(15f, 2f);
         g.fill(path);
      }
   }
   }

