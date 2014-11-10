
package org.micromanager.multichannelshading;

import ij.ImagePlus;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import javax.swing.table.AbstractTableModel;
import org.micromanager.api.ScriptInterface;

/**
 *
 * @author nico
 */
public class ShadingTableModel extends AbstractTableModel {
   private final ScriptInterface gui_;
   public final int PRESET = 0;
   public final int IMAGEFILE = 1;
   public final int LOADBUTTON = 2;
   public final String[] COLUMN_NAMES = new String[] {
         "Preset",
         "Image File",
         ""
   };
   private String channelGroup_;
   private final Preferences prefs_;
   private Preferences channelPrefs_;
   private List<String> presetList_;
   private List<String> fileList_;
   private final HashMap<String, SimpleFloatImage> flatFieldImages_ = new
        HashMap<String, SimpleFloatImage>();
   
   public ShadingTableModel(ScriptInterface gui) {
      gui_ = gui;
      prefs_ = Preferences.userNodeForPackage(this.getClass());
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
         if (channelGroup_ != null) {
            channelPrefs_ = prefs_.node(channelGroup_);
            channelPrefs_.clear();
            for (int i = 0; i < presetList_.size() && i < fileList_.size(); i++) {
               channelPrefs_.put(presetList_.get(i), fileList_.get(i));
            }
         }
         flatFieldImages_.clear();
         channelGroup_ = newGroup;
         // restore mapping from preferences
         fileList_.clear();
         presetList_.clear();
         channelPrefs_ = prefs_.node(channelGroup_);

         for (String key : channelPrefs_.keys()) {
            presetList_.add(key);
            String file = channelPrefs_.get(key, "");
            fileList_.add(file);
            if (file.length() > 0) {
               // TODO: handle exceptions
               ij.io.Opener opener = new ij.io.Opener();
               ImagePlus ip = opener.openImage(file);
               flatFieldImages_.put(key, new SimpleFloatImage(ip));
            }
         }
      } catch (BackingStoreException ex) {
         gui_.logError(ex);
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
      String[] presets = gui_.getMMCore().getAvailableConfigs(channelGroup_).
              toArray();
      String[] usedPresets = getUsedPresets();
      String[] availablePresets = new String[presets.length - usedPresets.length];
      for (String preset : presets) {
         boolean found = false;
         int j = 0;
         for (int i = 0; i < usedPresets.length; i++) {
            if (preset.equals(usedPresets[i])) {
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
      return (int) gui_.getMMCore().getAvailableConfigs(channelGroup_).size();
   }
   
   public int getUnusedNumberOfPresetsInCurrentGroup() {
      return getNumberOfPresetsInCurrentGroup() - getUsedPresets().length;
   }
   
   /**
    * Removes selected rows from the tablemodel
    * calls fireTableDataChanged to update the UI
    * @param selectedRows - array containing selected row numbers
    */
   public void removeRow(int[] selectedRows) {
      List<String> presetList = new ArrayList<String>();
      List<String> fileList = new ArrayList<String>();
      for (int i = 0; i < presetList_.size(); i++ ) {
         boolean isSelected = false;
         for (int j = 0; j < selectedRows.length; j++) {
            if (i == selectedRows[j]) {
               isSelected = true;
            }
         }
         if (!isSelected) {
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
         ij.io.Opener opener = new ij.io.Opener();
         ImagePlus ip = opener.openImage(fileList_.get(row));
         SimpleFloatImage flatFieldImage = new SimpleFloatImage(ip);
         if (presetList_.get(row) != null) {
            flatFieldImages_.put(presetList_.get(row), flatFieldImage);
         }
      }
         
   }
   
}
