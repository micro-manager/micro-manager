package org.micromanager.internal.dialogs.introdialogparts;

import java.awt.Component;
import java.awt.ComponentOrientation;
import java.awt.FontMetrics;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicComboBoxRenderer;

class ComboBoxCellRenderer extends BasicComboBoxRenderer {
   int width_;

   public ComboBoxCellRenderer(int width) {
      super();
      width_ = width;
      setHorizontalAlignment(SwingConstants.RIGHT);
      setBorder(new EmptyBorder(0, 0, 2, 20)); // Add some padding to the right
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
      FontMetrics metrics = component.getFontMetrics(component.getFont());
      if (index > -1 && component instanceof JLabel) {
         JLabel label = (JLabel) component;
         label.setHorizontalAlignment(SwingConstants.RIGHT);
         label.setBorder(new EmptyBorder(0, 0, 2,  20)); // Add some padding to the right
         String text = label.getText();
         boolean elipsis = metrics.stringWidth(text) > width_;
         while (metrics.stringWidth(text) > width_) {
            text = text.substring(1);
         }
         if (elipsis) {
            text = "..." + text;
         }
         label.setText(text);
      }
      return component;
   }

}
