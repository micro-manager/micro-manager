package org.micromanager.internal.dialogs.introdialogparts;

import java.awt.Component;
import java.awt.ComponentOrientation;
import javax.swing.JList;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicComboBoxRenderer;

class ComboBoxCellRenderer extends BasicComboBoxRenderer {

   public ComboBoxCellRenderer() {
      super();
      setHorizontalAlignment(SwingConstants.RIGHT);
      setBorder(new EmptyBorder(0, 0, 2, 10)); // Add some padding to the right
   }

   @Override
   public Component getListCellRendererComponent(JList list,
                                                 Object value,
                                                 int index,
                                                 boolean isSelected,
                                                 boolean cellHasFocus) {
      Component component = super.getListCellRendererComponent(
            list,
            value,
            index,
            isSelected,
            cellHasFocus);
      if (isSelected) {
         component.setBackground(list.getSelectionBackground());
         component.setForeground(list.getSelectionForeground());
      } else {
         component.setBackground(list.getBackground());
         component.setForeground(list.getForeground());
      }
      component.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
      return component;
   }
}
