/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package tables;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.prefs.Preferences;
import javax.swing.table.AbstractTableModel;
import mmcorej.CMMCore;
import mmcorej.Configuration;
import mmcorej.StrVector;
import org.micromanager.MMStudio;
import org.micromanager.api.ScriptInterface;
import org.micromanager.utils.PropertyItem;
import org.micromanager.utils.ReportingUtils;

/**
 * Model for table that allows selection of properties
 * 3 columns--checkbox, property, value, nickname
 */
public class PropertyChooserTableModel  extends AbstractTableModel {

   
   
   private ArrayList<PropertyItem> allProps_;
   private LinkedList<PropertyItem> storedProps_;
   private TreeMap<String, String> propLabels_;
   
   private CMMCore core_;
   private ScriptInterface mmAPI_;
   private Preferences prefs_;

   
   public PropertyChooserTableModel(Preferences prefs) {
      mmAPI_ = MMStudio.getInstance();
      core_ = mmAPI_.getMMCore();
      allProps_ = PropertyManager.readAllProperties();
      storedProps_ = PropertyManager.readStoredProperties(prefs, allProps_);
      propLabels_ = new TreeMap<String, String>();
      for (PropertyItem item : storedProps_) {
         propLabels_.put(item.device+"-"+item.name, PropertyManager.getPropLabel(prefs, item));
      }
      prefs_ = prefs;
   }
   
   public void storeProperties() {
      PropertyManager.saveStoredProperties(prefs_, storedProps_, propLabels_);
   }

   @Override
   public Class getColumnClass(int column) {
      if (column == 0) {
         return Boolean.class;
      }
      return String.class;
   }
   
   @Override
   public int getRowCount() {
     return allProps_.size();
   }

   @Override
   public int getColumnCount() {
      return 4;
   }
   
   @Override
   public void setValueAt(Object value, int row, int col) {
      if (col == 0) {
         PropertyItem item = allProps_.get(row);
         if (propLabels_.containsKey(item.device+"-"+item.name)) {
            propLabels_.remove(item.device+"-"+item.name);
            storedProps_.remove(storedProps_.indexOf(item));
         } else {
            propLabels_.put(item.device +"-"+ item.name, item.device +"-"+ item.name);
            storedProps_.add(item);
         }
         fireTableDataChanged();
      } else if (col == 3) {
         PropertyItem item = allProps_.get(row);
         propLabels_.put(item.device +"-"+ item.name, (String) value);
      }
   }

   @Override
   public Object getValueAt(int rowIndex, int columnIndex) {
      PropertyItem item = allProps_.get(rowIndex);
      if (columnIndex == 0) {
         //check box state
         return propLabels_.containsKey(item.device+"-"+item.name);
      } else if (columnIndex == 1) {
         return item.device + "-" + item.name;
      } else if (columnIndex == 2) {
         return item.value;
      } else {
         if (propLabels_.containsKey(item.device+"-"+item.name)) {
            return propLabels_.get(item.device+"-"+item.name);
         } else {
            return null;
         }
      }
   }

    @Override
   public String getColumnName(int column) {
      if (column == 0) {
         return "Include";
      } else if (column == 1) {
         return "Property";
      } else if (column == 2) {
         return "Value";
      } else {
         return "Label";
      }
   }

   @Override
   public boolean isCellEditable(int nRow, int nCol) {
      if (nCol == 0) {
         return true;
      } else if (nCol == 3) {
         return propLabels_.containsKey(allProps_.get(nRow).device + "-" + allProps_.get(nRow).name);
      }
      return false;
   }
   
    
   
}
