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

import javax.swing.JScrollPane;
import javax.swing.JTextArea;

class ProblemDescriptionPanel extends javax.swing.JPanel {
   private final ProblemReportController controller_;

   ProblemDescriptionPanel(ProblemReportController controller) {
      super(new net.miginfocom.swing.MigLayout(
              "fillx, filly, insets 0",
              "[grow, fill]",
              "[]related[grow, fill]"));

      controller_ = controller;

      final JTextArea descriptionTextArea = makeDescriptionTextArea();

      final JScrollPane scrollPane = new JScrollPane(descriptionTextArea,
               JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
               JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
      controller_.setDescriptionTextArea(descriptionTextArea);

      add(new javax.swing.JLabel("Problem Description:"), "gapafter push, wrap");
      add(scrollPane, "width 400:400, height 200:200");
   }

   private JTextArea makeDescriptionTextArea() {
      final JTextArea textArea = new JTextArea();
      textArea.setFont(new java.awt.Font(
               java.awt.Font.MONOSPACED,
               java.awt.Font.PLAIN,
               12));
      textArea.setLineWrap(true);
      textArea.setWrapStyleWord(true);

      // Ugly on OS X, but otherwise invisible on Windows
      textArea.setBorder(new javax.swing.border.LineBorder(java.awt.Color.BLACK));

      textArea.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
         public void insertUpdate(javax.swing.event.DocumentEvent e) {
            controller_.markDescriptionModified();
         }
         public void removeUpdate(javax.swing.event.DocumentEvent e) {
            controller_.markDescriptionModified();
         }
         public void changedUpdate(javax.swing.event.DocumentEvent e) {
            controller_.markDescriptionModified();
         }
      });

      return textArea;
   }
}
