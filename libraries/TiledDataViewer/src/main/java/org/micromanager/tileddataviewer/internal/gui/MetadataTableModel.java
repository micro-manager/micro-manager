package org.micromanager.tileddataviewer.internal.gui;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Vector;
import javax.swing.table.AbstractTableModel;
import mmcorej.org.json.JSONException;
import mmcorej.org.json.JSONObject;

/**
 *
 * @author henrypinkard
 */
public class MetadataTableModel extends AbstractTableModel {

   private final String[] columnNames = {"Property", "Value"};
   Vector<Vector<String>> data_;

   MetadataTableModel() {
      data_ = new Vector<Vector<String>>();
   }

   @Override
   public int getRowCount() {
      return data_.size();
   }

   public void addRow(Vector<String> rowData) {
      data_.add(rowData);
   }

   @Override
   public int getColumnCount() {
      return 2;
   }

   @Override
   public synchronized Object getValueAt(int rowIndex, int columnIndex) {
      if (data_.size() > rowIndex) {
         Vector<String> row = data_.get(rowIndex);
         if (row.size() > columnIndex) {
            return data_.get(rowIndex).get(columnIndex);
         } else {
            return "";
         }
      } else {
         return "";
      }
   }

   public void clear() {
      data_.clear();
   }

   @Override
   public String getColumnName(int colIndex) {
      return columnNames[colIndex];
   }

   public synchronized void setMetadata(JSONObject md) {
      clear();
      if (md != null) {
         int n = md.length();
         String[] keys = new String[n];
         Iterator<String> keysIter = md.keys();
         for (int i = 0; i < n; ++i) {
            keys[i] = keysIter.next();
         }
         Arrays.sort(keys);
         for (String key : keys) {
            Vector<String> rowData = new Vector<String>();
            rowData.add(key);
            try {
               rowData.add(md.getString(key));
            } catch (JSONException ex) {
               //Log.log(ex);
            }
            addRow(rowData);
         }
      }
      fireTableDataChanged();
   }
}
