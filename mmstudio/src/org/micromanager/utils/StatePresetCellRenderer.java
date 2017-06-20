
package org.micromanager.utils;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.text.ParseException;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;
import org.micromanager.ConfigGroupPad;

/**
 *
 * @author arthur
 */
/**
 * Rendering element for the property table.
 *
 */
public class StatePresetCellRenderer implements TableCellRenderer {

    private static final long serialVersionUID = 1L;
    // This method is called each time a cell in a column
    // using this renderer needs to be rendered.
    StateItem stateItem_;

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value,
            boolean isSelected, boolean hasFocus, int rowIndex, int colIndex) {

        // https://stackoverflow.com/a/3055930
        if (value == null) {
           return null;
        }

        ConfigGroupPad.StateTableData data = (ConfigGroupPad.StateTableData) table.getModel();
        stateItem_ = data.getPropertyItem(rowIndex);

        Component comp;

        // Configure the component with the specified value

        if (stateItem_.hasLimits) {
            SliderPanel slider = new SliderPanel();
            if (stateItem_.isInteger()) {
                slider.setLimits((int) stateItem_.lowerLimit, (int) stateItem_.upperLimit);
            } else {
                slider.setLimits(stateItem_.lowerLimit, stateItem_.upperLimit);
            }
           try {
              slider.setText((String) value);
           } catch (ParseException ex) {
              ReportingUtils.logError(ex);
           }
            slider.setToolTipText((String) value);
            comp = slider;

        } else {
            JLabel label = new JLabel();
            label.setOpaque(true);
            label.setFont(new Font("Arial", Font.PLAIN, 10));
            label.setText(stateItem_.config.toString());
            label.setToolTipText(stateItem_.descr);
            label.setHorizontalAlignment(JLabel.LEFT);
            comp = label;
        }

      
        if (isSelected) {
            comp.setBackground(Color.LIGHT_GRAY);
        } else {
            comp.setBackground(Color.WHITE);
        }

        return comp;
    }
      // The following methods override the defaults for performance reasons
      public void validate(){}
      public void revalidate(){}
      protected void firePropertyChange(String propertyName, Object oldValue, Object newValue) {}
      public void firePropertyChange(String propertyName, boolean oldValue, boolean newValue) {}

   }
