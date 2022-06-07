package de.embl.rieslab.emu.configuration.ui.tables;

import java.awt.Color;
import java.awt.Component;
import java.util.ArrayList;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableModel;

import de.embl.rieslab.emu.configuration.data.GlobalConfiguration;
import de.embl.rieslab.emu.configuration.data.PluginConfigurationID;
import de.embl.rieslab.emu.controller.utils.SystemDialogs;

public class ConfigurationTable extends JPanel {

    private static final long serialVersionUID = 1L;
    private JTable table;
    private String currentConfiguration;
    private int currentConfigurationRow;

    public ConfigurationTable(GlobalConfiguration conf) {
        currentConfiguration = conf.getCurrentConfigurationName();

        // Defines table
        DefaultTableModel model = new DefaultTableModel(new Object[]{"Plugin", "Configuration name"}, 0);

        currentConfigurationRow = -2;
        for (int i = 0; i < conf.getPluginConfigurations().size(); i++) {
            if (conf.getPluginConfigurations().get(i).getConfigurationName().equals(currentConfiguration)) {
                currentConfigurationRow = i;
            }
            model.addRow(new Object[]{conf.getPluginConfigurations().get(i).getPluginName(), conf.getPluginConfigurations().get(i).getConfigurationName()});
        }

        createTable(model);

        JScrollPane sc = new JScrollPane(table);
        this.add(sc);
    }

    private void createTable(DefaultTableModel model) {
        table = new JTable(model) {

            private static final long serialVersionUID = 1L;

            @Override
            public TableCellRenderer getCellRenderer(int row, int column) {
                String s = (String) table.getValueAt(row, 1);
                if (s.equals(currentConfiguration)) {
                    return new ColorCellRenderer(Color.black);
                } else {
                    return super.getCellRenderer(row, column);
                }
            }

            @Override
            public TableCellEditor getCellEditor(int row, int column) {
                return super.getCellEditor(row, column);
            }

            @Override
            public boolean isCellEditable(int row, int col) { // only first column is editable
                if (col > 0) {
                    return false;
                } else {
                    return true;
                }
            }
        };
    }

    public ArrayList<PluginConfigurationID> getConfigurations() {
        ArrayList<PluginConfigurationID> config = new ArrayList<PluginConfigurationID>();

        TableModel model = table.getModel();
        int nrow = model.getRowCount();

        for (int i = 0; i < nrow; i++) {
            config.add(new PluginConfigurationID((String) model.getValueAt(i, 1), (String) model.getValueAt(i, 0)));
        }

        return config;
    }

    public void deleteSelectedRow() {
        int row = table.getSelectedRow();
        if (row != -1 && row != currentConfigurationRow) {
            ((DefaultTableModel) table.getModel()).removeRow(row);

            if (row < currentConfigurationRow) {
                currentConfigurationRow--;
            }
        } else if (row == currentConfigurationRow) {
            SystemDialogs.showCannotDeleteCurrentConfiguration();
        }
    }

    private class ColorCellRenderer extends DefaultTableCellRenderer {

        private static final long serialVersionUID = 1L;
        private Color color;

        public ColorCellRenderer(Color c) {
            color = c;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
                                                       int row, int column) {

            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            c.setBackground(color);
            c.setForeground(Color.white);
            return c;
        }
    }
}
