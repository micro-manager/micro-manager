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

class ProblemReportFrame extends javax.swing.JFrame {
   private final ProblemReportController controller_;

   private final ProblemDescriptionPanel descriptionPanel_;
   private ControlPanel controlPanel_;

   private final javax.swing.JPanel controlPanelPanel_;

   ProblemReportFrame(ProblemReportController controller) {
      super("Problem Report");
      setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
      setLocationRelativeTo(null);

      controller_ = controller;

      addWindowListener(new java.awt.event.WindowAdapter() {
         @Override
         public void windowClosing(java.awt.event.WindowEvent e) {
            controller_.cancelRequested();
         }
      });

      descriptionPanel_ = new ProblemDescriptionPanel(controller);
      controlPanel_ = new InitialControlPanel(controller);

      controlPanelPanel_ = new javax.swing.JPanel();
      controlPanelPanel_.setLayout(new net.miginfocom.swing.MigLayout(
               "fill, insets 0",
               "[grow, fill]",
               "[grow, fill]"));
      controlPanelPanel_.add(controlPanel_);

      setLayout(new net.miginfocom.swing.MigLayout(
               "fill, insets dialog",
               "[grow, fill]",
               "[grow, fill]unrelated[]"));
      add(descriptionPanel_, "wrap");
      add(controlPanelPanel_);
      pack();
      setMinimumSize(getPreferredSize());
   }

   void close() {
      dispose();
   }

   void setControlPanel(final ControlPanel newPanel) {
      int saveWidth = getWidth();

      // Preserve the size of the description panel
      int descriptionPanelHeight = descriptionPanel_.getHeight();
      int defaultDescriptionPanelHeight = descriptionPanel_.getPreferredSize().height;
      int saveExtraHeight = descriptionPanelHeight - defaultDescriptionPanelHeight;

      controlPanelPanel_.remove(controlPanel_);
      controlPanel_ = newPanel;
      controlPanelPanel_.add(controlPanel_);

      validate();
      repaint();

      setMinimumSize(getPreferredSize());

      int newFrameHeight = getPreferredSize().height + saveExtraHeight;
      setSize(saveWidth, newFrameHeight);
   }

   ControlPanel getControlPanel() {
      return controlPanel_;
   }
}
