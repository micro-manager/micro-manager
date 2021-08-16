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

package org.micromanager.internal.diagnostics.gui;

import javax.swing.JButton;


class SendReportControlPanel extends ControlPanel {
   private final ProblemReportController controller_;

   private final JButton cancelButton_;
   private final JButton startOverButton_;

   SendReportControlPanel(ProblemReportController controller) {
      this(controller, true);
   }

   SendReportControlPanel(ProblemReportController controller,
         boolean allowRestart) {
      controller_ = controller;

      cancelButton_ = new JButton("Cancel");
      cancelButton_.addActionListener(e -> controller_.cancelRequested());

      if (allowRestart) {
         startOverButton_ = new JButton("Start Over");
         startOverButton_.addActionListener(e -> controller_.startLogCapture());
      } else {
         startOverButton_ = null;
      }

      JButton viewButton = new JButton("View Report");
      viewButton.addActionListener(e -> controller_.displayReport());

      setLayout(new net.miginfocom.swing.MigLayout(
               "fillx, insets 0", "", ""));

      if (startOverButton_ != null) {
         add(cancelButton_, "span 2, split 3, sizegroup cancelbtns");
         add(startOverButton_, "gapright push, sizegroup cancelbtns");
      } else {
         add(cancelButton_, "span 2, split 2, gapright push, sizegroup cancelbtns");
      }
      add(viewButton, "");
   }
}
