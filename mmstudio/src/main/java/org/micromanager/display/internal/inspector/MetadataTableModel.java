///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Display implementation
//-----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger, 2015
//
// COPYRIGHT:    University of California, San Francisco, 2015
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

package org.micromanager.display.internal.inspector;

import java.util.Arrays;
import java.util.Vector;

import javax.swing.table.AbstractTableModel;

import org.json.JSONException;
import org.json.JSONObject;

import org.micromanager.internal.utils.MDUtils;
import org.micromanager.internal.utils.ReportingUtils;

class MetadataTableModel extends AbstractTableModel {

   private Vector<Vector<String>> data_;
   private JSONObject priorMetadata_;

   private static final String[] columnNames_ = {"Property", "Value"};

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
      return columnNames_[colIndex];
   }

   public synchronized void setMetadata(JSONObject newMetadata,
         boolean shouldShowUnchangingValues) {
      clear();
      JSONObject displayedMetadata = newMetadata;
      if (!shouldShowUnchangingValues && priorMetadata_ != null) {
         // Determine which keys in the new metadata differ from the old
         // metadata.
         try {
            JSONObject changingMetadata = new JSONObject();
            for (String key : MDUtils.getKeys(newMetadata)) {
               if (!priorMetadata_.has(key)) {
                  // Must be different.
                  changingMetadata.put(key, newMetadata.get(key));
                  continue;
               }
               // Check for nullity, then compare equality by way of strings.
               String priorVal = "";
               if (priorMetadata_.get(key) != null) {
                  priorVal = priorMetadata_.getString(key);
               }
               String newVal = "";
               if (newMetadata.get(key) != null) {
                  newVal = newMetadata.getString(key);
               }
               if (!priorVal.contentEquals(newVal)) {
                  // Values differ.
                  changingMetadata.put(key, newVal);
               }
            }
            displayedMetadata = changingMetadata;
         }
         catch (JSONException e) {
            ReportingUtils.showError(e, "Failed to determine changing metadata.");
         }
      }
      priorMetadata_ = newMetadata;

      String[] keys = MDUtils.getKeys(displayedMetadata);
      Arrays.sort(keys);
      for (String key : keys) {
         Vector<String> rowData = new Vector<String>();
         rowData.add(key);
         try {
            rowData.add(displayedMetadata.getString(key));
         } catch (JSONException ex) {
            //ReportingUtils.logError(ex);
         }
         addRow(rowData);
      }

      fireTableDataChanged();
   }
}
