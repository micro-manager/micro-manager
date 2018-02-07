//////////////////////////////////////////////////////////////////////////////
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
import java.awt.geom.AffineTransform;
import java.text.ParseException;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import mmcorej.Configuration;
import mmcorej.StrVector;
import net.miginfocom.swing.MigLayout;
import org.micromanager.internal.MMStudio;
import org.micromanager.internal.utils.AffineUtils;
import org.micromanager.internal.utils.NumberUtils;
import org.micromanager.internal.utils.PropertyItem;
import org.micromanager.internal.utils.PropertyTableData;
import org.micromanager.internal.utils.ReportingUtils;
import org.micromanager.internal.utils.ShowFlagsPanel;

/**
 *
 * @author nico
 */
public class PixelConfigEditor extends ConfigDialog implements PixelSizeProvider {

   private static final long serialVersionUID = 5109403088011441146L;

   protected final String pixelSizeLabelText_;
   protected JTextField pixelSizeField_;
   protected String pixelSize_;
   private final AffineEditorPanel affineEditorPanel_;
   private final CalibrationListDlg parent_;

   public PixelConfigEditor(String pixelSizeConfigName, CalibrationListDlg parent, 
         String pixelSize, boolean newItem) {
      super("ConfigPixelSize", pixelSizeConfigName, parent.getStudio(), 
            parent.getStudio().getCMMCore(), newItem);
      // note: pixelSizeConfigName is called presetName_ in ConfigDialog
      instructionsText_ = "Specify all properties affecting pixel size.";
      nameFieldLabelText_ = "Pixel Config Name:";
      pixelSizeLabelText_ = "Pixel Size (um)";
      pixelSize_ = pixelSize;
      initName_ = pixelSizeConfigName;
      title_ = "Pixel Config Editor";
      showUnused_ = true;
      showFlagsPanelVisible_ = true;
      scrollPaneTop_ = 140;
      numColumns_ = 3;
      PropertyTableData.Builder ptdb = new PropertyTableData.Builder(core_);
      data_ = ptdb.groupName(groupName_).presetName(presetName_).propertyValueColumn(2).
              propertyUsedColumn(1).groupOnly(false).allowChangingProperties(true).
              allowChangesOnlyWhenUser(true).isPixelSizeConfig(true).build();
      super.initializeData();
      data_.setColumnNames("Property Name", "Use in Group?", "Current Property Value");
      showShowReadonlyCheckBox_ = true; 
      parent_ = parent;
      affineEditorPanel_ = new AffineEditorPanel(parent.getStudio(), this, 
            AffineUtils.noTransform());
      super.initialize();
   }

   @Override
   public void okChosen() {
      writeGroup(nameField_.getText());
      this.dispose();      
   }
   
   @Override
   public void dispose() {
      if (parent_ != null) {
         parent_.endedEditingPreset(this);
      }
      if (affineEditorPanel_ != null) {
         affineEditorPanel_.cleanup();
      }
      super.dispose();
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
         showMessageDialog("Please enter a name for this Pizel Size Configuration.");
         return false;
      }

      // Check to make sure that no Pixel Size Configs have been defined yet
      StrVector groups = core_.getAvailablePixelSizeConfigs();
      if (!groups.isEmpty()) {
         showMessageDialog("Properties for Pixel Size Config already selected. This is weird");
         return false;
      }

      try {
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
         ReportingUtils.logError(e);
      }

      ((MMStudio) gui_).setConfigChanged(true);
      return true;
   }
   
    @Override
   public Double getPixelSize() {
      try {
         return NumberUtils.displayStringToDouble(pixelSizeField_.getText());
      } catch (ParseException ex) {
         gui_.logs().showError("Pixel Size is not a valid Number");
         pixelSizeField_.requestFocus();
      }
      return 0.0;
   }
   
   @Override
   @SuppressWarnings("Convert2Lambda")
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

      if (showShowReadonlyCheckBox_) {
         showReadonlyCheckBox_ = new JCheckBox("Show read-only properties");
         showReadonlyCheckBox_.setFont(new Font("Arial", Font.PLAIN, 10));
         showReadonlyCheckBox_.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
               // show/hide read-only properties
               data_.setShowReadOnly(showReadonlyCheckBox_.isSelected());
               data_.update(false);
               data_.fireTableStructureChanged();
             }
         });
         leftPanel.add(showReadonlyCheckBox_, "gaptop 5, gapbottom 10");
      }

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

      if (showFlagsPanelVisible_ ) {
         flags_.load(ConfigDialog.class);
         Configuration cfg;
         try {
            cfg = new Configuration();
            showFlagsPanel_ = new ShowFlagsPanel(data_, flags_, core_, cfg);
         } catch (Exception e) {
            ReportingUtils.showError(e);
         }
         add(showFlagsPanel_, "growx");
      }

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

      add(affineEditorPanel_, "span 4, growx, wrap");
   }

   @Override
   public void setPixelSize(double pixelSizeUm) {
      pixelSize_ = NumberUtils.doubleToDisplayString(pixelSizeUm);
      pixelSizeField_.setText(pixelSize_); }

   @Override
   public AffineTransform getAffineTransform() {
      return AffineUtils.doubleToAffine(affineEditorPanel_.getAffineTransform());
   }

   @Override
   public void setAffineTransform(AffineTransform aft) {
      affineEditorPanel_.setAffineTransform(AffineUtils.affineToDouble(aft));
   }

}