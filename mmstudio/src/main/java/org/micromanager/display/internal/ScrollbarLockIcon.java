///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Display implementation
//-----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger, 2015
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

package org.micromanager.display.internal;

import com.bulenkov.iconloader.IconLoader;
import com.google.common.eventbus.Subscribe;
import java.awt.Dimension;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import javax.swing.event.MouseInputAdapter;
import org.micromanager.display.DataViewer;
import org.micromanager.internal.utils.TooltipTextMaker;

public final class ScrollbarLockIcon extends JButton {
   /**
    * This enum tracks the possible states of the lock icon. The difference
    * between "locked" and "superlocked" is that in the locked state, we will
    * still flash the display to newly-acquired images for a brief period.
    */
   public enum LockedState {
      UNLOCKED, LOCKED, SUPERLOCKED
   }

   // Paths to icons corresponding to the above.
   // Icons adapted from the public-domain icon here:
   // https://openclipart.org/detail/188461/closed-lock
   // .25x scaling factor gives us 16x16 icons from the 64x64 base images.
   private static final HashMap<LockedState, Icon> stateToIcon_ = new HashMap<LockedState, Icon>();
   static {
      stateToIcon_.put(LockedState.UNLOCKED,
            IconLoader.getIcon(
               "/org/micromanager/icons/lock_open.png"));
      stateToIcon_.put(LockedState.LOCKED,
            IconLoader.getIcon(
               "/org/micromanager/icons/lock_locked.png"));
      stateToIcon_.put(LockedState.SUPERLOCKED,
            IconLoader.getIcon(
               "/org/micromanager/icons/lock_super.png"));
   }

   /**
    * This event informs listeners of when the lock button is toggled.
    */
   public static class LockEvent {
      private final String axis_;
      private final LockedState lockedState_;
      public LockEvent(String axis, LockedState lockedState) {
         axis_ = axis;
         lockedState_ = lockedState;
      }
      public String getAxis() {
         return axis_;
      }
      public LockedState getLockedState() {
         return lockedState_;
      }
   }

   /**
    * This event causes other lock icons to be updated to match our state.
    */
   public static class ForceLockEvent {
      private final LockedState state_;
      public ForceLockEvent(LockedState state) {
         state_ = state;
      }

      public LockedState getState() {
         return state_;
      }
   }

   private LockedState lockedState_;
   private final String axis_;
   private final DataViewer viewer_;

   public ScrollbarLockIcon(final String axis, final DataViewer viewer) {
      // Start out unlocked.
      super(stateToIcon_.get(LockedState.UNLOCKED));
      lockedState_ = LockedState.UNLOCKED;
      axis_ = axis;
      viewer_ = viewer;
      this.setToolTipText(TooltipTextMaker.addHTMLBreaksForTooltip(
              "Lock the scrollbar to its current postion. Click twice to superlock; right-click to update all axes."));
      this.addMouseListener(new MouseInputAdapter() {
         @Override
         public void mouseClicked(MouseEvent e) {
            advanceLockedState(SwingUtilities.isRightMouseButton(e));
         }
      });
      viewer_.registerForEvents(this);
   }

   private void advanceLockedState(boolean shouldBroadcast) {
      switch (lockedState_) {
         case UNLOCKED:
            setLockedState(LockedState.LOCKED);
            break;
         case LOCKED:
            setLockedState(LockedState.SUPERLOCKED);
            break;
         default:
            setLockedState(LockedState.UNLOCKED);
            break;
      }
      if (shouldBroadcast) {
         viewer_.postEvent(new ForceLockEvent(lockedState_));
      }
   }

   public void setLockedState(LockedState state) {
      lockedState_ = state;
      setIcon(stateToIcon_.get(state));
      viewer_.postEvent(new LockEvent(axis_, lockedState_));
      repaint();
   }

   @Subscribe
   public void onForceLock(ForceLockEvent event) {
      setLockedState(event.getState());
   }

   public LockedState getLockedState() {
      return lockedState_;
   }

   /**
    * Return true if we are in LOCKED or SUPERLOCKED state.
    */
   public boolean getIsLocked() {
      return (lockedState_ == LockedState.LOCKED || 
            lockedState_ == LockedState.SUPERLOCKED);
   }

   /**
    * HACK: override this size so we don't take up too much space.
    */
   @Override
   public Dimension getPreferredSize() {
      return new Dimension(20, 16);
   }

   /**
    * HACK: override this size so the layout will allow us to shrink small
    * enough to fit.
    */
   @Override
   public Dimension getMinimumSize() {
      return new Dimension(0, 0);
   }
}
