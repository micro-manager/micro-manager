package org.micromanager.internal.positionlist;

import java.awt.Component;
import java.awt.Font;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;
import org.micromanager.Studio;

/**
 * Renders the first row of the position list table.
 */
class FirstRowRenderer extends JLabel implements TableCellRenderer {
   private static final long serialVersionUID = 1L;
   private final Studio studio_;

   public FirstRowRenderer(Studio studio, Font font) {
      studio_ = studio;
      super.setFont(font);
      super.setOpaque(true);
   }

   @Override
   public Component getTableCellRendererComponent(JTable table,
                                                  Object text, boolean isSelected, boolean hasFocus,
                                                  int row, int column) {

      setText((String) text);
      // HACK: use the "disabled" color for this row to differentiate it from
      // other rows.
      setBackground(studio_.app().skin().getDisabledBackgroundColor());
      setForeground(studio_.app().skin().getEnabledTextColor());
      return this;
   }
}
