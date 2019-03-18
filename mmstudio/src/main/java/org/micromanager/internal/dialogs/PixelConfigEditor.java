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

import java.awt.Dimension;
import java.awt.geom.AffineTransform;
import java.text.ParseException;
import mmcorej.StrVector;
import org.micromanager.internal.MMStudio;
import org.micromanager.internal.utils.AffineUtils;
import org.micromanager.internal.utils.NumberUtils;
import org.micromanager.internal.utils.PropertyItem;
import org.micromanager.internal.utils.PropertyTableData;
import org.micromanager.internal.utils.ReportingUtils;

/**
 *
 * @author nico
 */
public class PixelConfigEditor extends ConfigDialog implements PixelSizeProvider {

   private static final long serialVersionUID = 5109403088011441146L;

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
      showPixelSize_ = true;
      pixelSize_ = pixelSize;
      initName_ = pixelSizeConfigName;
      title_ = "Pixel Config Editor";
      showUnused_ = true;
      showFlagsPanelVisible_ = true;
      scrollPaneTop_ = 140;
      numColumns_ = 3;
      numRowsBeforeFilters_ = 2;
      PropertyTableData.Builder ptdb = new PropertyTableData.Builder(parent.getStudio());
      data_ = ptdb.groupName(groupName_).presetName(presetName_).propertyValueColumn(2).
              propertyUsedColumn(1).groupOnly(false).allowChangingProperties(true).allowChangesOnlyWhenUsed(true).isPixelSizeConfig(true).build();
      super.initializeData();
      data_.setColumnNames("Property Name", "Use in Group?", "Current Property Value");
      parent_ = parent;
      affineEditorPanel_ = new AffineEditorPanel(parent.getStudio(), this, 
            AffineUtils.noTransform());
      super.initialize();
      super.loadAndRestorePosition(100, 100, 550, 600);
      super.setMinimumSize(new Dimension(500, 530));
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

         for (PropertyItem item : data_.getProperties()) {
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

      ((MMStudio) studio_).setConfigChanged(true);
      return true;
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
   protected void initializeWidgets() {
      presetName_ = "";  // makes sure initializeWidgets() has the right behavior when loading ShowFlagsPanel
      super.initializeWidgets();
   }
   
   @Override
   protected void initializeBetweenWidgetsAndTable() {
      add(affineEditorPanel_, "growx, center");
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