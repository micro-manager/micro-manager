package org.micromanager.positionlist;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;

/**
 * Renders the first row of the position list table
 */
class FirstRowRenderer extends JLabel implements TableCellRenderer {
   private static final long serialVersionUID = 1L;

   public FirstRowRenderer(Font font) {
      setFont(font);
      setOpaque(true);
   }

   @Override
   public Component getTableCellRendererComponent(JTable table, 
         Object text, boolean isSelected, boolean hasFocus, 
         int row, int column) {

      // https://stackoverflow.com/a/3055930
      if (text == null) {
         return null;
      }

      setText((String) text);
      setBackground(Color.lightGray);
      return this;
   }
}
