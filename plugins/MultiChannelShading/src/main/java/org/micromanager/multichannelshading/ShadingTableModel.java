///////////////////////////////////////////////////////////////////////////////
//FILE:          ShadingTableModel.java
//PROJECT:       Micro-Manager  
//SUBSYSTEM:     MultiChannelShading plugin
//-----------------------------------------------------------------------------
//
// AUTHOR:       Kurt Thorn, Nico Stuurman
//
// COPYRIGHT:    University of California, San Francisco 2014
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


package org.micromanager.multichannelshading;

import java.util.ArrayList;
import java.util.List;
import javax.swing.table.AbstractTableModel;
import org.micromanager.Studio;

/**
 * Model for the table representing channel presets and flatfield files.
 *
 * @author nico
 */
@SuppressWarnings("serial")
public class ShadingTableModel extends AbstractTableModel {
   private final Studio studio_;
   public final String[] columnNames_ = new String[] {
         "Preset",
         "Image File",
         "",
         ""
   };
   private String channelGroup_;
   private List<String> presetList_;
   private List<String> fileList_;
   private final ImageCollection imageCollection_;

   /**
    * Constructor.
    *
    * @param studio Studio
    * @param imageCollection Collection of background and flatfield images
    */
   public ShadingTableModel(Studio studio, ImageCollection
         imageCollection) {
      studio_ = studio;
      imageCollection_ = imageCollection;
      presetList_ = new ArrayList<>();
      fileList_ = new ArrayList<>();
   }


   @Override
   public int getRowCount() {
      if (fileList_.size() < presetList_.size()) {
         return fileList_.size();
      }
      return presetList_.size();
   }

   @Override
   public int getColumnCount() {
      return columnNames_.length;
   }

   @Override
   public String getColumnName(int columnIndex) {
      return columnNames_[columnIndex];
   }

   @Override
   public Object getValueAt(int row, int column) {
      switch (column) {
         case 0:
            if (presetList_.size() <= row) {
               return "None";
            } else {
               return presetList_.get(row);
            }
         case 1:
            if (fileList_.size() <= row) {
               return "";
            } else {
               return fileList_.get(row);
            }
         case 2:
            //return new JButton("...");
            break;
         case 3:
            break;
         default:
            break;
      }
      return null;
   }


   @Override
   public boolean isCellEditable(int rowIndex, int columnIndex) {
      return true;
   }

   @Override
   public void setValueAt(Object value, int row, int column) {
      switch (column) {
         case 0:
            presetList_.set(row, (String) value);
            updateFlatFieldImage(row);
            break;
         case 1:
            fileList_.set(row, (String) value);
            updateFlatFieldImage(row);
            break;
         default:
            break;
      }
   }

   public void setChannelGroup(String newGroup) {
      try {
         // first save our settings
         if (channelGroup_ != null) {
            String[] channels = presetList_.toArray(new String[0]);
            String[] files = fileList_.toArray(new String[0]);
            studio_.profile().getSettings(this.getClass()).putStringList(
                  channelGroup_ + "-channels", channels);
            studio_.profile().getSettings(this.getClass()).putStringList(
                  channelGroup_ + "-files", files);
         }
         imageCollection_.clearFlatFields();
         channelGroup_ = newGroup;

         // then restore mapping from preferences
         fileList_.clear();
         presetList_.clear();
         // Strange workaround since we can not pass null as default
         List<String> emptyList = new ArrayList<>(0);
         List<String> channels = studio_.profile().getSettings(this.getClass())
                     .getStringList(channelGroup_ + "-channels", emptyList);
         List<String> files = studio_.profile().getSettings(this.getClass())
                     .getStringList(channelGroup_ + "-files", emptyList);
         if (channels != null && files != null) {
            for (int i = 0; i < channels.size() && i < files.size(); i++) {
               imageCollection_.addFlatField(channels.get(i), files.get(i));
               presetList_.add(channels.get(i));
               fileList_.add(files.get(i));
            }
         }

      } catch (ShadingException ex) {
         studio_.logs().showError(ex);
      }
      fireTableDataChanged();
   }

