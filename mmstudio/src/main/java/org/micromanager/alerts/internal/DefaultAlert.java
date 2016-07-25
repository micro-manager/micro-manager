///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger, 2016
//
// COPYRIGHT:    (c) 2016 Open Imaging, Inc.
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

package org.micromanager.alerts.internal;

import com.bulenkov.iconloader.IconLoader;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.GraphicsConfiguration;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;

import java.util.ArrayList;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

import net.miginfocom.swing.MigLayout;

import org.micromanager.Studio;

import org.micromanager.alerts.Alert;
import org.micromanager.internal.utils.GUIUtils;

public class DefaultAlert extends Alert {
   private static ArrayList<DefaultAlert> allAlerts_ = new ArrayList<DefaultAlert>();
   // This is shown if there's not enough room to show all alerts.
   // Note we can't use addAlert to create this one, as addAlert calls
   // adjustPositions, which refers to moreAlert_. Plus we don't want
   // moreAlert_ to be in the allAlerts_ list.
   private static DefaultAlert moreAlert_;
   static {
      JPanel contents = new JPanel(new MigLayout());
      contents.add(new JLabel("And more..."));
      moreAlert_ = new DefaultAlert(null, contents, true);
   }

   /**
    * Create a simple alert with a text message.
    */
   public static DefaultAlert addOneShotAlert(Studio studio, String text) {
      JPanel contents = new JPanel(new MigLayout());
      contents.add(new JLabel(text));
      return addAlert(studio, contents, true);
   }

   /**
    * Create a custom alert with any contents
    */
   public static DefaultAlert addAlert(Studio studio, JPanel contents,
         boolean isOneShot) {
      DefaultAlert alert = new DefaultAlert(studio, contents, isOneShot);
      allAlerts_.add(alert);
      adjustPositions();
      return alert;
   }

   /**
    * Variant of the above specifically for MultiTextAlerts.
    */
   public static MultiTextAlert addTextAlert(Studio studio) {
      MultiTextAlert alert = MultiTextAlert.addAlert(studio);
      allAlerts_.add(alert);
      adjustPositions();
      return alert;
   }

   private Studio studio_;
   private JPanel wrapper_;
   private JPanel contents_;
   private boolean isUsable_ = true;
   private boolean isOneShot_;

   /**
    * @param isOneShot: if true, then clicking on the alert dismisses it;
    * if false, we add a specific button for dismissing the alert instead.
    */
   protected DefaultAlert(Studio studio, JPanel contents, boolean isOneShot) {
      super();
      studio_ = studio;
      isOneShot_ = isOneShot;
      contents_ = contents;
      // Wrap contents in a JPanel that uses MigLayout; this is used for the
      // "non-one-shot" close button positioning.
      wrapper_ = new JPanel(
            new MigLayout("insets 1, gap 0, fill", "[fill, grow]", "[fill, grow]"));
      wrapper_.add(contents_, "grow");
      layout(wrapper_);
   }

   /**
    * Note that due to logic in the constructor, we are guaranteed that
    * contents uses MigLayout for layout.
    */
   private void layout(JPanel contents) {
      setUndecorated(true);
      setResizable(false);
      setModal(false);
      setFocusableWindowState(false);
      wrapper_.setBorder(BorderFactory.createLineBorder(Color.BLACK));
      if (this != moreAlert_) {
         // Show a close button when the dialog is moused over, and dismiss
         // the dialog when the button is clicked on. This is rendered a bit
         // trickier by the fact that the mouse entering the button means it is
         // leaving the contents panel...
         final JButton closeButton = new JButton(
               IconLoader.getIcon("/org/micromanager/icons/cancel.png"));
         closeButton.setContentAreaFilled(false);
         closeButton.setBorderPainted(false);
         closeButton.setBorder(null);
         closeButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
               dispose();
            }
         });
         MouseAdapter adapter = new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
               closeButton.setVisible(true);
            }
            @Override
            public void mouseExited(MouseEvent e) {
               if (!closeButton.getBounds().contains(e.getX(), e.getY())) {
                  closeButton.setVisible(false);
               }
            }
         };
         closeButton.setVisible(false);
         wrapper_.addMouseListener(adapter);
         closeButton.addMouseListener(adapter);
         // Position the button in the upper-right corner of the panel.
         wrapper_.add(closeButton, "hidemode 3, pos null 5 (container.x2 - 5) null");
         // Draw the close button on top of everything.
         wrapper_.setComponentZOrder(closeButton, 0);
      }
      setContentPane(wrapper_);
      pack();
   }

   /**
    * This may happen "from the outside" due to additions to the contents of
    * the alert, and requires that we fix our position afterwards.
    */
   @Override
   public void pack() {
      super.pack();
      if (isVisible()) {
         adjustPositions();
      }
   }

   /**
    * On disposal, we need to rearrange other alerts.
    */
   @Override
   public void dispose() {
      isUsable_ = false;
      allAlerts_.remove(this);
      super.dispose();
      adjustPositions();
   }

   @Override
   public void dismiss() {
      dispose();
   }

   /**
    * Returns whether or not this alert can have more content added to it.
    */
   @Override
   public boolean isUsable() {
      return isUsable_;
   }

   public JPanel getContents() {
      return contents_;
   }

   /**
    * Fix the positions of each Alert.
    */
   private static void adjustPositions() {
      // TODO: we should be on a consistent display, say the one that contains
      // the main window?
      GraphicsConfiguration config = GUIUtils.getGraphicsConfigurationContaining(0, 0);
      Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(config);
      Rectangle screenBounds = config.getBounds();
      int yOffset = 0;
      boolean isVisible = true;
      for (DefaultAlert alert : allAlerts_) {
         if (yOffset + alert.getSize().height + 50 > screenBounds.height - (insets.top + insets.bottom)) {
            // Not enough room to show this and future alerts.
            isVisible = false;
         }
         alert.setVisible(isVisible);
         if (!isVisible) {
            continue;
         }
         Dimension ourSize = alert.getSize();
         alert.setLocation(screenBounds.x + screenBounds.width -
                  ourSize.width - insets.right - 5,
               screenBounds.y + insets.top + 5 + yOffset);
         Rectangle bounds = alert.getBounds();
         yOffset = bounds.y + bounds.height;
      }
      // Show the "more alert" only if necessary.
      moreAlert_.setVisible(!isVisible);
      if (!isVisible) {
         Dimension moreSize = moreAlert_.getSize();
         moreAlert_.setLocation(screenBounds.x + screenBounds.width -
               moreSize.width - insets.right - 5,
            screenBounds.y + screenBounds.height - insets.bottom - 5 -
               moreSize.height);
      }
   }
}
