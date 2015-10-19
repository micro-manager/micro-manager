package mmcloneclasses.utils;

import java.awt.Color;
import java.awt.Component;
import java.text.ParseException;

import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;
import misc.Log;
import propsandcovariants.DeviceControlTableModel;
import propsandcovariants.SinglePropertyOrGroup;

public class PropertyValueCellRenderer implements TableCellRenderer {
   // This method is called each time a cell in a column
   // using this renderer needs to be rendered.

   SinglePropertyOrGroup item_;
   JLabel lab_ = new JLabel();
   private boolean disable_;

   public PropertyValueCellRenderer(boolean disable) {
      super();
      disable_ = disable;
   }

   public PropertyValueCellRenderer() {
      this(false);
   }

   @Override
   public Component getTableCellRendererComponent(JTable table, Object value,
           boolean isSelected, boolean hasFocus, int rowIndex, int colIndex) {

      DeviceControlTableModel data = (DeviceControlTableModel) table.getModel();
      item_ = data.getPropertyItem(rowIndex);

      lab_.setOpaque(true);
      lab_.setHorizontalAlignment(JLabel.LEFT);

      Component comp;

      if (item_.hasRange) {
         SliderPanel slider = new SliderPanel();
         if (item_.isInteger()) {
            slider.setLimits((int) item_.lowerLimit, (int) item_.upperLimit);
         } else {
            slider.setLimits(item_.lowerLimit, item_.upperLimit);
         }
         try {
            slider.setText((String) value);
         } catch (ParseException ex) {
            Log.log(ex);
         }
         slider.setToolTipText(item_.value);
         comp = slider;
      } else {
         lab_.setText(item_.value);
         comp = lab_;
      }

      if (disable_) {
         comp.setEnabled(false); // Disable preset values that aren't checked.
      }
      
      if (item_.readOnly) {
         comp.setBackground(Color.LIGHT_GRAY);
      } else {
         comp.setBackground(Color.WHITE);
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
