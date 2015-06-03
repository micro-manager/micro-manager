package org.micromanager.internal.dialogs;

import java.awt.Color;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashSet;

import javax.swing.AbstractCellEditor;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.TableCellEditor;

import org.micromanager.acquisition.internal.AcquisitionEngine;
import org.micromanager.internal.utils.ChannelSpec;
import org.micromanager.internal.utils.NumberUtils;
import org.micromanager.internal.utils.ReportingUtils;

/**
 * Cell editing using either JTextField or JComboBox depending on whether the
 * property enforces a set of allowed values.
 */
public class ChannelCellEditor extends AbstractCellEditor implements TableCellEditor {

   private static final long serialVersionUID = -8374637422965302637L;
   JTextField text_ = new JTextField();
   JComboBox combo_ = new JComboBox();
   JCheckBox checkBox_ = new JCheckBox();
   JLabel colorLabel_ = new JLabel();
   int editCol_ = -1;
   int editRow_ = -1;
   ChannelSpec channel_ = null;

   private AcquisitionEngine acqEng_;

   public ChannelCellEditor(AcquisitionEngine engine) {
      acqEng_ = engine;
   }

   // This method is called when a cell value is edited by the user.
   @Override
   public Component getTableCellEditorComponent(JTable table, Object value,
           boolean isSelected, int rowIndex, int colIndex) {

      if (isSelected) {
         // cell (and perhaps other cells) are selected
      }

      ChannelTableModel model = (ChannelTableModel) table.getModel();
      ArrayList<ChannelSpec> channels = model.getChannels();
      final ChannelSpec channel = channels.get(rowIndex);
      channel_ = channel;

      colIndex = table.convertColumnIndexToModel(colIndex);

      // Configure the component with the specified value
      editRow_ = rowIndex;
      editCol_ = colIndex;
      if (colIndex == 0) {
         checkBox_.setSelected((Boolean) value);
         return checkBox_;
      } else if (colIndex == 2 || colIndex == 3) {
         // exposure and z offset
         text_.setText(NumberUtils.doubleToDisplayString((Double)value));
         return text_;
      } else if (colIndex == 4) {
         checkBox_.setSelected((Boolean) value);
         return checkBox_;
      } else if (colIndex == 5) {
         // skip
         text_.setText(NumberUtils.intToDisplayString((Integer) value));
         return text_;
      } else if (colIndex == 1) {
         // channel
         combo_.removeAllItems();

         // remove old listeners
         ActionListener[] listeners = combo_.getActionListeners();
         for (int i = 0; i < listeners.length; i++) {
            combo_.removeActionListener(listeners[i]);
         }
         combo_.removeAllItems();

         // Only allow channels that aren't already selected in a different
         // row.
         HashSet<String> usedChannels = new HashSet<String>();
         for (int i = 0; i < model.getChannels().size(); ++i) {
            if (i != editRow_) {
               usedChannels.add((String) model.getValueAt(i, 1));
            }
         }
         String configs[] = model.getAvailableChannels();
         for (int i = 0; i < configs.length; i++) {
            if (!usedChannels.contains(configs[i])) {
               combo_.addItem(configs[i]);
            }
         }
         combo_.setSelectedItem(channel.config);
         
         // end editing on selection change
         combo_.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
               channel_.color = new Color(AcqControlDlg.getChannelColor(
                     acqEng_.getChannelGroup(),
                     (String) combo_.getSelectedItem(), Color.white.getRGB()));
               channel_.exposure = AcqControlDlg.getChannelExposure(
                  acqEng_.getChannelGroup(),
                  (String) combo_.getSelectedItem(), 10.0);
               fireEditingStopped();
            }
         });

         // Return the configured component
         return combo_;
      } else {
         // ColorEditor takes care of this
         return colorLabel_;
      }
   }

   /** 
    * This method is called when editing is completed.
    * It must return the new value to be stored in the cell.
    */
   @Override
   public Object getCellEditorValue() {
      // TODO: if content of column does not match type we get an exception
      try {
         if (editCol_ == 0) {
            return checkBox_.isSelected();
         } else if (editCol_ == 1) {
            // As a side effect, change to the color and exposure of the new channel
            channel_.color = new Color(AcqControlDlg.getChannelColor(
                     acqEng_.getChannelGroup(),
                     (String) combo_.getSelectedItem(), Color.white.getRGB()));
            channel_.exposure = AcqControlDlg.getChannelExposure(
                  acqEng_.getChannelGroup(), channel_.config, 10.0);
            return combo_.getSelectedItem();
         } else if (editCol_ == 2 || editCol_ == 3) {
            return new Double(NumberUtils.displayStringToDouble(text_.getText()));
         } else if (editCol_ == 4) {
            return checkBox_.isSelected();
         } else if (editCol_ == 5) {
            return new Integer(NumberUtils.displayStringToInt(text_.getText()));
         } else if (editCol_ == 6) {
            Color c = colorLabel_.getBackground();
            return c;
         } else {
            String err = "Internal error: unknown column";
            return err;
         }
      } catch (ParseException p) {
         ReportingUtils.showError(p);
      }
      String err = "Internal error: unknown column";
      return err;
   }
}
