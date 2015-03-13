package propsandcovariants;

import propsandcovariants.SinglePropertyOrGroup;
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
import mmcloneclasses.utils.SliderPanel;
import org.micromanager.utils.ReportingUtils;
import propsandcovariants.DeviceControlTableModel;

/**
 * Cell editing using either JTextField or JComboBox depending on whether the
 * property enforces a set of allowed values.
 */
public class CovariantValueCellEditor extends AbstractCellEditor implements TableCellEditor {

   private static final long serialVersionUID = 1L;
   // This is the component that will handle the editing of the cell value
   JTextField text_ = new JTextField();
   JComboBox combo_ = new JComboBox();
   SliderPanel slider_ = new SliderPanel();
   Covariant item_;

   public CovariantValueCellEditor() {
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

      CovariantPairValuesTableModel data = (CovariantPairValuesTableModel) table.getModel();
      item_ = colIndex == 0 ?  data.getPairing().getIndependentCovariant() : data.getPairing().getDependentCovariant();

      // Configure the component with the specified value:
      if (!item_.isDiscrete()) {
//         if (item_.hasLimits()) {
//            if (item_.getType() == CovariantType.INT) {
//               slider_.setLimits( item_.getLowerLimit().intValue(), item_.getUpperLimit().intValue());
//            } else {
//               slider_.setLimits( item_.getLowerLimit().doubleValue(), item_.getUpperLimit().doubleValue());
//            }
//            try {
//               slider_.setText(((CovariantValue) value).toString());
//            } catch (ParseException ex) {
//               ReportingUtils.logError(ex);
//            }
//            return slider_;
//         } else {
            text_.setText(((CovariantValue) value).toString());
            return text_;
//         }
      } else {
         ActionListener[] l = combo_.getActionListeners();
         for (int i = 0; i < l.length; i++) {
            combo_.removeActionListener(l[i]);
         }
         combo_.removeAllItems();
         for (int i = 0; i < item_.getAllowedValues().length; i++) {
            combo_.addItem(item_.getAllowedValues()[i]);
         }
         combo_.setSelectedItem(data.getPairing().getValue(colIndex, rowIndex));

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
      if (!item_.isDiscrete()) {
//         if (item_.hasLimits()) {
//            return slider_.getText();
//         } else {
            return text_.getText();
//         }
      } else {
         return combo_.getSelectedItem();
      }
   }
}
