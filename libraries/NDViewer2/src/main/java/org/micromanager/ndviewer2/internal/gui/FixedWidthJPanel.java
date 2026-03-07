package org.micromanager.ndviewer2.internal.gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import javax.swing.JPanel;

/**
 * Utility class for Fixed Width JPanel.
 *
 * @author henrypinkard
 */
public class FixedWidthJPanel extends JPanel {

   public FixedWidthJPanel() {
      super(new BorderLayout());
   }

   @Override
   public Dimension getPreferredSize() {
      return new Dimension(40, 40);
   }
}
