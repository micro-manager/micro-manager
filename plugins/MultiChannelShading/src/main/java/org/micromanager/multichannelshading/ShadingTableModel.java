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
 *
 * @author nico
 */
public class ShadingTableModel extends AbstractTableModel {
   private final Studio gui_;
   public final int PRESET = 0;
   public final int IMAGEFILE = 1;
   public final int LOADBUTTON = 2;
   public final String[] COLUMN_NAMES = new String[] {
         "Preset",
         "Image File",
         ""
   };
   private String channelGroup_;
   private List<String> presetList_;
   private List<String> fileList_;
   private final ImageCollection imageCollection_;
   
   public ShadingTableModel(Studio gui, ImageCollection 
           imageCollection) {
      gui_ = gui;
      imageCollection_ = imageCollection;
      presetList_ = new ArrayList<String>();
      fileList_ = new ArrayList<String>();
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
      return COLUMN_NAMES.length;
   }
   
   @Override
   public String getColumnName(int columnIndex) {
      return COLUMN_NAMES[columnIndex];
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
      }     
   }
   
   public void setChannelGroup(String newGroup) {
      try {
         // first save our settings
         if (channelGroup_ != null) {
            String[] channels = presetList_.toArray(
                    new String[presetList_.size()]);
            String[] files = fileList_.toArray(
                    new String[fileList_.size()]);

            gui_.profile().setStringArray(this.getClass(), 
                    channelGroup_ + "-channels", channels);
            gui_.profile().setStringArray(this.getClass(), 
                    channelGroup_ + "-files", files);
         }
         imageCollection_.clearFlatFields();
         channelGroup_ = newGroup;
         
         // then restore mapping from preferences
         fileList_.clear();
         presetList_.clear();
         String[] channels = gui_.profile().getStringArray(this.getClass(), 
                 channelGroup_ + "-channels", null);
         String[] files = gui_.profile().getStringArray(this.getClass(), 
                 channelGroup_ + "-files", null);
         if (channels != null && files != null) {
            for (int i = 0; i < channels.length && i < files.length; i++) {
               imageCollection_.addFlatField(channels[i], files[i]);
               presetList_.add(channels[i]);
               fileList_.add(files[i]);
            }
         }

      } catch (ShadingException ex) {
         gui_.logs().showError(ex);
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
   
   public String[] getAvailablePresets() {
      String[] presets = gui_.getCMMCore().getAvailableConfigs(channelGroup_).
              toArray();
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
      return (int) gui_.getCMMCore().getAvailableConfigs(channelGroup_).size();
   }
   
   public int getUnusedNumberOfPresetsInCurrentGroup() {
      return getNumberOfPresetsInCurrentGroup() - getUsedPresets().length;
   }
   
   public ImagePlusInfo getFlatFieldImage (String channelGroup, String preset) {
      if (channelGroup.equals(channelGroup_)) {
         return imageCollection_.getFlatField(preset);
      }
      return null;
   }
   
   /**
    * Removes selected rows from the tablemodel
    * calls fireTableDataChanged to update the UI
    * @param selectedRows - array containing selected row numbers
    */
   public void removeRow(int[] selectedRows) {
      // Since we have ordered lists, rebuild them
      List<String> presetList = new ArrayList<String>();
      List<String> fileList = new ArrayList<String>();
      for (int i = 0; i < presetList_.size(); i++ ) {
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
               gui_.logs().showError(ex);
            }
            gui_.profile().setString(this.getClass(), preset, fileList_.get(row));
         }
      }
         
   }
   
}