   /**
    * Replaces the current preset/file lists with the provided ones.
    * Use this to restore per-instance settings that override profile defaults.
    *
    * @param presets list of preset names
    * @param files list of flatfield file paths (parallel to presets)
    */
   public void loadPresets(List<String> presets, List<String> files) {
      imageCollection_.clearFlatFields();
      presetList_.clear();
      fileList_.clear();
      try {
         for (int i = 0; i < presets.size() && i < files.size(); i++) {
            String file = files.get(i);
            presetList_.add(presets.get(i));
            fileList_.add(file);
            if (file != null && !file.isEmpty()) {
               imageCollection_.addFlatField(presets.get(i), file);
            }
         }
      } catch (ShadingException ex) {
         studio_.logs().showError(ex);
      }
      fireTableDataChanged();
   }

   public String getChannelGroup() {
      return channelGroup_;
   }

   public void addRow() {
      String[] availablePresets = getAvailablePresets();
      if (availablePresets != null && availablePresets.length > 0) {
         presetList_.add(availablePresets[0]);
         fileList_.add("");
         fireTableDataChanged();
      }
      // TODO: handle error 
   }

   /**
    * Discovers the unused presets in the current channel group.
    *
    * @return Array of unused presets
    */
   public String[] getAvailablePresets() {
      String[] presets = {"Default"};
      if (!channelGroup_.isEmpty()) {
         presets = studio_.getCMMCore().getAvailableConfigs(channelGroup_).toArray();
      }
      String[] usedPresets = getUsedPresets();
      String[] availablePresets = new String[presets.length - usedPresets.length];
      for (String preset : presets) {
         boolean found = false;
         int j = 0;
         for (String usedPreset : usedPresets) {
            if (preset.equals(usedPreset)) {
               found = true;
            }
         }
         if (!found) {
            availablePresets[j] = preset;
            j++;
         }
      }
      return availablePresets;
   }

   public String[] getUsedPresets() {
      String[] presets = new String[presetList_.size()];
      for (int i = 0; i < presetList_.size(); i++) {
         presets[i] = presetList_.get(i);
      }
      return presets;
   }

   public String[] getUsedPresets(int excludedRow) {
      String[] presets = new String[presetList_.size() - 1];
      int j = 0;
      for (int i = 0; i < presetList_.size(); i++) {
         if (i != excludedRow) {
            presets[j] = presetList_.get(i);
            j++;
         }
      }
      return presets;
   }

   public int getNumberOfPresetsInCurrentGroup() {
      if (channelGroup_.isEmpty()) {
         return 1;
      }
      return (int) studio_.getCMMCore().getAvailableConfigs(channelGroup_).size();
   }

   public int getUnusedNumberOfPresetsInCurrentGroup() {
      return getNumberOfPresetsInCurrentGroup() - getUsedPresets().length;
   }

   public ImagePlusInfo getFlatFieldImage(String channelGroup, String preset) {
      if (channelGroup.equals(channelGroup_)) {
         return imageCollection_.getFlatField(preset);
      }
      return null;
   }

   /**
    * Removes selected rows from the tablemodel.
    * Calls fireTableDataChanged to update the UI.
    *
    * @param selectedRows - array containing selected row numbers
    */
   public void removeRow(int[] selectedRows) {
      // Since we have ordered lists, rebuild them
      List<String> presetList = new ArrayList<String>();
      List<String> fileList = new ArrayList<String>();
      for (int i = 0; i < presetList_.size(); i++) {
         boolean removeRow = false;
         for (int j = 0; j < selectedRows.length; j++) {
            if (i == selectedRows[j]) {
               removeRow = true;
               // TODO: channelPrefs_.remove(presetList_.get(i));
               imageCollection_.removeFlatField(presetList_.get(i));
            }
         }
         if (!removeRow) {
            presetList.add(presetList_.get(i));
            fileList.add(fileList_.get(i));
         }
      }
      presetList_ = presetList;
      fileList_ = fileList;

      fireTableDataChanged();
   }

   private void updateFlatFieldImage(int row) {
      if (fileList_.get(row) != null && !fileList_.get(row).isEmpty()) {
         String preset = presetList_.get(row);
         if (preset != null) {
            try {
               imageCollection_.addFlatField(preset, fileList_.get(row));
            } catch (ShadingException ex) {
               studio_.logs().showError(ex);
            }
            studio_.profile().getSettings(this.getClass()).putString(preset, fileList_.get(row));
         }
      }

   }

}
