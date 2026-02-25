package org.micromanager.magellan.internal.explore.gui;

import java.awt.Window;
import java.io.File;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import net.miginfocom.swing.MigLayout;

/**
 * Modal dialog that lets the user choose a resolution level, image format,
 * and output file path for an export operation.
 */
public class ExportDialog extends JDialog {

   public static class ExportOptions {
      public final int resolutionLevel;
      public final String format;
      public final String filePath;

      public ExportOptions(int resolutionLevel, String format, String filePath) {
         this.resolutionLevel = resolutionLevel;
         this.format = format;
         this.filePath = filePath;
      }
   }

   private static final String[] FORMATS = {"TIFF", "JPEG", "PNG", "GIF"};

   private final int numResLevels_;
   private final int roiW_;
   private final int roiH_;

   private JComboBox<String> resolutionCombo_;
   private JLabel outputSizeLabel_;
   private JComboBox<String> formatCombo_;
   private JTextField pathField_;

   private ExportOptions result_ = null;

   public ExportDialog(Window owner, int numResLevels, int roiW, int roiH) {
      super(owner, "Export Image", ModalityType.APPLICATION_MODAL);
      numResLevels_ = numResLevels;
      roiW_ = roiW;
      roiH_ = roiH;
      initComponents();
      pack();
      setLocationRelativeTo(owner);
   }

   private void initComponents() {
      setLayout(new MigLayout("insets 12, gap 8", "[right][left,grow][]"));

      // Resolution combo
      String[] resItems = new String[numResLevels_];
      for (int i = 0; i < numResLevels_; i++) {
         resItems[i] = "Level " + i + "  (" + (roiW_ >> i) + " \u00d7 " + (roiH_ >> i) + " px)";
      }
      resolutionCombo_ = new JComboBox<>(resItems);
      add(new JLabel("Resolution:"));
      add(resolutionCombo_, "span 2, wrap");

      // Output size label
      outputSizeLabel_ = new JLabel(outputSizeText(0));
      add(new JLabel(""));
      add(outputSizeLabel_, "span 2, wrap");

      resolutionCombo_.addActionListener(e -> {
         int level = resolutionCombo_.getSelectedIndex();
         outputSizeLabel_.setText(outputSizeText(level));
         pack();
      });

      // Format combo
      formatCombo_ = new JComboBox<>(FORMATS);
      add(new JLabel("Format:"));
      add(formatCombo_, "span 2, wrap");

      // Path field + browse button
      pathField_ = new JTextField(30);
      JButton browseButton = new JButton("...");
      browseButton.addActionListener(e -> {
         JFileChooser chooser = new JFileChooser();
         chooser.setDialogTitle("Choose output file");
         chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
         if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            pathField_.setText(chooser.getSelectedFile().getAbsolutePath());
         }
      });
      add(new JLabel("Save to:"));
      add(pathField_, "growx");
      add(browseButton, "wrap");

      // OK / Cancel buttons
      JButton okButton = new JButton("OK");
      okButton.addActionListener(e -> {
         String path = pathField_.getText().trim();
         if (path.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter an output file path.",
                    "Missing path", JOptionPane.WARNING_MESSAGE);
            return;
         }
         result_ = new ExportOptions(
                 resolutionCombo_.getSelectedIndex(),
                 (String) formatCombo_.getSelectedItem(),
                 path);
         dispose();
      });
      JButton cancelButton = new JButton("Cancel");
      cancelButton.addActionListener(e -> dispose());

      add(new JLabel(""), "");
      add(okButton, "split 2, align right");
      add(cancelButton, "wrap");
   }

   private String outputSizeText(int level) {
      return "Output size: " + (roiW_ >> level) + " \u00d7 " + (roiH_ >> level) + " px";
   }

   /**
    * Shows the dialog and blocks until the user closes it.
    *
    * @return ExportOptions if the user clicked OK, or null if cancelled.
    */
   public ExportOptions showAndGet() {
      setVisible(true);
      return result_;
   }

}
