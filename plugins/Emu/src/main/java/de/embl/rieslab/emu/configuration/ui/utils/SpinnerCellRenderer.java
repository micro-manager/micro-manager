package de.embl.rieslab.emu.configuration.ui.utils;

import java.awt.Component;

import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.SpinnerModel;
import javax.swing.table.TableCellRenderer;

/**
 * TableCellRenderer for JSpinner.
 *
 * @author Joran Deschamps
 */
public class SpinnerCellRenderer extends JSpinner implements TableCellRenderer {

    private static final long serialVersionUID = 1L;

    public SpinnerCellRenderer() {
        setOpaque(true);
    }

    public Component getTableCellRendererComponent(JTable table,
                                                   Object value, boolean isSelected, boolean hasFocus, int row,
                                                   int column) {
        setModel((SpinnerModel) value);

        return this;
    }
}