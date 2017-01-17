/**
 * 
 * Nico Stuurman, nico.stuurman at ucsf.edu
 * 
 * Copyright UCSF, 2017
 * 
 * Licensed under BSD license version 2.0
 * 
 *
 * Extension of JTable that takes care of sorting
 * 
 * @author - Nico Stuurman, September 2017
 * 
 * 
Copyright (c) 2017-2017, Regents of the University of California
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.
2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

The views and conclusions contained in the software and documentation are those
of the authors and should not be interpreted as representing official policies,
either expressed or implied, of the FreeBSD Project.
 */

package edu.valelab.gaussianfit.internal.tabledisplay;

import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;

/**
 * Extension of JTable that takes care of sorting
 * 
 * 
 * @author nico
 */
public class DataTable extends JTable {
   
   static class DoubleRenderer extends DefaultTableCellRenderer {
      @Override
      public void setValue(Object value) {
         if (value.getClass() == NullClass.class) {
            setText("");
         } else {
            setText(String.format("%.2f", value));
         }
      }
   }

   static class IntegerRenderer extends DefaultTableCellRenderer {
      @Override
      public void setValue(Object value) {
         if (value.getClass() == NullClass.class) {
            setText("");
         } else {
            setText("" + value);
         }
      }
   }

   

   public int getSelectedRowSorted() {
      int rawRow = super.getSelectedRow();
      return convertRowIndexToModel(rawRow);
   }
   
   public int[] getSelectedRowsSorted() {
      int[] rawRows = super.getSelectedRows();
      int[] sortedRows = new int[rawRows.length];
      for (int i = 0; i < rawRows.length; i++) {
         sortedRows[i] = super.convertRowIndexToModel(rawRows[i]);
      }
      return sortedRows;
   }
   
   @Override
   public TableCellRenderer getCellRenderer(int row, int column) {
      Class<?> columnClass = super.getModel().getColumnClass(column);
      if (columnClass.equals(Double.class)) {
         return new DoubleRenderer();
      } else if (columnClass.equals(Integer.class)) {
         return new IntegerRenderer();
      } 
      return super.getCellRenderer(row, column);
   }
}
