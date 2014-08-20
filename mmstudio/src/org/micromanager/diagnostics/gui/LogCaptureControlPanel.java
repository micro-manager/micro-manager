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
import javax.swing.JSeparator;
import javax.swing.JTextField;


class LogCaptureControlPanel extends ControlPanel {
   private final ProblemReportController controller_;

   private final JButton startOverButton_;
   private final JButton doneButton_;
   private final JButton cannotReproButton_;

   private final JLabel statusLabel_;
   private final JLabel instructionsLabel_;

   LogCaptureControlPanel(ProblemReportController controller) {
      controller_ = controller;

      JButton cancelButton = new JButton("Cancel");
      cancelButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            controller_.cancelRequested();
         }
      });

      startOverButton_ = new JButton("Start Over");
      startOverButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            controller_.startLogCapture();
         }
      });

      doneButton_ = new JButton("Done");
      doneButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            controller_.finishLogCapture();
         }
      });

      cannotReproButton_ = new JButton("Cannot Reproduce");
      cannotReproButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            controller_.finishWithoutReproducing();
         }
      });

      statusLabel_ = new JLabel();
      statusLabel_.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
      setStatus("Preparing report...");

      instructionsLabel_ = new JLabel();
      instructionsLabel_.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
      setInstructions("Please wait.");

      final JTextField remarkTextField = new JTextField();
      final JButton remarkButton = new JButton("Insert");
      remarkButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            String remark = remarkTextField.getText();
            if (!remark.isEmpty()) {
               controller_.insertTimestampedRemark(remark);
            }
         }
      });

      setLayout(new net.miginfocom.swing.MigLayout(
               "fillx, insets 0",
               "[fill]",
               "unrel[]unrel[]unrel[][]"));

      javax.swing.JProgressBar activityIndicator = new javax.swing.JProgressBar();
      activityIndicator.setIndeterminate(true);

      add(statusLabel_, "split 2, grow, gapright related");
      add(activityIndicator, "wrap");

      add(instructionsLabel_, "grow, wrap");

      add(startOverButton_, "split 3, sizegroup btns, gapright push");
      add(cannotReproButton_, "sizegroup btns");
      add(doneButton_, "sizegroup btns, wrap");

      add(cancelButton, "gapright push, wrap");

      add(new JSeparator(), "grow, wrap");
      add(new JLabel("Insert timestamped remark into log:"),
            "align left, wrap");
      add(remarkTextField, "split 2, grow, gapright related");
      add(remarkButton, "");
   }

   void setStatus(String status) {
      statusLabel_.setText(status);
   }

   void setInstructions(String instructions) {
      instructionsLabel_.setText(instructions);
   }

   void setButtonsEnabled(boolean flag) {
      startOverButton_.setEnabled(flag);
      cannotReproButton_.setEnabled(flag);
      doneButton_.setEnabled(flag);
   }
}
