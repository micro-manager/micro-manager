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

import java.awt.Dimension;
import java.awt.HeadlessException;
import java.awt.geom.AffineTransform;
import java.text.ParseException;
import javax.swing.JOptionPane;
import mmcorej.Configuration;
import mmcorej.DoubleVector;
import org.micromanager.Studio;
import org.micromanager.internal.MMStudio;
import org.micromanager.internal.utils.*;

/**
 *
 * @author nico
 */
public class PixelPresetEditor extends ConfigDialog implements PixelSizeProvider {

   private static final long serialVersionUID = -3709174019188065514L;

   private DoubleVector affineTransform_;
   private final AffineEditorPanel affineEditorPanel_;
   private final CalibrationListDlg parent_;
   private final double fractionErrorAllowed_ = 0.2;

   public PixelPresetEditor(String pixelSizeConfigName, 
         CalibrationListDlg parent, String pixelSize, boolean newItem) {
      super("ConfigPixelSize", pixelSizeConfigName, parent.getStudio(), newItem);
      // note: pixelSizeConfigName is called presetName_ in ConfigDialog
      instructionsText_ = "Specify pixel size configuration";
      nameFieldLabelText_ = "Pixel Config Name:";
      showPixelSize_ = true;
      pixelSize_ = pixelSize;
      initName_ = pixelSizeConfigName;
      title_ = "Pixel Preset Editor";
      showUnused_ = true;
      showFlagsPanelVisible_ = false;
      scrollPaneTop_ = 140;
      numColumns_ = 2;
      Studio gui = parent.getStudio();
      try {
         if (gui.getCMMCore().isPixelSizeConfigDefined(pixelSizeConfigName)) {
            gui.getCMMCore().setPixelSizeConfig(pixelSizeConfigName);
            affineTransform_ = gui.getCMMCore().getPixelSizeAffineByID(presetName_);
         }
      } catch (Exception ex) {
         gui.logs().showError(ex, "Failed to set this Pixel Size configuration");
      }
      
      PropertyTableData.Builder ptdb = new PropertyTableData.Builder(parent.getStudio());
      data_ = ptdb.groupName(groupName_).presetName(presetName_).propertyValueColumn(1).
              propertyUsedColumn(2).groupOnly(true).allowChangingProperties(true).
              allowChangesOnlyWhenUsed(true).isPixelSizeConfig(true).build();
      data_.setShowReadOnly(true);
      super.initializeData();
      data_.setColumnNames("Property Name", "Use in Group?", "Current Property Value");
      parent_ = parent;
      affineEditorPanel_ = new AffineEditorPanel(parent_.getStudio(), this, affineTransform_);

      super.initialize();  // will call initializeWidgets, which overrides the base class
      super.setBounds(100, 100, 450, 400);
      WindowPositioning.setUpBoundsMemory(this, this.getClass(), null);
      super.setMinimumSize(new Dimension(380, 350));
   }

   @Override
   public void okChosen() {
      try {
         AffineTransform affineTransform = AffineUtils.doubleToAffine(
                 affineEditorPanel_.getAffineTransform());
         double predictedPixelSize = AffineUtils.deducePixelSize(affineTransform);
         double inputSize = NumberUtils.displayStringToDouble(pixelSizeField_.getText());
         if (predictedPixelSize > (1 + fractionErrorAllowed_) * inputSize ||
               predictedPixelSize < (1 - fractionErrorAllowed_) * inputSize  ) {
            Object[] options = { "Yes", "No"};
            Object selectedValue = JOptionPane.showOptionDialog(null,
                    "Affine transform appears wrong.  Calculate from pixelSize?", "",
                     JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE,
                     null, options, options[0]);
            if (selectedValue instanceof Integer &&  0 == ((Integer)selectedValue)) {
               affineEditorPanel_.calculate();
            }
         }

         if(writeGroup(nameField_.getText())) {
            this.dispose();
         }

      } catch (HeadlessException | ParseException e) {
         ReportingUtils.showError(e);
      }      
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
      for (PropertyItem item : data_.getProperties()) {
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
         // Check if duplicate presets would be created.
         CalibrationList calibrationList_ = new CalibrationList(core_);
         calibrationList_.getCalibrationsFromCore();
         Configuration otherPreset;
         Configuration oldPreset = null;
         boolean same = false;

         // Compare the new added preset with all other presets.
         for (int j =0 ; j < calibrationList_.size(); j++) {
            String otherPresetName = calibrationList_.get(j).getLabel();
            otherPreset = core_.getPixelSizeConfigData(otherPresetName);

            if(!newItem_ && otherPresetName.equals(presetName_)) {
               continue;
            }

            same = true;
            // Save the old preset in case we need to restore the old properties
            if(otherPresetName.contentEquals(presetName_)) {
               oldPreset = core_.getPixelSizeConfigData(otherPresetName);
            }
            for (PropertyItem item:data_.getProperties()) {
               if (item.confInclude)
                  if (otherPreset.isPropertyIncluded(item.device, item.name))
                     if (! item.getValueInCoreFormat().contentEquals(otherPreset.getSetting(item.device, item.name).getPropertyValue()) )
                        same = false;
            }

            if (same) {
               showMessageDialog("This combination of properties is already found in the \"" + otherPresetName + "\" preset.\nPlease choose unique property values for your new preset.");
               return false;
            }
         }

         // If the preset name is being changed, delete old preset name.
         if (!newName.equals(presetName_)) {
            if (core_.isPixelSizeConfigDefined(presetName_)) {
               core_.deletePixelSizeConfig(presetName_);
            }
         }

         // If the new preset name has been used in existing presets, just overwrite it.
         if (core_.isPixelSizeConfigDefined(newName)) {
            core_.deletePixelSizeConfig(newName);
         }

         core_.definePixelSizeConfig(newName);
         for (PropertyItem item : data_.getProperties()) {
            if (item.confInclude) {
               // if no duplicate presets will be created, then allow this preset to be added
               if(!same) {
                  core_.definePixelSizeConfig(newName, item.device, item.name, item.getValueInCoreFormat());
               }
               // if fails to edit an existing preset due to the duplicate presets, keep the old preset
               else {
                  core_.definePixelSizeConfig(newName, item.device, item.name, oldPreset.getSetting(item.device, item.name).getPropertyValue());
               }
            }
         }

         pixelSize_ = pixelSizeField_.getText();
         core_.setPixelSizeUm(newName, NumberUtils.displayStringToDouble(pixelSize_));
         core_.setPixelSizeAffine(newName, affineEditorPanel_.getAffineTransform());

      } catch (Exception e) {
         ReportingUtils.showError(e);
         return false;
      }

      ((MMStudio) studio_).setConfigChanged(true);
      return true;
   }
     
   
   @Override
    protected void initializeWidgets() {
      super.initializeWidgets();
   }
   
   @Override protected void initializeBetweenWidgetsAndTable() {
      numRowsBeforeFilters_++;
      add(affineEditorPanel_, "growx, center");
   }

   @Override
   public Double getPixelSize() {
      try {
         return NumberUtils.displayStringToDouble(pixelSizeField_.getText());
      } catch (ParseException ex) {
         studio_.logs().showError("Pixel Size is not a valid Number");
         pixelSizeField_.requestFocus();
      }
      return 0.0;
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