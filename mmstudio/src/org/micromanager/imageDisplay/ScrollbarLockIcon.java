package org.micromanager.imageDisplay;

import com.google.common.eventbus.EventBus;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;

import javax.swing.event.MouseInputAdapter;
import javax.swing.JComponent;
import org.micromanager.utils.TooltipTextMaker;

/**
 *
 * @author Henry
 */
public class ScrollbarLockIcon extends JComponent   {

   /**
    * This event informs listeners of when the lock button is toggled.
    */
   public class LockEvent {
      private String axis_;
      private boolean isLocked_;
      public LockEvent(String axis, boolean isLocked) {
         axis_ = axis;
         isLocked_ = isLocked;
      }
      public String getAxis() {
         return axis_;
      }
      public boolean getIsLocked() {
         return isLocked_;
      }
   }

   private static final int WIDTH = 17, HEIGHT = 14;
   private boolean isLocked_;
   private Color foreground_ = Color.black, background_ = Color.white;
   
   public ScrollbarLockIcon(final String axis, final EventBus bus) {
      isLocked_ = false;
      setSize(WIDTH, HEIGHT);
      this.setToolTipText(TooltipTextMaker.addHTMLBreaksForTooltip(
              "Lock the scrollbar to its current postion"));
      this.addMouseListener(new MouseInputAdapter() {
         @Override
         public void mouseClicked(MouseEvent e) {
            isLocked_ = !isLocked_;
            bus.post(new LockEvent(axis, isLocked_));
            repaint();
         }
         @Override
         public void mouseEntered(MouseEvent e) {
            foreground_ = Color.blue;
            repaint();
         }
         @Override
         public void mouseExited(MouseEvent e) {
            foreground_ = Color.black;
            repaint();
         }         
      });
   }

   public boolean getIsLocked() {
      return isLocked_;
   }

   /**
    * Overrides Component getPreferredSize().
    */
   @Override
   public Dimension getPreferredSize() {
      return new Dimension(WIDTH, HEIGHT);
   }
   
   @Override
   public Dimension getMinimumSize() {
       return new Dimension(WIDTH, HEIGHT);
   }
   
   @Override
    public Dimension getMaximumSize() {
       return new Dimension(WIDTH, HEIGHT);
   }

   @Override
   public void paint(Graphics g) {
      if (g == null) {
         return;
      }
      g.setColor(Color.white);
      g.fillRect(0, 0, WIDTH, HEIGHT);
      Graphics2D g2d = (Graphics2D) g;
      g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      if (isLocked_) {
         drawLocked(g2d);
      } else {
         drawUnlocked(g2d);
      }
   }
   
   private void drawUnlocked(Graphics2D g) {
      g.setColor(foreground_);
      //body
      g.fillRect(1, 7, 10, 6);

      //lock part
      g.fillRect(8, 4, 2, 3);
      g.fillRect(14, 4, 2, 3);

      g.fillArc(8, 1, 8, 8, 0, 180);
      g.setColor(background_);
      g.fillArc(10, 3, 4, 4, 0, 180);
   }

   private void drawLocked(Graphics2D g) {
      g.setColor(foreground_);   
      //body
      g.fillRect(1, 7, 10, 6);
      
      //lock part
      g.fillRect(2, 4, 2, 3);
      g.fillRect(8, 4, 2, 3);
      
      g.fillArc(2, 1, 8, 8, 0, 180);
      g.setColor(background_);
      g.fillArc(4, 3, 4, 4, 0, 180);
   }
}
