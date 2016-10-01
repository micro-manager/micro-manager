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
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.event.MouseInputAdapter;

/**
 * This class displays a little play/pause icon with a single-character label,
 * and is used for handling animation of an AxisScroller.
 */
public final class ScrollbarAnimateIcon extends JButton {
   // HACK: This is intentionally wider than it "needs" to be because otherwise
   // we sometimes end up with the text portion of the button getting
   // "truncated" into an ellipsis, even though that's actually wider than the
   // text would be otherwise! I don't understand operating systems sometimes.
   private static final int BUTTON_WIDTH = 40;
   private static final int BUTTON_HEIGHT = 18;
   private static final Icon PLAY_ICON = IconLoader.getIcon(
         "/org/micromanager/icons/play.png");
   private static final Icon PAUSE_ICON = IconLoader.getIcon(
         "/org/micromanager/icons/pause.png");
   private static final Font LABEL_FONT = new Font("Arial", Font.PLAIN, 10);
   private final String label_;
   private boolean isAnimated_;
   private Icon curIcon_;

   public ScrollbarAnimateIcon(final String axis, final ScrollerPanel parent) {
      super();
      curIcon_ = PLAY_ICON;
      super.setSize(BUTTON_WIDTH, BUTTON_HEIGHT);
      // Only use the first letter of the axis.
      label_ = axis.substring(0, 1);
      isAnimated_ = false;
      super.setToolTipText("Toggle animation of the " + axis + " axis.");
      super.addMouseListener(new MouseInputAdapter() {
         @Override
         public void mousePressed(MouseEvent e) {
            parent.toggleAnimation(axis);
            setIsAnimated(!isAnimated_);
         }
      });
   }

   /**
    * HACK: override the paint() method and manually paint our label, because
    * otherwise Windows has a tendency to "truncate" our label down to an
    * ellipsis (even though the ellipsis is wider than the original label).
    */
   @Override
   public void paint(Graphics g) {
      // Paint the normal button decorations.
      super.paint(g);

      // Paint the icon. We do this instead of using setIcon() and having
      // Swing paint it because we want the icon to be left-justified.
      Graphics2D g2d = (Graphics2D) g;
      int yOffset = (BUTTON_HEIGHT - curIcon_.getIconHeight()) / 2;
      curIcon_.paintIcon(this, g2d, 5, yOffset);

      // Paint the label.
      g2d.setFont(LABEL_FONT);
      g2d.setColor(Color.BLACK);
      // HACK: manually-derived decent-looking offsets.
      g2d.drawString(label_, 20, 13);
   }

   public void setIsAnimated(boolean isAnimated) {
      isAnimated_ = isAnimated;
      curIcon_ = isAnimated_ ? PAUSE_ICON : PLAY_ICON;
   }

   public boolean getIsAnimated() {
      return isAnimated_;
   }

   @Override
   public Dimension getPreferredSize() {
      return new Dimension(BUTTON_WIDTH, BUTTON_HEIGHT);
   }
   @Override
   public Dimension getMinimumSize() {
      return new Dimension(BUTTON_WIDTH, BUTTON_HEIGHT);
   }
}

