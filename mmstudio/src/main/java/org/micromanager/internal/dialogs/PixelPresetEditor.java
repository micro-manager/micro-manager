///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman, 2018
//
// COPYRIGHT:    Regents of the University of California, 2018
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

package org.micromanager.internal.dialogs;

import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.ParseException;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import mmcorej.Configuration;
import mmcorej.DoubleVector;
import net.miginfocom.swing.MigLayout;
import org.micromanager.Studio;
import org.micromanager.internal.MMStudio;
import org.micromanager.internal.utils.NumberUtils;
import org.micromanager.internal.utils.PropertyItem;
import org.micromanager.internal.utils.PropertyTableData;
import org.micromanager.internal.utils.ReportingUtils;
import org.micromanager.internal.utils.ShowFlagsPanel;

/**
 *
 * @author nico
 */
public class PixelPresetEditor extends ConfigDialog implements PixelSizeProvider {

   private static final long serialVersionUID = -3709174019188065514L;

   protected final String pixelSizeLabelText_;
   protected JTextField pixelSizeField_;
   protected String pixelSize_;
   private DoubleVector affineTransform_;
   private final AffineEditorPanel affineEditorPanel_;

   public PixelPresetEditor(String pixelSizeConfigName, Studio gui, String pixelSize, boolean newItem) {
      super("ConfigPixelSize", pixelSizeConfigName, gui, gui.getCMMCore(), newItem);
      // note: pixelSizeConfigName is called presetName_ in ConfigDialog
      instructionsText_ = "Specify pixel size configuration";
      nameFieldLabelText_ = "Pixel Config Name:";
      pixelSizeLabelText_ = "Pixel Size (um)";
      pixelSize_ = pixelSize;
      initName_ = pixelSizeConfigName;
      title_ = "Pixel Config Editor";
      showUnused_ = true;
      showFlagsPanelVisible_ = false;
      scrollPaneTop_ = 140;
      numColumns_ = 2;
      try {
         if (gui.getCMMCore().isPixelSizeConfigDefined(pixelSizeConfigName)) {
            gui.getCMMCore().setPixelSizeConfig(pixelSizeConfigName);
            affineTransform_ = gui.getCMMCore().getPixelSizeAffineByID(presetName_);
         }
      } catch (Exception ex) {
         gui.logs().showError(ex, "Failed to set this Pixel Size configuration");
      }
      
      PropertyTableData.Builder ptdb = new PropertyTableData.Builder(core_);
      data_ = ptdb.groupName(groupName_).presetName(presetName_).propertyValueColumn(1).
              propertyUsedColumn(2).groupOnly(true).allowChangingProperties(true).
              allowChangesOnlyWhenUser(true).isPixelSizeConfig(true).build();
      data_.setShowReadOnly(true);
      super.initializeData();
      data_.setColumnNames("Property Name", "Use in Group?", "Current Property Value");
      showShowReadonlyCheckBox_ = true;
      affineEditorPanel_ = new AffineEditorPanel(this, affineTransform_);
      super.initialize();  // will cal out initializeWidgets, which overrides the base class
   }

   @Override
   public void okChosen() {
      writeGroup(nameField_.getText());
      this.dispose();      
   }

   public boolean writeGroup(String newName) {

      // Check that at least one property has been selected.
      int itemsIncludedCount = 0;
      for (PropertyItem item : data_.getPropList()) {
         if (item.confInclude) {
            itemsIncludedCount++;
         }
      }
      if (itemsIncludedCount == 0) {
         showMessageDialog("Please select at least one property for this group.");
         return false;
      }

      // Check to make sure a group name has been specified.
      if (newName.length() == 0) {
         showMessageDialog("Please enter a name for this Pixel Size Configuration.");
         return false;
      }

      try {
         if (!newName.equals(presetName_)) {
            if (core_.isPixelSizeConfigDefined(presetName_)) {
               core_.deletePixelSizeConfig(presetName_);
            }
         }

         if (core_.isPixelSizeConfigDefined(newName)) {
            core_.deletePixelSizeConfig(newName);
         }

         core_.definePixelSizeConfig(newName);

         for (PropertyItem item : data_.getPropList()) {
            if (item.confInclude) {
               core_.definePixelSizeConfig(newName, item.device, item.name, item.getValueInCoreFormat());              
            }
         }
         pixelSize_ = pixelSizeField_.getText();
         core_.setPixelSizeUm(newName, NumberUtils.displayStringToDouble(pixelSize_));
         core_.setPixelSizeAffine(newName, affineEditorPanel_.getAffineTransform() );
      } catch (Exception e) {
         ReportingUtils.showError(e);
         return false;
      }

      ((MMStudio) gui_).setConfigChanged(true);
      return true;
   }
     
   
   @Override
    protected void initializeWidgets() {
      JPanel leftPanel = new JPanel(
            new MigLayout("filly, flowy, insets 0 6 0 0, gap 2"));
      instructionsTextArea_ = new JTextArea();
      instructionsTextArea_.setFont(new Font("Arial", Font.PLAIN, 12));
      instructionsTextArea_.setWrapStyleWord(true);
      instructionsTextArea_.setText(instructionsText_);
      instructionsTextArea_.setEditable(false);
      instructionsTextArea_.setOpaque(false);
      leftPanel.add(instructionsTextArea_, "gaptop 2, gapbottom push");

      final Font boldArial = new Font("Arial", Font.BOLD, 12);
      nameFieldLabel_ = new JLabel(nameFieldLabelText_);
      nameFieldLabel_.setFont(boldArial);
      leftPanel.add(nameFieldLabel_, "split 2, flowx, alignx right");

      nameField_ = new JTextField();
      nameField_.setText(presetName_);
      nameField_.setEditable(true);
      nameField_.setSelectionStart(0);
      nameField_.setSelectionEnd(nameField_.getText().length());
      leftPanel.add(nameField_, "width 90!");
      
      JLabel pixelSizeFieldLabel = new JLabel(pixelSizeLabelText_);
      pixelSizeFieldLabel.setFont(boldArial);
      leftPanel.add(pixelSizeFieldLabel, "split 2, flowx, alignx right");
      
      pixelSizeField_ = new JTextField();
      pixelSizeField_.setText(pixelSize_);
      pixelSizeField_.setEditable(true);
      pixelSizeField_.setSelectionStart(0);
      pixelSizeField_.setSelectionEnd(pixelSizeField_.getText().length());
      leftPanel.add(pixelSizeField_, "width 90!");
              
      add(leftPanel, "growy, gapright push");

      okButton_ = new JButton("OK");
      okButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            if (table_.isEditing() && table_.getCellEditor() != null) {
               table_.getCellEditor().stopCellEditing();
            }
            okChosen();
         }
      });
      add(okButton_, "gapleft push, split 2, flowy, width 90!");

      cancelButton_ = new JButton("Cancel");
      cancelButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            dispose();
         }
      });
      add(cancelButton_, "gapleft push, gapbottom push, wrap, width 90!");
      
      add(new JSeparator(SwingConstants.HORIZONTAL),
              "span4, gapleft push, gapright push, growx, wrap");

      add(affineEditorPanel_, "span 4, wrap");
      
   }

   @Override
   public Double pixelSize() {
      try {
         return NumberUtils.displayStringToDouble(pixelSizeField_.getText());
      } catch (ParseException ex) {
         gui_.logs().showError("Pixel Size is not a valid Number");
         pixelSizeField_.requestFocus();
      }
      return 0.0;
   }
    
}