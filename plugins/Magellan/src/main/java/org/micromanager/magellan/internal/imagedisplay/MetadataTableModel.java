/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.magellan.internal.imagedisplay;

import java.util.Arrays;
import java.util.Vector;
import javax.swing.table.AbstractTableModel;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.magellan.internal.misc.MD;

/**
 *
 * @author henrypinkard
 */


public class MetadataTableModel extends AbstractTableModel {

      private final String[] COLUMN_NAMES = {"Property", "Value"};
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
         return COLUMN_NAMES[colIndex];
      }

      public synchronized void setMetadata(JSONObject md) {
         clear();
         if (md != null) {
            String[] keys = MD.getKeys(md);
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

