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

package org.micromanager.internal.utils;

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
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;

import net.miginfocom.swing.MigLayout;

import org.micromanager.Studio;

/**
 * An Alert is a temporary, undecorated dialog that shows up along the side of
 * the screen for information that doesn't need to interrupt the user.
 */
public class Alert extends JDialog {
   private static ArrayList<Alert> allAlerts_ = new ArrayList<Alert>();
   // This is shown if there's not enough room to show all alerts.
   // Note we can't use addAlert to create this one, as addAlert calls
   // adjustPositions, which refers to moreAlert_. Plus we don't want
   // moreAlert_ to be in the allAlerts_ list.
   private static Alert moreAlert_;
   static {
      JPanel contents = new JPanel(new MigLayout());
      contents.add(new JLabel("And more..."));
      moreAlert_ = new Alert(null, contents);
   }

   /**
    * Create a simple alert with a text message.
    */
   public static Alert addAlert(Studio studio, String text) {
      JPanel contents = new JPanel(new MigLayout());
      contents.add(new JLabel(text));
      return addAlert(studio, contents);
   }

   /**
    * Create a custom alert with any contents
    */
   public static Alert addAlert(Studio studio, JPanel contents) {
      Alert alert = new Alert(studio, contents);
      allAlerts_.add(alert);
      adjustPositions();
      return alert;
   }

   private Studio studio_;
   private JPanel contents_;

   private Alert(Studio studio, JPanel contents) {
      super();
      studio_ = studio;
      layout(contents);
   }

   private void layout(JPanel contents) {
      contents_ = contents;
      setUndecorated(true);
      setResizable(false);
      setModal(false);
      setFocusableWindowState(false);
      contents_.setBorder(BorderFactory.createLineBorder(Color.BLACK));
      if (this != moreAlert_) {
         contents_.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
               allAlerts_.remove(Alert.this);
               dispose();
               adjustPositions();
            }
         });
      }
      setContentPane(contents_);
      pack();
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
      for (Alert alert : allAlerts_) {
         if (yOffset + 200 > screenBounds.height - (insets.top + insets.bottom)) {
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
