package org.micromanager.internal.dialogs;

import java.awt.Component;
import java.util.ArrayList;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;
import org.micromanager.acquisition.ChannelSpec;
import org.micromanager.acquisition.internal.AcquisitionEngine;
import org.micromanager.internal.utils.NumberUtils;

/**
 * Renderer class for the channel table.
 */
public final class ChannelCellRenderer extends JLabel implements TableCellRenderer {

   private static final long serialVersionUID = -4328340719459382679L;
   private final AcquisitionEngine acqEng_;

   // This method is called each time a cell in a column
   // using this renderer needs to be rendered.
   public ChannelCellRenderer(AcquisitionEngine acqEng) {
      super();
      acqEng_ = acqEng;
   }

   @Override
   public Component getTableCellRendererComponent(JTable table, Object value,
                                                  boolean isSelected, boolean hasFocus,
                                                  int rowIndex, int colIndex) {

      this.setEnabled(table.isEnabled());
      colIndex = table.convertColumnIndexToModel(colIndex);
      setOpaque(false);

      final ChannelTableModel model = (ChannelTableModel) table.getModel();
      final ArrayList<ChannelSpec> channels = model.getChannels();
      final ChannelSpec channel = channels.get(rowIndex);
      if (colIndex == 0) {
         JCheckBox check = new JCheckBox("", channel.useChannel());
         check.setEnabled(table.isEnabled());
         check.setOpaque(true);
         if (isSelected) {
            check.setBackground(table.getSelectionBackground());
            check.setOpaque(true);
         } else {
            check.setOpaque(false);
            check.setBackground(table.getBackground());
         }
         return check;
      } else if (colIndex == 1) {
         setText(channel.config());
      } else if (colIndex == 2) {
         setText(NumberUtils.doubleToDisplayString(channel.exposure()));
      } else if (colIndex == 3) {
         setText(NumberUtils.doubleToDisplayString(channel.zOffset()));
      } else if (colIndex == 4) {
         JCheckBox check = new JCheckBox("", channel.doZStack());
         check.setEnabled(acqEng_.isZSliceSettingEnabled() && table.isEnabled());
         if (isSelected) {
            check.setBackground(table.getSelectionBackground());
            check.setOpaque(true);
         } else {
            check.setOpaque(false);
            check.setBackground(table.getBackground());
         }
         return check;
      } else if (colIndex == 5) {
         setText(Integer.toString(channel.skipFactorFrame()));
      } else if (colIndex == 6) {
         setText("");
         setBackground(channel.color());
         setOpaque(true);
      }

      if (isSelected) {
         setBackground(table.getSelectionBackground());
         setOpaque(true);
      } else {
         setOpaque(false);
         setBackground(table.getBackground());
      }

      // Since the renderer is a component, return itself
      return this;
   }

   // The following methods override the defaults for performance reasons
   @Override
   public void validate() {
   }

   @Override
   public void revalidate() {
   }

   @Override
   protected void firePropertyChange(String propertyName, Object oldValue, Object newValue) {
   }

   @Override
   public void firePropertyChange(String propertyName, boolean oldValue, boolean newValue) {
   }
}
