/**
 * Nico Stuurman, nico.stuurman at ucsf.edu
 * <p>
 * Copyright UCSF, 2017
 * <p>
 * Licensed under BSD license version 2.0
 * <p>
 * <p>
 * Extension of JTable that takes care of sorting
 *
 * @author - Nico Stuurman, September 2017
 * <p>
 * <p>
 * Copyright (c) 2017-2017, Regents of the University of California All rights reserved.
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

import javax.swing.JTable;
import javax.swing.event.ChangeEvent;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;

/**
 * Extension of JTable that takes care of sorting
 *
 * @author nico
 */
public class DataTable extends JTable {

   private boolean inLayout_;

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
      try {
         return convertRowIndexToModel(rawRow);
      } catch (IndexOutOfBoundsException ioobe) {
         return -1;
      }
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

   @Override
   public boolean getScrollableTracksViewportWidth() {
      return hasExcessWidth();
   }


   protected boolean hasExcessWidth() {
      return getPreferredSize().width < getParent().getWidth();
   }

   @Override
   public void doLayout() {
      if (hasExcessWidth()) {
         // fool super
         autoResizeMode = AUTO_RESIZE_SUBSEQUENT_COLUMNS;
      }
      inLayout_ = true;
      super.doLayout();
      inLayout_ = false;
      autoResizeMode = AUTO_RESIZE_OFF;
   }

   @Override
   public void columnMarginChanged(ChangeEvent e) {
      if (isEditing()) {
         removeEditor();
      }
      TableColumn resizingColumn = getTableHeader().getResizingColumn();
      // Need to do this here, before the parent's
      // layout manager calls getPreferredSize().
      if (resizingColumn != null && autoResizeMode == AUTO_RESIZE_OFF
            && !inLayout_) {
         resizingColumn.setPreferredWidth(resizingColumn.getWidth());
      }
      resizeAndRepaint();
   }

   public void update() {
      super.resizeAndRepaint();
   }

}
