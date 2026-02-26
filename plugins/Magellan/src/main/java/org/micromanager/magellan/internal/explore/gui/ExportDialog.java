package org.micromanager.magellan.internal.explore.gui;

import java.awt.Window;
import java.io.File;
import java.util.prefs.Preferences;
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
 * and output file path for an export operation.  Settings are persisted via
 * java.util.prefs.Preferences across sessions.
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
   private static final String PREF_FORMAT   = "ExportFormat";
   private static final String PREF_RES      = "ExportResLevel";
   private static final String PREF_PATH     = "ExportPath";

   private static final Preferences PREFS =
           Preferences.userNodeForPackage(ExportDialog.class);

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
         resItems[i] = "Level " + i + "  (" + (roiW_ >> i) + " \u00d7 " // x
            + (roiH_ >> i) + " px)";
      }
      resolutionCombo_ = new JComboBox<>(resItems);
      int savedRes = Math.min(PREFS.getInt(PREF_RES, 0), numResLevels_ - 1);
      resolutionCombo_.setSelectedIndex(savedRes);
      add(new JLabel("Resolution:"));
      add(resolutionCombo_, "span 2, wrap");

      // Output size label
      outputSizeLabel_ = new JLabel(outputSizeText(savedRes));
      add(new JLabel(""));
      add(outputSizeLabel_, "span 2, wrap");

      resolutionCombo_.addActionListener(e -> {
         int level = resolutionCombo_.getSelectedIndex();
         outputSizeLabel_.setText(outputSizeText(level));
         pack();
      });

      // Format combo
      formatCombo_ = new JComboBox<>(FORMATS);
      String savedFormat = PREFS.get(PREF_FORMAT, "TIFF");
      for (int i = 0; i < FORMATS.length; i++) {
         if (FORMATS[i].equals(savedFormat)) {
            formatCombo_.setSelectedIndex(i);
            break;
         }
      }
      add(new JLabel("Format:"));
      add(formatCombo_, "span 2, wrap");

      // Path field + browse button
      pathField_ = new JTextField(30);
      pathField_.setText(PREFS.get(PREF_PATH, ""));
      JButton browseButton = new JButton("...");
      browseButton.addActionListener(e -> {
         JFileChooser chooser = new JFileChooser();
         chooser.setDialogTitle("Choose output file");
         chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
         String current = pathField_.getText().trim();
         if (!current.isEmpty()) {
            File f = new File(current);
            chooser.setCurrentDirectory(f.getParentFile() != null ? f.getParentFile() : f);
         }
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
         int level = resolutionCombo_.getSelectedIndex();
         String format = (String) formatCombo_.getSelectedItem();
         // Resolve the extension the same way the exporter will
         String ext = formatToExt(format);
         File resolvedFile = new File(path.endsWith(ext) ? path : path + ext);
         if (resolvedFile.exists()) {
            int choice = JOptionPane.showConfirmDialog(this,
                    resolvedFile.getName() + " already exists.\nOverwrite?",
                    "Confirm overwrite", JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (choice != JOptionPane.YES_OPTION) {
               return;
            }
         }
         PREFS.putInt(PREF_RES, level);
         PREFS.put(PREF_FORMAT, format);
         PREFS.put(PREF_PATH, path);
         result_ = new ExportOptions(level, format, path);
         dispose();
      });
      JButton cancelButton = new JButton("Cancel");
      cancelButton.addActionListener(e -> dispose());

      add(new JLabel(""), "");
      add(okButton, "split 2, align right");
      add(cancelButton, "wrap");
   }

   private static String formatToExt(String format) {
      switch (format) {
         case "JPEG": return ".jpg";
         case "PNG":  return ".png";
         case "GIF":  return ".gif";
         default:     return ".tif";
      }
   }

   private String outputSizeText(int level) {
      return "Output size: " + (roiW_ >> level) + " \u00d7 "  // x
               + (roiH_ >> level) + " px";
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
