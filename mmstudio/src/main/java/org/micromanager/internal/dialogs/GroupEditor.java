///////////////////////////////////////////////////////////////////////////////
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
package org.micromanager.internal.dialogs;

import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import mmcorej.CMMCore;
import mmcorej.Configuration;
import mmcorej.DeviceType;
import mmcorej.PropertyType;
import mmcorej.StrVector;
import net.miginfocom.swing.MigLayout;
import org.micromanager.Studio;
import org.micromanager.internal.MMStudio;
import org.micromanager.internal.utils.PropertyItem;
import org.micromanager.internal.utils.PropertyTableData;
import org.micromanager.internal.utils.ReportingUtils;
import org.micromanager.internal.utils.SortFunctionObjects;

public final class GroupEditor extends ConfigDialog {

   private static final long serialVersionUID = 8281144157746745260L;
   private static final String DISPLAY_SHUTTER_WARNING = "Warn user before saving a config group that includes shutter state.";

   public GroupEditor(String groupName, String presetName, Studio studio, CMMCore core, boolean newItem) {
      super(groupName, presetName, studio, core, newItem);
      instructionsText_ = "Specify properties in this configuration group:";
      nameFieldLabelText_ = "Group name:";
      initName_ = groupName_;
      title_ = "Group Editor";
      showUnused_ = true;
      showFlagsPanelVisible_ = true;
      scrollPaneTop_ = 140;
      numColumns_ = 3;
      PropertyTableData.Builder ptdb = new PropertyTableData.Builder(studio);
      data_ = ptdb.groupName(groupName).presetName(presetName).propertyValueColumn(2).
              propertyUsedColumn(1).groupOnly(false).allowChangingProperties(false).allowChangesOnlyWhenUsed(false).isPixelSizeConfig(false).build();
      initializeData();
      data_.setColumnNames("Property Name", "Use in Group?", "Current Property Value");
      initialize();
      super.loadAndRestorePosition(100, 100, 550, 600);
      super.setMinimumSize(new Dimension(520, 400));
   }

   @Override
   public void okChosen() {
      String newName = nameField_.getText();

      // Examine the group, checking for shutter state properties.
      List<PropertyItem> shutters = new ArrayList<>();
      for (PropertyItem item : data_.getProperties()) {
         try {
            if (item.confInclude && item.name.contentEquals("State") &&
                  core_.getDeviceType(item.device) == DeviceType.ShutterDevice) {
               if (item.allowed.length != 2) {
                  // Has more than two allowed values, ergo doesn't reflect
                  // the shutter openness.
                  continue;
               }
               if ((item.allowed[0].contentEquals("0") &&
                     item.allowed[1].contentEquals("1")) ||
                   (item.allowed[0].contentEquals("1") &&
                     item.allowed[1].contentEquals("0"))) {
                  shutters.add(item);
               }
            }
         }
         catch (Exception e) {
            studio_.logs().logError(e, "Error getting information on device/property " + item.device + "/" + item.name);
         }
      }
      // Warn user about including shutter state in config groups.
      if (shutters.size() > 0 && studio_.profile().getSettings(GroupEditor.class).
              getBoolean(DISPLAY_SHUTTER_WARNING, true)) {
         JPanel contents = new JPanel(new MigLayout("fill, flowy"));
         // NB I would prefer to use a JTextArea here, and use its automatic
         // line wrapping, but that causes a NullPointerException when laying
         // out the dialog, for unknown reasons.
         JLabel warning = new JLabel(
            "<html>It looks like this config group contains a property that<br>" +
            "controls whether or not a shutter is open. If you intend<br>" +
            "to use this config group to control the current channel,<br>" +
            "you should be aware that including shutter state properties<br>" +
            "can lead to bad interactions with the autoshutter system.<br>" +
            "If you want to use different shutters for different channel<br>" +
            "presets, you should use the Core-Shutter property.<br><br>" +
            "Would you like to remove the shutter state property from<br>" +
            "this config group?</html>");
         contents.add(warning, "growx");

         JCheckBox neverAgain = new JCheckBox(
               "Do not show me this warning again.");
         contents.add(neverAgain);
         String[] buttons = new String[] {"Keep", "Remove", "Cancel"};
         int selection = JOptionPane.showOptionDialog(this,
               contents, "Shutter State in Config Group",
               JOptionPane.WARNING_MESSAGE, 0, null,
               buttons, buttons[0]);
         studio_.profile().getSettings(GroupEditor.class).putBoolean(
                 DISPLAY_SHUTTER_WARNING, !neverAgain.isSelected());
         if (selection == 2) {
            // User cancelled.
            return;
         }
         else if (selection == 1) {
            for (PropertyItem shutter : shutters) {
               shutter.confInclude = false;
            }
         }
         // Otherwise user elected to keep it.
      }
      if (writeGroup(initName_, newName)) {
         groupName_ = newName;
         this.dispose();
      }
   }

   public boolean writeGroup(String initName, String newName) {

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
         showMessageDialog("Please enter a name for this group.");
         return false;
      }

