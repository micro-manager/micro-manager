///////////////////////////////////////////////////////////////////////////////
//FILE:          PresetEditor.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Arthur Edelstein, June 2009
//
// COPYRIGHT:    University of California, San Francisco, 2009
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

package org.micromanager.dialogs;


import mmcorej.CMMCore;
import mmcorej.Configuration;
import mmcorej.StrVector;

import org.micromanager.api.ScriptInterface;
import org.micromanager.utils.PropertyItem;
import org.micromanager.utils.PropertyTableData;
import org.micromanager.utils.ReportingUtils;

public class PresetEditor extends ConfigDialog {

   /**
    * 
    */
   private static final long serialVersionUID = 8281144157746745260L;

   public PresetEditor(String groupName, String presetName, ScriptInterface gui, CMMCore core, boolean newItem) {
      super(groupName, presetName, gui, core, newItem);
      instructionsText_ = "Here you can specifiy the property values\nin a configuration preset.";
      nameFieldLabelText_ = "Preset name:";
      initName_ = presetName_;
      TITLE = "Preset editor for the \"" + groupName + "\" configuration group";
      showUnused_ = false;
      showFlagsPanelVisible = false;
      scrollPaneTop_ = 70;
      numColumns_=2;
      data_ = new PropertyTableData(core_,groupName_,presetName_,1,2, true);
      initializeData();
      data_.setColumnNames("Property Name","Preset Value","");
      data_.setShowReadOnly(true);
      initialize();

   }

   @Override
   public void okChosen() {
      String newName = nameField_.getText();
      if (writePreset(initName_,newName)) {
         this.dispose();
      }
   }

   public boolean writePreset(String initName, String newName) {

      // Check to make sure a group name has been specified.
      if (newName.length()==0) { 
         showMessageDialog("Please enter a name for this preset.");
         return false;
      }

      // Avoid clashing names
      StrVector groups = core_.getAvailableConfigs(groupName_);
      for (int i=0;i<groups.size();i++)
         if (groups.get(i).contentEquals(newName) && !newName.contentEquals(initName)) {
            showMessageDialog("A preset by this name already exists in the \"" + groupName_ + "\" group.\nPlease enter a different name.");
            return false;
         }  

      StrVector cfgs = core_.getAvailableConfigs(groupName_);
      try {
         // Check if duplicate presets would be created
         Configuration otherPreset;
         boolean same;
         for (int j=0;j<cfgs.size();j++) {
            same = true;
            if (newItem_ || ! cfgs.get(j).contentEquals(initName)) {
               otherPreset = core_.getConfigData(groupName_, cfgs.get(j));
               for (PropertyItem item:data_.getPropList()) {
                  if (item.confInclude)
                     if (otherPreset.isPropertyIncluded(item.device, item.name))
                        if (! item.getValueInCoreFormat().contentEquals(otherPreset.getSetting(item.device, item.name).getPropertyValue()) )
                           same = false;
               }
               if (same) {
                  showMessageDialog("This combination of properties is already found in the \"" + cfgs.get(j) + "\" preset.\nPlease choose unique property values for your new preset.");
                  return false;
               }
            }

         }

      } catch (Exception e) {
         ReportingUtils.logError(e);
      }

      // Rename the preset if its name has changed
      if (! newItem_ && !initName.contentEquals(newName))
         try {
            core_.renameConfig(groupName_, initName, newName);
         } catch (Exception e1) {
            ReportingUtils.logError(e1);
         }

         // Define the preset.   
         for (PropertyItem item_:data_.getPropList()) {
            if (item_.confInclude) {
               try {
                  core_.defineConfig(groupName_, newName, item_.device, item_.name, item_.getValueInCoreFormat());
               } catch (Exception e) {
                  ReportingUtils.logError(e);
               }
            }
         }

         gui_.setConfigChanged(true);
         return true;

   }
}
