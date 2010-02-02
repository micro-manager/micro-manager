
package org.micromanager.utils;

import java.awt.Color;
import java.awt.Component;

import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;
import org.micromanager.api.MMPropertyTableModel;


public class PropertyValueCellRenderer implements TableCellRenderer {
	// This method is called each time a cell in a column
	// using this renderer needs to be rendered.
	PropertyItem item_;

    private boolean disableExcluded_;

	public PropertyValueCellRenderer(boolean disableExcluded) {
        super();
		disableExcluded_ = disableExcluded;

	}

    public PropertyValueCellRenderer() {
        this(false);
    }
    
	public Component getTableCellRendererComponent(JTable table, Object value,
			boolean isSelected, boolean hasFocus, int rowIndex, int colIndex) {

		MMPropertyTableModel data = (MMPropertyTableModel)table.getModel();
		item_ = data.getPropertyItem(rowIndex);


		JLabel lab = new JLabel();
		lab.setOpaque(true);
		lab.setHorizontalAlignment(JLabel.LEFT);


		Component comp;

        if (item_.hasRange) {
            SliderPanel slider = new SliderPanel();
            if (item_.isInteger())
                slider.setLimits((int)item_.lowerLimit, (int)item_.upperLimit);
            else {
                slider.setLimits(item_.lowerLimit, item_.upperLimit);
            }
            slider.setText((String) value);
            slider.setToolTipText(item_.value);
            comp = slider;
        } else {
            lab.setText(item_.value);
            comp = lab;
        }

        if (disableExcluded_) {
            comp.setEnabled(item_.confInclude); // Disable preset values that aren't checked.
        }
		if (item_.readOnly) {
			//comp.setForeground(Color.DARK_GRAY);
			comp.setBackground(Color.LIGHT_GRAY);
		} else {
			//comp.setForeground(Color.BLACK);
			comp.setBackground(Color.WHITE);
		}         

		return comp;
	}

	// The following methods override the defaults for performance reasons
	public void validate() {}
	public void revalidate() {}
	protected void firePropertyChange(String propertyName, Object oldValue, Object newValue) {}
	public void firePropertyChange(String propertyName, boolean oldValue, boolean newValue) {}

}