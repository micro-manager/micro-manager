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
