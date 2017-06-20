///////////////////////////////////////////////////////////////////////////////
// AUTHOR:       Henry Pinkard, henry.pinkard@gmail.com
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
//
package org.micromanager.plugins.magellan.propsandcovariants;

import java.awt.Color;
import java.awt.Component;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;


public class CovariantValueCellRenderer implements TableCellRenderer {
   // This method is called each time a cell in a column
   // using this renderer needs to be rendered.

   JLabel lab_ = new JLabel();

   public CovariantValueCellRenderer() {
      super();
   }


   @Override
   public Component getTableCellRendererComponent(JTable table, Object value,
           boolean isSelected, boolean hasFocus, int rowIndex, int colIndex) {

      // https://stackoverflow.com/a/3055930
      if (value == null) {
         return null;
      }

      CovariantPairValuesTableModel data = (CovariantPairValuesTableModel) table.getModel();       
      Covariant cv = colIndex == 0 ?  data.getPairing().getIndependentCovariant() : data.getPairing().getDependentCovariant();
      
      lab_.setOpaque(true);
      lab_.setHorizontalAlignment(JLabel.LEFT);
      Component comp;
      
//      if (cv.hasLimits()) {
//         SliderPanel slider = new SliderPanel();
//         if (cv.getType() == CovariantType.INT) {
//            slider.setLimits(cv.getLowerLimit().intValue(), cv.getUpperLimit().intValue());
//         } else {
//            slider.setLimits(cv.getLowerLimit().doubleValue(), cv.getUpperLimit().doubleValue());
//         }
//         try {
//            slider.setText(((CovariantValue) value).toString());
//         } catch (ParseException ex) {
//            Log.log(ex);
//         }
//         slider.setToolTipText(data.getPairing().getValue(colIndex, rowIndex).toString());
//         comp = slider;
//      } else {
      try {
         lab_.setText(data.getPairing().getValue(colIndex, rowIndex).toString());
         comp = lab_;

//      }
      
      if (rowIndex == table.getSelectedRow()) {
         Component c = (new DefaultTableCellRenderer()).getTableCellRendererComponent(table, value, 
                 true, hasFocus, rowIndex, colIndex);
         comp.setForeground(c.getForeground());
         comp.setBackground(c.getBackground());
      } else {
         comp.setBackground(Color.WHITE);
         comp.setForeground(Color.black);
      }

      } catch (Exception e) {
         System.out.println();
         throw new RuntimeException();
      }
      return comp;
   }

   // The following methods override the defaults for performance reasons
   public void validate() {
   }

   public void revalidate() {
   }

   protected void firePropertyChange(String propertyName, Object oldValue, Object newValue) {
   }

   public void firePropertyChange(String propertyName, boolean oldValue, boolean newValue) {
   }
}

