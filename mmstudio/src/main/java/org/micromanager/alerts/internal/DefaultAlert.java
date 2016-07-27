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

   protected AlertsWindow parent_;
   private JPanel wrapper_;
   private JPanel contents_;
   private boolean isUsable_ = true;
   protected MouseAdapter showCloseButtonAdapter_;

   /**
    */
   protected DefaultAlert(AlertsWindow parent, JPanel contents) {
      super();
      setLayout(new MigLayout("fill, insets 0, gap 0"));
      parent_ = parent;
      contents_ = contents;
      // Wrap contents in a JPanel that uses MigLayout; this is used for the
      // close button positioning.
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
      wrapper_.setBorder(BorderFactory.createLineBorder(Color.BLACK));
      // Show a close button when the panel is moused over, and dismiss
      // the panel when the button is clicked on. This is rendered a bit
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
            parent_.removeAlert(DefaultAlert.this);
         }
      });
      showCloseButtonAdapter_ = new MouseAdapter() {
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
      wrapper_.addMouseListener(showCloseButtonAdapter_);
      closeButton.addMouseListener(showCloseButtonAdapter_);
      // Position the button in the upper-right corner of the panel.
      wrapper_.add(closeButton, "hidemode 3, pos null 5 (container.x2 - 5) null");
      // Draw the close button on top of everything.
      wrapper_.setComponentZOrder(closeButton, 0);
      add(wrapper_, "grow");
      parent_.pack();
   }

   @Override
   public void relayout() {
      parent_.pack();
   }

   @Override
   public void dismiss() {
      isUsable_ = false;
      parent_.removeAlert(this);
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
}