      // Avoid clashing names
      StrVector groups = core_.getAvailableConfigGroups();
      for (int i = 0; i < groups.size(); i++) {
         if (groups.get(i).contentEquals(newName) && !newName.contentEquals(initName)) {
            showMessageDialog("A group by this name already exists. Please enter a different name.");
            return false;
         }
      }

      StrVector cfgs = core_.getAvailableConfigs(newName);
      try {
         // Check if duplicate presets would be created
         if (!newItem_) {
            Configuration first;
            Configuration second;
            boolean same;

            for (int i = 0; i < cfgs.size(); i++) {
               first = core_.getConfigData(initName, cfgs.get(i));
               for (int j = i + 1; j < cfgs.size(); j++) {
                  same = true;

                  second = core_.getConfigData(initName, cfgs.get(j));
                  for (PropertyItem item : data_.getProperties()) {
                     if (item.confInclude) {
                        if (first.isPropertyIncluded(item.device, item.name) && second.isPropertyIncluded(item.device, item.name)) {
                           if (!first.getSetting(item.device, item.name).getPropertyValue().contentEquals(second.getSetting(item.device, item.name).getPropertyValue())) {
                              same = false;
                           }
                        }
                     }
                  }
                  if (same) {
                     showMessageDialog("By removing properties, you would create duplicate presets.\nTo avoid duplicates when you remove properties, you should\nfirst delete some of the presets in this group.");
                     return false;
                  }

               }
            }
         }
      } catch (Exception e) {
         ReportingUtils.logError(e);
      }

      // Rename the group if its name has changed
      if (!newItem_ && !initName.contentEquals(newName)) {
         try {
            core_.renameConfigGroup(initName, newName);
         } catch (Exception e1) {
            ReportingUtils.logError(e1);
         }
      }

      if (newItem_) { // A new configuration group is being created.
         try {
            core_.defineConfigGroup(newName);

            for (PropertyItem item : data_.getProperties()) {
               if (item.confInclude) {
                  if (itemsIncludedCount == 1 && item.allowed.length > 0) {
                     /* ensure sorting of the property value list: */
                     if (PropertyType.Float == item.type) {
                        Arrays.sort(item.allowed, new SortFunctionObjects.DoubleStringComp());
                     } else if (PropertyType.Integer == item.type) {
                        Arrays.sort(item.allowed, new SortFunctionObjects.IntStringComp());
                     } else if (PropertyType.String == item.type) {
                        boolean allNumeric = true;
                        // test that first character of every possible value is a numeral
                        // if so, show user the list sorted by the numeric prefix
                        for (int k = 0; k < item.allowed.length; k++) {
                           if (!Character.isDigit(item.allowed[k].charAt(0))) {
                              allNumeric = false;
                              break;
                           }
                        }
                        if (allNumeric) {
                           Arrays.sort(item.allowed, new SortFunctionObjects.NumericPrefixStringComp());
                        } else {
                           Arrays.sort(item.allowed);
                        }
                     }

                     for (String allowedValue : item.allowed) {
                        if (!allowedValue.equals("")) {
                           // Make sure that forbiddedn characters do not make it into the Preset Name
                           String presetName = allowedValue.replaceAll("[/\\*!']", "-");
                           core_.defineConfig(newName, presetName, item.device, item.name, allowedValue);
                        }
                     }
                  } else {
                     core_.defineConfig(newName, "NewPreset", item.device, item.name, item.getValueInCoreFormat());
                  }
               }
            }

         } catch (Exception e) {
            ReportingUtils.logError(e);
         }
         // Make the first preset.
         if (itemsIncludedCount > 1) {
            new PresetEditor(newName, "NewPreset", studio_, core_, false);
         }
      } else {// An existing configuration group is being modified.
         // Apply configuration settings to all properties in the group.
         String cfg;
         Configuration unionCfg;
         try {
            // Get a configuration with the full list of properties.
            unionCfg = core_.getConfigGroupState(newName);

            for (PropertyItem item : data_.getProperties()) {
               if (!item.confInclude && unionCfg.isPropertyIncluded(item.device, item.name)) {
                  // If some presets have this property when they shouldn't, delete it.
                  for (int i = 0; i < cfgs.size(); i++) {
                     cfg = cfgs.get(i);
                     if (core_.getConfigData(newName, cfg).isPropertyIncluded(item.device, item.name)) {
                        core_.deleteConfig(newName, cfg, item.device, item.name);
                     }
                  }
               } else if (item.confInclude && !unionCfg.isPropertyIncluded(item.device, item.name)) {
                  // If some presets don't have this property when they should, add it with current values.
                  for (int i = 0; i < cfgs.size(); i++) {
                     cfg = cfgs.get(i);
                     if (!core_.getConfigData(groupName_, cfg).isPropertyIncluded(item.device, item.name)) {
                        core_.defineConfig(newName, cfg, item.device, item.name, item.getValueInCoreFormat());
                     }
                  }
               }
            }
         } catch (Exception e) {
            ReportingUtils.logError(e);
         }
      }

      ((MMStudio) studio_).setConfigChanged(true);
      return true;
   }
}
