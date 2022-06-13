package de.embl.rieslab.emu.configuration.ui.utils;

import java.awt.Component;
import java.util.Map;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JLabel;
import javax.swing.JList;

/**
 * Renders {@link ColorIcon} without text within a list. Adapted from http://helpdesk.objects.com.au/tag/defaultlistcellrenderer.
 */
public class IconListRenderer extends DefaultListCellRenderer {


   private static final long serialVersionUID = 1L;
   private Map<String, ColorIcon> icons = null;

   public IconListRenderer(Map<String, ColorIcon> icons) {
      this.icons = icons;
   }

   @Override
   public Component getListCellRendererComponent(JList<?> list, Object value,
                                                 int index, boolean isSelected,
                                                 boolean cellHasFocus) {

      // Get the renderer component from parent class
      JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected,
            cellHasFocus);

      // Get icon to use for the list item value
      ColorIcon icon = icons.get(value);

      // Set icon to display for value
      label.setIcon(icon);

      return label;
   }
}