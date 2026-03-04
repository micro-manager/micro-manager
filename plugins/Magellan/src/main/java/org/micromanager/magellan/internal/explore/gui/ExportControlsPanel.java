package org.micromanager.magellan.internal.explore.gui;

import java.awt.Color;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.miginfocom.swing.MigLayout;
import org.micromanager.ndviewer.api.ControlsPanelInterface;

/**
 * A dedicated "Export" tab panel containing the export button and status label.
 */
public class ExportControlsPanel extends JPanel implements ControlsPanelInterface {

   private final Runnable exportAction_;
   private final JLabel statusLabel_;

   public ExportControlsPanel(Runnable exportAction) {
      exportAction_ = exportAction;

      setLayout(new MigLayout("insets 8"));

      JButton exportButton = new JButton("Export Image...");
      exportButton.addActionListener(evt -> {
         if (exportAction_ != null) {
            exportAction_.run();
         }
      });

      statusLabel_ = new JLabel(" ");
      statusLabel_.setForeground(Color.RED);

      add(exportButton, "wrap");
      add(statusLabel_, "wrap");
   }

   public void setStatus(String message) {
      statusLabel_.setText(message == null || message.isEmpty() ? " " : message);
   }

   @Override
   public void selected() {
   }

   @Override
   public void deselected() {
   }

   @Override
   public String getTitle() {
      return "Export";
   }

   @Override
   public void close() {
   }

}
