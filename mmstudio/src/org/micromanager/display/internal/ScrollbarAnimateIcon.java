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

import java.awt.Dimension;
import java.awt.event.MouseEvent;

import javax.swing.event.MouseInputAdapter;
import javax.swing.Icon;
import javax.swing.JButton;

/**
 * This class displays a little play/pause icon with a single-character label,
 * and is used for handling animation of an AxisScroller.
 */
public class ScrollbarAnimateIcon extends JButton {
   private static final int BUTTON_WIDTH = 30;
   private static final int BUTTON_HEIGHT = 18;
   private static final Icon PLAY_ICON = IconLoader.getIcon(
         "/org/micromanager/internal/icons/play.png");
   private static final Icon PAUSE_ICON = IconLoader.getIcon(
         "/org/micromanager/internal/icons/pause.png");
   private final String label_;
   private boolean isAnimated_;

   public ScrollbarAnimateIcon(final String axis, final ScrollerPanel parent) {
      super(PLAY_ICON);
      setSize(BUTTON_WIDTH, BUTTON_HEIGHT);
      // Only use the first letter of the axis.
      label_ = axis.substring(0, 1);
      setText(label_);
      isAnimated_ = false;
      setToolTipText("Toggle animation of the " + axis + " axis.");
      addMouseListener(new MouseInputAdapter() {
         @Override
         public void mousePressed(MouseEvent e) {
            parent.toggleAnimation(axis);
            setIsAnimated(!isAnimated_);
         }
      });
   }

   public void setIsAnimated(boolean isAnimated) {
      isAnimated_ = isAnimated;
      setIcon(isAnimated_ ? PAUSE_ICON : PLAY_ICON);
   }

   public boolean getIsAnimated() {
      return isAnimated_;
   }

   /** Don't require more space than is needed to show the icon. */
   @Override
   public Dimension getPreferredSize() {
      return new Dimension(BUTTON_WIDTH, BUTTON_HEIGHT);
   }
   @Override
   public Dimension getMinimumSize() {
      return new Dimension(BUTTON_WIDTH, BUTTON_HEIGHT);
   }
}

