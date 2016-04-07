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

   public static void addAlert(Studio studio, String text) {
      Alert alert = new Alert(studio, text);

      allAlerts_.add(alert);
      adjustPositions();
      alert.setVisible(true);
   }

   private Studio studio_;
   private String text_;

   private Alert(Studio studio, String text) {
      super();
      studio_ = studio;
      text_ = text;
      setUndecorated(true);
      setResizable(false);
      setModal(false);
      setFocusableWindowState(false);
      JPanel contents = new JPanel(new MigLayout());
      contents.setBorder(BorderFactory.createLineBorder(Color.BLACK));
      contents.add(new JLabel(text));
      contents.addMouseListener(new MouseAdapter() {
         @Override
         public void mouseReleased(MouseEvent e) {
            allAlerts_.remove(Alert.this);
            dispose();
            adjustPositions();
         }
      });
      setContentPane(contents);
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
      for (Alert alert : allAlerts_) {
         Dimension ourSize = alert.getSize();
         alert.setLocation(screenBounds.x + screenBounds.width -
                  ourSize.width - insets.right - 5,
               screenBounds.y + insets.top + 5 + yOffset);
         Rectangle bounds = alert.getBounds();
         yOffset = bounds.y + bounds.height;
      }
   }
}
