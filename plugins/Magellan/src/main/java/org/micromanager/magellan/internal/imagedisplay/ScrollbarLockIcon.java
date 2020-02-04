///////////////////////////////////////////////////////////////////////////////
// AUTHOR:       Henry Pinkard, henry.pinkard@gmail.com
//
// COPYRIGHT:    University of California, San Francisco, 2015
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
package org.micromanager.magellan.internal.imagedisplay;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import javax.swing.JComponent;
import javax.swing.event.MouseInputAdapter;
import org.micromanager.magellan.internal.imagedisplay.MagellanDisplayController;
import org.micromanager.magellan.internal.imagedisplay.events.DisplayClosingEvent;
import org.micromanager.magellan.internal.imagedisplay.events.LockEvent;

/**
 *
 * @author Henry
 */
public class ScrollbarLockIcon extends JComponent   {

   /**
    * This enum tracks the possible states of the lock icon. The difference
    * between "locked" and "superlocked" is that in the locked state, we will
    * still flash the display to newly-acquired images for a brief period.
    */
   public enum LockedState {
      UNLOCKED, SUPERLOCKED
   }


   private static final int WIDTH = 17, HEIGHT = 14;
   private LockedState lockedState_;
   private String axis_;
   private final Color BACKGROUND_COLOR = Color.white;
   private final Color LOCK_COLOR = Color.black;
   private final Color SUPERLOCK_COLOR = Color.red;
   private Color foreground_ = LOCK_COLOR;
   private MagellanDisplayController display_;
   
   public ScrollbarLockIcon(MagellanDisplayController disp, final String axis) {
      lockedState_ = LockedState.UNLOCKED;
      axis_ = axis;
      setSize(WIDTH, HEIGHT);
      this.setToolTipText("Lock the scrollbar to its current postion");
      this.addMouseListener(new MouseInputAdapter() {
         @Override
         public void mouseClicked(MouseEvent e) {
            advanceLockedState();
         }
      });
      display_ = disp;
      display_.registerForEvents(this);
   }
   
   @Subscribe
   public void onDisplayClose(DisplayClosingEvent e) {
      for (MouseListener l : this.getMouseListeners()) {
         this.removeMouseListener(l);
      }
      lockedState_ = null;
      display_.unregisterForEvents(this);
      display_ = null;
   }

   private void advanceLockedState() {
      switch (lockedState_) {
         case UNLOCKED:
            setLockedState(LockedState.SUPERLOCKED);
            break;
         default:
            setLockedState(LockedState.UNLOCKED);
            break;
      }
   }

   public void setLockedState(LockedState state) {
      lockedState_ = state;
      foreground_ = (lockedState_ == LockedState.SUPERLOCKED) ? SUPERLOCK_COLOR : LOCK_COLOR;
      display_.postEvent(new LockEvent(axis_, lockedState_));
      repaint();
   }

   public LockedState getLockedState() {
      return lockedState_;
   }

   /**
    * Return true if we are in LOCKED or SUPERLOCKED state.
    */
   public boolean getIsLocked() {
      return (lockedState_ == LockedState.SUPERLOCKED);
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
      if (lockedState_ == LockedState.UNLOCKED) {
         drawUnlocked(g2d);
      } 
      else {
         drawLocked(g2d);
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
      g.setColor(BACKGROUND_COLOR);
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
      g.setColor(BACKGROUND_COLOR);
      g.fillArc(4, 3, 4, 4, 0, 180);
   }
}

