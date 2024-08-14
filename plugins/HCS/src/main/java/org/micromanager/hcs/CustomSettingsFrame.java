package org.micromanager.hcs;

import java.awt.event.ActionEvent;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTextField;
import net.miginfocom.swing.MigLayout;
import org.micromanager.Studio;
import org.micromanager.internal.utils.FileDialogs;

/**
 * Dialog that lets the operator create a new plate layout and save it in a file for
 * later use.
 */
public class CustomSettingsFrame extends JFrame {
   private final JTextField id_;
   private final JTextField description_;
   private final JTextField rows_;
   private final JTextField cols_;
   private final JTextField wellSpacingX_;
   private final JTextField wellSpacingY_;
   private final JTextField sizeXUm_;
   private final JTextField sizeYUm_;
   private final JTextField wellSizeXUm_;
   private final JTextField wellSizeYUm_;
   private final JTextField firstWellX_;
   private final JTextField firstWellY_;
   private final JCheckBox circular_;

   private final Studio studio_;
   private final SiteGenerator parent_;

   /**
    * Creates the dialog.
    *
    * @param studio The always present Studio instance.
    * @param parent The HCS GUI.
    */
   public CustomSettingsFrame(Studio studio, SiteGenerator parent) {
      super();
      studio_ = studio;
      parent_ = parent;
      super.setLayout(new MigLayout("flowx"));

      super.add(new JLabel(
            "<html><body>If you believe your settings would be useful to other labs, "
             + "please consider sharing<br>the settings file on the &#956;Manager mailing list "
             + "micro-manager-general@lists.sourceforge.net</body></html>"), "span, wrap");

      id_ = createText("Layout name: ", 10, false);
      description_ = createText("Layout description: ", 25, true);

      rows_ = createText("Number of rows: ", 10, false);
      cols_ = createText("Number of columns: ", 10, true);

      wellSpacingX_ = createText("<html>Horizontal spacing (&#956;m): </html>", 10, false);
      wellSpacingY_ = createText("<html>Vertical spacing (&#956;m): </html>", 10, true);

      sizeXUm_ = createText("<html>Total width (&#956;m): </html>", 10, false);
      sizeYUm_ = createText("<html>Total height (&#956;m): </html>", 10, true);

      wellSizeXUm_ = createText("<html>Well width (&#956;m): </html>", 10, false);
      wellSizeYUm_ = createText("<html>Well height (&#956;m): </html>", 10, true);

      firstWellX_ = createText("<html>First well X-offset (&#956;m): </html>", 10, false);
      firstWellY_ = createText("<html>First well Y-offset (&#956;m): </html>", 10, true);

      circular_ = new JCheckBox("<html>Circular: ");
      super.add(circular_, "wrap");

      JButton cancel = new JButton("Cancel");
      cancel.addActionListener((ActionEvent e) -> dispose());
      super.add(cancel, "align right");

      JButton save = new JButton("Save to file");
      save.addActionListener((ActionEvent e) -> {
         File target = FileDialogs.save(CustomSettingsFrame.this,
               "Select a file to save the settings to", SBSPlate.PLATE_FILE);
         if (target != null) {
            saveToFile(target);
         }
      });
      super.add(save, "align right");

      super.pack();

      super.setLocationRelativeTo(parent);

   }

   private JTextField createText(String label, int width, boolean shouldWrap) {
      add(new JLabel(label));
      JTextField field = new JTextField("", width);
      add(field, shouldWrap ? "wrap" : "");
      return field;
   }

   private void saveToFile(File target) {
      try {
         try (FileWriter writer = new FileWriter(target)) {
            writer.write(SBSPlate.serialize(Integer.parseInt(rows_.getText()),
                  Integer.parseInt(cols_.getText()),
                  Double.parseDouble(wellSpacingX_.getText()),
                  Double.parseDouble(wellSpacingY_.getText()),
                  Double.parseDouble(sizeXUm_.getText()),
                  Double.parseDouble(sizeYUm_.getText()),
                  id_.getText(), description_.getText(),
                  Double.parseDouble(firstWellX_.getText()),
                  Double.parseDouble(firstWellY_.getText()),
                  Double.parseDouble(wellSizeXUm_.getText()),
                  Double.parseDouble(wellSizeYUm_.getText()),
                  circular_.isSelected()));
         }
         dispose();
         parent_.loadCustom(target);
      } catch (IOException e) {
         studio_.logs().showError(e, "There was an IO error trying to save your settings");
      } catch (NumberFormatException e) {
         studio_.logs().showError(e, "There was a NumberFormat Error trying to save your settings");
      } catch (HCSException e) {
         studio_.logs().showError(e, "There was an HCS error trying to save your settings");
      }
   }
}
