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
   private JPanel contents_;
   private boolean isUsable_ = true;
   protected MouseAdapter showCloseButtonAdapter_;

   /**
    */
   protected DefaultAlert(AlertsWindow parent, JPanel contents) {
      super();
      setLayout(new MigLayout("flowx, fill, insets 1, gap 0", "[]2[]"));
      contents.setBorder(BorderFactory.createLineBorder(Color.BLACK));
      parent_ = parent;
      contents_ = contents;
      add(contents_, "grow");

      // Show a close button in the top-right, to dismiss the panel.
      final JButton closeButton = new JButton(
            IconLoader.getIcon("/org/micromanager/icons/cancel.png"));
      closeButton.setContentAreaFilled(false);
      closeButton.setBorderPainted(false);
      closeButton.setBorder(null);
      closeButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            dismiss();
         }
      });
      add(closeButton, "span, split, flowy, gapbottom push");
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
