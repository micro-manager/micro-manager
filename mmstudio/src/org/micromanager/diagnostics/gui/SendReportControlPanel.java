// AUTHOR:       Mark Tsuchida
// COPYRIGHT:    University of California, San Francisco, 2014
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

package org.micromanager.diagnostics.gui;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;


class SendReportControlPanel extends ControlPanel {
   private final ProblemReportController controller_;

   private final JTextField nameField_;
   private final JTextField organizationField_;
   private final JTextField emailField_;

   private final JButton cancelButton_;
   private final JButton startOverButton_;
   private final JButton viewButton_;
   private final JButton sendButton_;

   private final JPanel sendingActivityIndicatorPanel_;
   private final JLabel sendingActivityLabel_;
   private JProgressBar sendingActivityIndicator_;

   enum UIMode {
      UNSENT,
      SENDING,
      SENT,
   }
   UIMode mode_ = UIMode.UNSENT;

   SendReportControlPanel(ProblemReportController controller) {
      this(controller, true);
   }

   SendReportControlPanel(ProblemReportController controller,
         boolean allowRestart) {
      controller_ = controller;

      cancelButton_ = new JButton("Cancel");
      cancelButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            controller_.cancelRequested();
         }
      });

      if (allowRestart) {
         startOverButton_ = new JButton("Start Over");
         startOverButton_.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
               controller_.startLogCapture();
            }
         });
      }
      else {
         startOverButton_ = null;
      }

      viewButton_ = new JButton("View Report");
      viewButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            controller_.displayReport();
         }
      });

      sendButton_ = new JButton("Send Report...");
      sendButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            controller_.sendRequested();
         }
      });

      nameField_ = new JTextField(controller_.getName());
      nameField_.getDocument().addDocumentListener(new DocumentListener() {
         @Override
         public void changedUpdate(DocumentEvent e) {
            controller_.nameChanged(e.getDocument());
         }
         @Override
         public void insertUpdate(DocumentEvent e) {
            controller_.nameChanged(e.getDocument());
         }
         @Override
         public void removeUpdate(DocumentEvent e) {
            controller_.nameChanged(e.getDocument());
         }
      });

      organizationField_ = new JTextField(controller_.getOrganization());
      organizationField_.getDocument().addDocumentListener(new DocumentListener() {
         @Override
         public void changedUpdate(DocumentEvent e) {
            controller_.organizationChanged(e.getDocument());
         }
         @Override
         public void insertUpdate(DocumentEvent e) {
            controller_.organizationChanged(e.getDocument());
         }
         @Override
         public void removeUpdate(DocumentEvent e) {
            controller_.organizationChanged(e.getDocument());
         }
      });

      emailField_ = new JTextField(controller_.getEmail());
      emailField_.getDocument().addDocumentListener(new DocumentListener() {
         @Override
         public void changedUpdate(DocumentEvent e) {
            controller_.emailChanged(e.getDocument());
         }
         @Override
         public void insertUpdate(DocumentEvent e) {
            controller_.emailChanged(e.getDocument());
         }
         @Override
         public void removeUpdate(DocumentEvent e) {
            controller_.emailChanged(e.getDocument());
         }
      });

      sendingActivityIndicatorPanel_ =
         new JPanel(new net.miginfocom.swing.MigLayout("fillx, insets 0"));
      sendingActivityLabel_ = new JLabel("Report not yet sent.");
      sendingActivityLabel_.setEnabled(false);
      sendingActivityIndicatorPanel_.add(sendingActivityLabel_);

      setLayout(new net.miginfocom.swing.MigLayout(
               "fillx, insets 0",
               "[grow 0,label]rel[grow,fill]",
               "[][][]unrel[]"));

      add(new JLabel("Name:"));
      add(nameField_, "wrap");
      add(new JLabel("Organization:"));
      add(organizationField_, "wrap");
      add(new JLabel("Email:"));
      add(emailField_, "wrap");

      if (startOverButton_ != null) {
         add(cancelButton_, "span 2, split 4, sizegroup cancelbtns");
         add(startOverButton_, "gapright push, sizegroup cancelbtns");
      }
      else {
         add(cancelButton_, "span 2, split 3, gapright push, sizegroup cancelbtns");
      }
      add(viewButton_, "sizegroup actionbtns");
      add(sendButton_, "sizegroup actionbtns");

      add(sendingActivityIndicatorPanel_, "newline, span 2");
   }

   void setUIMode(UIMode mode) {
      if (mode == mode_) {
         return;
      }

      boolean editable = false, disabled = true, sendable = false;
      switch (mode) {
         case UNSENT:
            editable = true;
            disabled = false;
            sendable = true;
            break;
         case SENDING:
            editable = false;
            disabled = true;
            sendable = false;
            break;
         case SENT:
            editable = true;
            disabled = false;
            sendable = false;
            break;
      }

      nameField_.setEnabled(editable);
      organizationField_.setEnabled(editable);
      emailField_.setEnabled(editable);

      cancelButton_.setEnabled(!disabled);
      if (startOverButton_ != null) {
         startOverButton_.setEnabled(!disabled);
      }
      // Leave view button always enabled.
      sendButton_.setEnabled(sendable);

      switch (mode) {
         case UNSENT:
            sendingActivityLabel_.setEnabled(false);
            sendingActivityLabel_.setText("Report not yet sent.");
            cancelButton_.setText("Cancel");
            break;
         case SENDING:
            sendingActivityLabel_.setEnabled(true);
            sendingActivityLabel_.setText("Sending Report...");
            break;
         case SENT:
            sendingActivityLabel_.setEnabled(false);
            sendingActivityLabel_.setText("Report sent to micro-manager.org (thank you!).");
            cancelButton_.setText("Close");
            break;
      }

      if (mode_ != UIMode.SENDING && mode == UIMode.SENDING) {
         if (sendingActivityIndicator_ == null) {
            sendingActivityIndicator_ = new JProgressBar();
            sendingActivityIndicator_.setIndeterminate(true);
         }
         sendingActivityIndicatorPanel_.add(sendingActivityIndicator_);
      }
      else if (mode_ == UIMode.SENDING && mode != UIMode.SENDING) {
         sendingActivityIndicatorPanel_.remove(sendingActivityIndicator_);
      }

      mode_ = mode;
   }
}
