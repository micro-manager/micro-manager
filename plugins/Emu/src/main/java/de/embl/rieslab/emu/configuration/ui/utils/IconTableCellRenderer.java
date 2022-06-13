package de.embl.rieslab.emu.configuration.ui.utils;

import de.embl.rieslab.emu.utils.ColorRepository;
import java.awt.Component;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;

/**
 * TableCellRenderer using {@link ColorIcon}.
 *
 * @author Joran Deschamps
 */
public class IconTableCellRenderer implements TableCellRenderer {
   @Override
   public Component getTableCellRendererComponent(JTable table,
                                                  Object value, boolean isSelected,
                                                  boolean hasFocus, int row,
                                                  int column) {
      JLabel label = new JLabel((String) value);
      label.setIcon(new ColorIcon(ColorRepository.getColor((String) value)));
      return label;
   }

}