package mmcloneclasses.utils;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.ParseException;

import javax.swing.AbstractCellEditor;
import javax.swing.JComboBox;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.TableCellEditor;
import org.micromanager.utils.ReportingUtils;
import propsandcovariants.DeviceControlTableModel;
import propsandcovariants.SinglePropertyOrGroup;

/**
 * Cell editing using either JTextField or JComboBox depending on whether the
 * property enforces a set of allowed values.
 */
public class PropertyValueCellEditor extends AbstractCellEditor implements TableCellEditor {

   private static final long serialVersionUID = 1L;
   // This is the component that will handle the editing of the cell value
   JTextField text_ = new JTextField();
   JComboBox combo_ = new JComboBox();
   SliderPanel slider_ = new SliderPanel();
   SinglePropertyOrGroup item_;


   public PropertyValueCellEditor() {
      super();

      // end editing on selection change
      combo_.addActionListener(new ActionListener() {

         @Override
         public void actionPerformed(ActionEvent e) {
            fireEditingStopped();
         }
      });

      slider_.addEditActionListener(new ActionListener() {

         @Override
         public void actionPerformed(ActionEvent e) {
            fireEditingStopped();
         }
      });

      slider_.addSliderMouseListener(new MouseAdapter() {

         @Override
         public void mouseReleased(MouseEvent e) {
            fireEditingStopped();
         }
      });

      text_.addKeyListener(new KeyAdapter() {

         @Override
         public void keyPressed(KeyEvent e) {
            if (e.getKeyCode() == KeyEvent.VK_ENTER) {
               fireEditingStopped();
            }
         }
      });

      text_.addFocusListener(new FocusAdapter() {

         @Override
         public void focusLost(FocusEvent e) {
            // fireEditingStopped();
         }
      });
   }

   // This method is called when a cell value is edited by the user.
   @Override
   public Component getTableCellEditorComponent(JTable table, Object value,
           boolean isSelected, int rowIndex, int colIndex) {

      DeviceControlTableModel data = (DeviceControlTableModel) table.getModel();
      item_ = data.getPropertyItem(rowIndex);

      // Configure the component with the specified value:
      if (item_.allowed.length == 0) {
         if (item_.hasRange) {
            if (item_.isInteger()) {
               slider_.setLimits((int) item_.lowerLimit, (int) item_.upperLimit);
            } else {
               slider_.setLimits(item_.lowerLimit, item_.upperLimit);
            }
            try {
               slider_.setText((String) value);
            } catch (ParseException ex) {
               ReportingUtils.logError(ex);
            }
            return slider_;
         } else {
            text_.setText((String) value);
            return text_;
         }
      } else {
         ActionListener[] l = combo_.getActionListeners();
         for (int i = 0; i < l.length; i++) {
            combo_.removeActionListener(l[i]);
         }
         combo_.removeAllItems();
         for (int i = 0; i < item_.allowed.length; i++) {
            combo_.addItem(item_.allowed[i]);
         }
         combo_.setSelectedItem(item_.value);

         // end editing on selection change
         combo_.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
               fireEditingStopped();
            }
         });

         return combo_;
      }


   }

   // This method is called when editing is completed.
   // It must return the new value to be stored in the cell.
   @Override
   public Object getCellEditorValue() {
      if (item_.allowed.length == 0) {
         if (item_.hasRange) {
            return slider_.getText();
         } else {
            return text_.getText();
         }
      } else {
         return combo_.getSelectedItem();
      }
   }
}
