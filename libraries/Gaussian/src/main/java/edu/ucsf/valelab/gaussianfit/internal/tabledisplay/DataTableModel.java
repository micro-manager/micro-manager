/**
 * Nico Stuurman, nico.stuurman at ucsf.edu
 * <p>
 * <p>
 * <p>
 * Model for the data shown in the Data window in Localization microscopy plugin
 *
 * @author - Nico Stuurman, September 2016
 * <p>
 * <p>
 * Copyright (c) 2016-2017, Regents of the University of California All rights reserved.
 * <p>
 * Redistribution and use in source and binary forms, with or without modification, are permitted
 * provided that the following conditions are met:
 * <p>
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions
 * and the following disclaimer. 2. Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * <p>
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY
 * WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * <p>
 * The views and conclusions contained in the software and documentation are those of the authors
 * and should not be interpreted as representing official policies, either expressed or implied, of
 * the FreeBSD Project.
 */

package edu.ucsf.valelab.gaussianfit.internal.tabledisplay;

import edu.ucsf.valelab.gaussianfit.data.RowData;
import java.util.ArrayList;
import javax.swing.table.AbstractTableModel;

/**
 * Model for the data shown in the Data window in Localization microscopy plugin
 *
 * @author nico
 */
public class DataTableModel extends AbstractTableModel {

   private final String[] columnNames_ = {"ID", "Image", "Nr of spots",
         "2C Reference", "Ch.", "X", "Y", "std", "nrPhotons"};
   private final ArrayList<RowData> rowData_;
   public final static NullClass NULLINSTANCE = new NullClass();

   public DataTableModel() {
      rowData_ = new ArrayList<RowData>();
   }

   public void addRowData(RowData row) {
      rowData_.add(row);
   }

   public void fireRowInserted() {
      super.fireTableRowsInserted(rowData_.size() - 1, rowData_.size() - 1);
   }

   public RowData getRow(int rowNr) {
      return rowData_.get(rowNr);
   }

   public void removeRow(int rowNr) {
      rowData_.remove(rowNr);
      super.fireTableRowsDeleted(rowNr, rowNr);
   }

   public void removeRows(int[] rows) {
      for (int row = rows.length - 1; row >= 0; row--) {
         rowData_.remove(rows[row]);
      }
      super.fireTableDataChanged();
   }


   /**
    * Return a dataset
    *
    * @param ID with requested ID.
    * @return RowData with selected ID, or null if not found
    */
   public RowData getDataByID(int ID) {
      for (RowData row : rowData_) {
         if (row.ID_ == ID) {
            return row;
         }
      }
      return null;
   }

   @Override
   public String getColumnName(int col) {
      return columnNames_[col];
   }

   @Override
   public int getRowCount() {
      if (rowData_ == null) {
         return 0;
      }
      return rowData_.size();
   }

   @Override
   public int getColumnCount() {
      return columnNames_.length;
   }

   @Override
   public Object getValueAt(int row, int col) {
      if (col == 0 && rowData_ != null) {
         return rowData_.get(row).ID_;
      } else if (col == 1 && rowData_ != null) {
         return rowData_.get(row).getName();
      } else if (col == 2) {
         return rowData_.get(row).spotList_.size();
      } else if (col == 3) {
         return rowData_.get(row).colCorrRef_;
      } else if (col == 4) {
         if (rowData_.get(row).isTrack_) {
            return rowData_.get(row).channels_;
         } else {
            return NULLINSTANCE;
         }
      } else if (col == 5) {
         if (rowData_.get(row).isTrack_) {
            return rowData_.get(row).spotList_.get(0).getXCenter();
         } else {
            return NULLINSTANCE;
         }
      } else if (col == 6) {
         if (rowData_.get(row).isTrack_) {
            return rowData_.get(row).spotList_.get(0).getYCenter();
         } else {
            return NULLINSTANCE;
         }
      } else if (col == 7) {
         if (rowData_.get(row).isTrack_) {
            return rowData_.get(row).std_;
         } else {
            return NULLINSTANCE;
         }
      } else if (col == 8) {
         if (rowData_.get(row).isTrack_) {
            return Math.round(rowData_.get(row).totalNrPhotons_);
         } else {
            return NULLINSTANCE;
         }
      }

      return getColumnName(col);

   }

   @Override
   public boolean isCellEditable(int row, int col) {
      return col == 1;
   }

   @Override
   public Class getColumnClass(int col) {
      switch (col) {
         case 1:
         case 3:
            return String.class;
         case 0:
         case 2:
         case 4:
         case 8:
            return Integer.class;
         default:
            return Double.class;
      }
   }

   @Override
   public void setValueAt(Object value, int row, int col) {
      if (col == 1) {
         rowData_.get(row).setName((String) value);
      }
      fireTableCellUpdated(row, col);
   }
}
