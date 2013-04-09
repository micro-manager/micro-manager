package org.micromanager.acquisition;

import java.awt.BasicStroke;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Event;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.geom.GeneralPath;
import javax.swing.JComponent;
import org.micromanager.utils.TooltipTextMaker;

/**
 *
 * @author Henry
 */
public class ScrollbarLockIcon extends JComponent   {

   private static final int WIDTH = 17, HEIGHT = 14;
   private VirtualAcquisitionDisplay virtAcq_;
   private String label_;

   public ScrollbarLockIcon(VirtualAcquisitionDisplay vad, String label) {
      virtAcq_ = vad;
      label_ = label;
      setSize(WIDTH, HEIGHT);
      this.setToolTipText(TooltipTextMaker.addHTMLBreaksForTooltip(
              "Lock the scrollbar to its current postion while acquisition is running"));
   }

   /**
    * Overrides Component getPreferredSize().
    */
   public Dimension getPreferredSize() {
      return new Dimension(WIDTH, HEIGHT);
   }
   
   public Dimension getMinimumSize() {
       return new Dimension(WIDTH, HEIGHT);
   }
   
    public Dimension getMaximumSize() {
       return new Dimension(WIDTH, HEIGHT);
   }

   public void paint(Graphics g) {
      if (g == null) {
         return;
      }
      g.setColor(Color.white);
      g.fillRect(0, 0, WIDTH, HEIGHT);
      Graphics2D g2d = (Graphics2D) g;
      g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      if (virtAcq_.isScrollbarLocked(label_)) {
         drawLocked(g2d);
      } else {
         drawUnlocked(g2d);
      }
   }
   
   private void drawUnlocked(Graphics2D g) {
      g.setColor(Color.black);
      //body
      g.fillRect(1, 7, 10, 6);

      //lock part
      g.fillRect(8, 4, 2, 3);
      g.fillRect(14, 4, 2, 3);

      g.fillArc(8, 1, 8, 8, 0, 180);
      g.setColor(Color.white);
      g.fillArc(10, 3, 4, 4, 0, 180);
   }

   private void drawLocked(Graphics2D g) {
         g.setColor(Color.black);   
         //body
         g.fillRect(1, 7, 10, 6);
         
         //lock part
         g.fillRect(2, 4, 2, 3);
         g.fillRect(8, 4, 2, 3);
         
         g.fillArc(2, 1, 8, 8, 0, 180);
         g.setColor(Color.white);
         g.fillArc(4, 3, 4, 4, 0, 180);
   }
}
