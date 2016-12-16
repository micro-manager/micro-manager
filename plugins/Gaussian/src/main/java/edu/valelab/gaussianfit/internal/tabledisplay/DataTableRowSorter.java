/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.valelab.gaussianfit.internal.tabledisplay;

import java.util.Comparator;
import javax.swing.table.TableRowSorter;

/**
 *
 * @author nico
 */
public class DataTableRowSorter extends TableRowSorter<DataTableModel> {
   private final DataTableModel model_;
   
   public DataTableRowSorter (DataTableModel model) {
      super (model);
      model_ = model;
   }
   
   @Override
   public Comparator<?> getComparator(int column) {
      return super.getComparator(column);
   }
}
