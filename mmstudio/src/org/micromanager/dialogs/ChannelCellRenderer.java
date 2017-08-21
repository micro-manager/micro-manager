package org.micromanager.dialogs;

import java.awt.Component;
import java.util.ArrayList;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;

import org.micromanager.acquisition.AcquisitionEngine;
import org.micromanager.utils.ChannelSpec;
import org.micromanager.utils.NumberUtils;

/**
 * Renderer class for the channel table.
 */
public class ChannelCellRenderer extends JLabel implements TableCellRenderer {

   private static final long serialVersionUID = -4328340719459382679L;
   private AcquisitionEngine acqEng_;

   // This method is called each time a cell in a column
   // using this renderer needs to be rendered.
   public ChannelCellRenderer(AcquisitionEngine acqEng) {
      super();
      acqEng_ = acqEng;
   }

   @Override
   public Component getTableCellRendererComponent(JTable table, Object value,
           boolean isSelected, boolean hasFocus, int rowIndex, int colIndex) {

      // https://stackoverflow.com/a/3055930
      if (value == null) {
         return null;
      }

      ChannelTableModel model = (ChannelTableModel) table.getModel();
      ArrayList<ChannelSpec> channels = model.getChannels();
      ChannelSpec channel = channels.get(rowIndex);
      this.setEnabled(table.isEnabled());

      colIndex = table.convertColumnIndexToModel(colIndex);

      setOpaque(false);
      if (colIndex == 0) {
         JCheckBox check = new JCheckBox("", channel.useChannel);
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
         setText(channel.config);
      } else if (colIndex == 2) {
         setText(NumberUtils.doubleToDisplayString(channel.exposure));
      } else if (colIndex == 3) {
         setText(NumberUtils.doubleToDisplayString(channel.zOffset));
      } else if (colIndex == 4) {
         JCheckBox check = new JCheckBox("", channel.doZStack);
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
         setText(Integer.toString(channel.skipFactorFrame));
      } else if (colIndex == 6) {
         setText("");
         setBackground(channel.color);
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
