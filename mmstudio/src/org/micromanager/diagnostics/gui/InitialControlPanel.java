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

import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import javax.swing.JButton;
import javax.swing.JCheckBox;


class InitialControlPanel extends ControlPanel {
   private final ProblemReportController controller_;

   InitialControlPanel(ProblemReportController controller) {
      controller_ = controller;

      JButton cancelButton = new JButton("Cancel");
      cancelButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            controller_.cancelRequested();
         }
      });

      JButton startButton = new JButton("Generate Report...");
      startButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            controller_.startLogCapture();
         }
      });

      final JCheckBox crashRobustCheckBox =
         new JCheckBox("Use crash-robust logging");
      crashRobustCheckBox.setSelected(controller_.getUseCrashRobustLogging());
      crashRobustCheckBox.setToolTipText("Prevents lost log entries. " +
            "Uncheck if it masks the problem to be reported.");
      crashRobustCheckBox.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            controller_.setUseCrashRobustLogging(crashRobustCheckBox.isSelected());
         }
      });

      add(cancelButton, "align left");
      add(startButton, "align right, wrap");
      add(crashRobustCheckBox, "align left, span 2");
   }
}
