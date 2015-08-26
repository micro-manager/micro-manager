package org.micromanager.hcs;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileWriter;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTextField;

import net.miginfocom.swing.MigLayout;

import org.micromanager.internal.utils.FileDialogs;
import org.micromanager.internal.utils.ReportingUtils;

public class CustomSettingsFrame extends JFrame {
   private JTextField id_;
   private JTextField description_;
   private JTextField rows_;
   private JTextField cols_;
   private JTextField wellSpacingX_;
   private JTextField wellSpacingY_;
   private JTextField sizeXUm_;
   private JTextField sizeYUm_;
   private JTextField firstWellX_;
   private JTextField firstWellY_;
   private JTextField wellSizeX_;
   private JTextField wellSizeY_;
   private JCheckBox circular_;

   private SiteGenerator parent_;

   public CustomSettingsFrame(SiteGenerator parent) {
      super();
      parent_ = parent;
      setLayout(new MigLayout("flowx"));

      add(new JLabel("<html><body>If you believe your settings would be useful to other labs, please consider sharing<br>the settings file on the &#956;Manager mailing list micro-manager-general@lists.sourceforge.net</body></html>"), "span, wrap");

      id_ = createText("Layout name: ", 10, false);
      description_ = createText("Layout description: ", 25, true);

      rows_ = createText("Number of rows: ", 10, false);
      cols_ = createText("Number of columns: ", 10, true);

      wellSpacingX_ = createText("<html>Horizontal spacing (&#956;m): </html>", 10, false);
      wellSpacingY_ = createText("<html>Vertical spacing (&#956;m): </html>", 10, true);

      sizeXUm_ = createText("<html>Total width (&#956;m): </html>", 10, false);
      sizeYUm_ = createText("<html>Total height (&#956;m): </html>", 10, true);

      firstWellX_ = createText("<html>First well X-offset (&#956;m): </html>", 10, false);
      firstWellY_ = createText("<html>First well Y-offset (&#956;m): </html>", 10, true);

      wellSizeX_ = createText("<html>Well width (&#956;m): </html>", 10, false);
      wellSizeY_ = createText("<html>Well height (&#956;m): </html>", 10, true);

      circular_ = new JCheckBox("<html>Circular: ");
      add(circular_, "wrap");

      JButton cancel = new JButton("Cancel");
      cancel.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            dispose();
         }
      });
      add(cancel, "align right");

      JButton save = new JButton("Save to file");
      save.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            File target = FileDialogs.save(CustomSettingsFrame.this,
               "Select a file to save the settings to", SBSPlate.PLATE_FILE);
            if (target != null) {
               saveToFile(target);
            }
         }
      });
      add(save, "align right");

      pack();
      setVisible(true);
   }

   private JTextField createText(String label, int width, boolean shouldWrap) {
      add(new JLabel(label));
      JTextField field = new JTextField("", width);
      add(field, shouldWrap ? "wrap" : "");
      return field;
   }

   private void saveToFile(File target) {
      try {
         FileWriter writer = new FileWriter(target);
         writer.write(SBSPlate.serialize(Integer.parseInt(rows_.getText()),
                  Integer.parseInt(cols_.getText()),
                  Double.parseDouble(wellSpacingX_.getText()),
                  Double.parseDouble(wellSpacingY_.getText()),
                  Double.parseDouble(sizeXUm_.getText()),
                  Double.parseDouble(sizeYUm_.getText()),
                  id_.getText(), description_.getText(),
                  Double.parseDouble(firstWellX_.getText()),
                  Double.parseDouble(firstWellY_.getText()),
                  circular_.isSelected()));
         writer.close();
         dispose();
         parent_.loadCustom(target);
      }
      catch (Exception e) {
         ReportingUtils.showError(e, "There was an error trying to save your settings");
      }
   }
}
