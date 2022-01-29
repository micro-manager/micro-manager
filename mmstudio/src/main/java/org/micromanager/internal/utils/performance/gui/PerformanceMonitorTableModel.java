// Copyright (C) 2017 Open Imaging, Inc.
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

package org.micromanager.internal.utils.performance.gui;

import java.util.List;
import java.util.Map;
import javax.swing.table.AbstractTableModel;
import org.micromanager.internal.utils.MustCallOnEDT;
import org.micromanager.internal.utils.performance.AbstractExponentialSmoothing;

/**
 *
 * @author Mark A. Tsuchida
 */
final class PerformanceMonitorTableModel extends AbstractTableModel {
   private List<Map.Entry<String, ? extends AbstractExponentialSmoothing>> entries_;

   private enum Column {
      COL_STATNAME("Statistic"),
      COL_AVERAGE("Average"),
      COL_STDEV("Stdev");

      private final String name_;

      Column(String name) {
         name_ = name;
      }

      static Column getByPosition(int c) {
         return values()[c];
      }

      static int getCount() {
         return values().length;
      }

      String getName() {
         return name_;
      }

      Class<?> getColumnClass() {
         return String.class;
      }
   }

   @MustCallOnEDT
   void setData(List<Map.Entry<String, ? extends AbstractExponentialSmoothing>> entries) {
      entries_ = entries;
      fireTableStructureChanged();
   }

   @Override
   public int getRowCount() {
      if (entries_ == null) {
         return 0;
      }
      return entries_.size();
   }

   @Override
   public int getColumnCount() {
      return Column.getCount();
   }

   @Override
   public String getColumnName(int columnIndex) {
      return Column.getByPosition(columnIndex).getName();
   }

   @Override
   public Class<?> getColumnClass(int columnIndex) {
      return Column.getByPosition(columnIndex).getColumnClass();
   }

   @Override
   public boolean isCellEditable(int rowIndex, int columnIndex) {
      return false;
   }

   @Override
   public Object getValueAt(int rowIndex, int columnIndex) {
      Map.Entry<String, ? extends AbstractExponentialSmoothing> entry = entries_.get(rowIndex);
      switch (Column.getByPosition(columnIndex)) {
         case COL_STATNAME:
            return entry.getKey();
         case COL_AVERAGE:
            return String.format("%.3g", entry.getValue().getAverage());
         case COL_STDEV:
            return String.format("%.3g", entry.getValue().getStandardDeviation());
         default:
            throw new IndexOutOfBoundsException();
      }
   }

   @Override
   public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
      throw new UnsupportedOperationException("Read only");
   }
}