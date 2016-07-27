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
import java.awt.Font;
import java.awt.GraphicsConfiguration;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;

import java.util.ArrayList;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;

import net.miginfocom.swing.MigLayout;

import org.micromanager.Studio;

import org.micromanager.alerts.Alert;
import org.micromanager.internal.utils.GUIUtils;

public class DefaultAlert extends Alert {

   protected AlertsWindow parent_;
   private String title_;
   protected String text_;
   private JComponent contents_;
   private JToggleButton muteButton_;
   private boolean isUsable_ = true;
   protected MouseAdapter showCloseButtonAdapter_;

   /**
    */
   protected DefaultAlert(AlertsWindow parent, String title, JComponent contents) {
      super();
      setLayout(new MigLayout("flowx, fill, insets 1, gap 0", "[]2[]"));

      parent_ = parent;
      title_ = title;
      contents_ = contents;
      // HACK: if contents are a JLabel, store their text.
      if (contents instanceof JLabel) {
         text_ = ((JLabel) contents).getText();
      }

      if (title != null && !title.contentEquals("")) {
         JLabel titleLabel = new JLabel(title);
         titleLabel.setFont(new Font("Arial", Font.BOLD, 12));
         add(titleLabel, "span, wrap");
      }
      contents.setBorder(BorderFactory.createLineBorder(Color.BLACK));
      add(contents_, "grow, pushx 100");

      // Show a close button in the top-right, to dismiss the panel.
      JButton closeButton = new JButton(
            IconLoader.getIcon("/org/micromanager/icons/cancel_gray.png"));
      closeButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            dismiss();
         }
      });
      add(closeButton, "span, split, flowy, width 32!, height 32!");

      // Show a mute button to hide alerts from this source.
      // This icon based on the public-domain icon at
      // https://commons.wikimedia.org/wiki/File:Echo_bell.svg
      muteButton_ = new JToggleButton(
            IconLoader.getIcon("/org/micromanager/icons/bell_mute.png"));
      muteButton_.setToolTipText("Mute this message source, so that it will no longer cause the Messages window to be shown if it recurs");
      muteButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            parent_.setMuted(DefaultAlert.this, muteButton_.isSelected());
         }
      });
      add(muteButton_, "width 32!, height 32!, gapbottom push");
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

   public JComponent getContents() {
      return contents_;
   }

   public String getTitle() {
      return title_;
   }

   public String getText() {
      return text_;
   }

   public void setMuteButtonState(boolean isMuted) {
      muteButton_.setSelected(isMuted);
   }
}
