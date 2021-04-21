package de.embl.rieslab.emu.configuration.ui.tables;

import java.awt.Component;
import java.awt.Font;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import javax.swing.DefaultCellEditor;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableModel;

import de.embl.rieslab.emu.configuration.ui.HelpWindow;
import de.embl.rieslab.emu.utils.settings.BoolSetting;
import de.embl.rieslab.emu.utils.settings.Setting;

/**
 * A JPanel containing a JTable tailored for Settings. The first column contains the names of the
 * settings, is non editable and displayed in bold text. The second column contains the values. The
 * latter's cells are rendered with a JCheckbox in case of a BoolSetting and normal text if the
 * setting is a String- or IntSetting. This class is used in the settings menu of EMU.
 *
 * @author Joran Deschamps
 */
public class SettingsTable extends JPanel {

  private static final long serialVersionUID = 1L;

  private JTable table;

  @SuppressWarnings("rawtypes")
  private Map<String, Setting> settings_;

  private String[] namesettings_;
  private HelpWindow help_;
  private boolean
      valuesChanged_; // used to signal a necessary update of property and parameter tabs

  @SuppressWarnings("rawtypes")
  public SettingsTable(HashMap<String, Setting> hashMap, HelpWindow help) {
    if (hashMap == null || help == null) {
      throw new NullPointerException();
    }

    settings_ = hashMap;
    help_ = help;
    valuesChanged_ = false;

    // Extract global settings names
    namesettings_ = settings_.keySet().toArray(new String[0]);
    Arrays.sort(namesettings_);

    // Define table
    DefaultTableModel model = new DefaultTableModel(new Object[] {"Setting", "Value"}, 0);
    for (int i = 0; i < namesettings_.length; i++) {
      model.addRow(new Object[] {namesettings_[i], settings_.get(namesettings_[i]).getValue()});
    }

    createTable(model);

    JScrollPane sc = new JScrollPane(table);
    this.add(sc);
  }

  @SuppressWarnings("rawtypes")
  public SettingsTable(
      HashMap<String, Setting> referenceMap, TreeMap<String, String> valuesMap, HelpWindow help) {
    if (referenceMap == null || valuesMap == null || help == null) {
      throw new NullPointerException();
    }

    // fills the reference map with values from the valuesMap
    settings_ = referenceMap;
    Iterator<String> it = valuesMap.keySet().iterator();
    while (it.hasNext()) {
      String s = it.next();

      // if the setting has the expected type, then replace in the current settings
      if (settings_.containsKey(s) && settings_.get(s).isValueCompatible(valuesMap.get(s)))
        settings_.get(s).setStringValue(valuesMap.get(s));
    }

    help_ = help;

    // Extract global settings names
    namesettings_ = settings_.keySet().toArray(new String[0]);
    Arrays.sort(namesettings_);

    // Define table
    DefaultTableModel model = new DefaultTableModel(new Object[] {"Setting", "Value"}, 0);
    for (int i = 0; i < namesettings_.length; i++) {
      model.addRow(new Object[] {namesettings_[i], settings_.get(namesettings_[i]).getValue()});
    }

    createTable(model);

    JScrollPane sc = new JScrollPane(table);
    this.add(sc);
  }

  private void createTable(DefaultTableModel model) {

    table =
        new JTable(model) {

          private static final long serialVersionUID = 1L;

          @Override
          public TableCellRenderer getCellRenderer(int row, int column) {
            switch (column) {
              case 0:
                return new BoldTableCellRenderer();
              case 1:
                String s = (String) table.getValueAt(row, 0);
                if (settings_.get(s) instanceof BoolSetting) { // if bool global settings
                  return super.getDefaultRenderer(Boolean.class);
                } else {
                  return new DefaultTableCellRenderer();
                }
              default:
                return super.getCellRenderer(row, column);
            }
          }

          @Override
          public TableCellEditor getCellEditor(int row, int column) {
            switch (column) {
              case 0:
                return super.getCellEditor(row, column);
              case 1:
                String s = (String) table.getValueAt(row, 0);
                if (settings_.get(s) instanceof BoolSetting) {
                  return super.getDefaultEditor(Boolean.class);
                } else {
                  return new DefaultCellEditor(new JTextField());
                }
              default:
                return super.getCellEditor(row, column);
            }
          }

          @Override
          public boolean isCellEditable(int row, int col) { // only second column is editable
            if (col < 1) {
              return false;
            } else {
              return true;
            }
          }
        };
    table.setAutoCreateRowSorter(false);
    table.setRowHeight(23);

    table.addMouseListener(
        new java.awt.event.MouseAdapter() {
          @Override
          public void mouseClicked(java.awt.event.MouseEvent evt) {
            int row = table.rowAtPoint(evt.getPoint());
            int col = table.columnAtPoint(evt.getPoint());
            if (col == 0) {
              updateHelper(row);
            } else {
              if (!valuesChanged_) {
                valuesChanged_ = true;
              }
            }
          }
        });
  }

  /**
   * Shows the help window and updates its content with the description of the parameter currently
   * selected.
   *
   * @param b True if the window is to be displayed, false if it needs to be hidden.
   */
  public void showHelp(boolean b) {
    help_.showHelp(b);
    updateHelper(table.getSelectedRow());
  }

  private void updateHelper(int row) {
    String s = (String) table.getValueAt(row, 0);
    help_.update(s, settings_.get(s).getDescription());
  }

  /**
   * Returns the map of the GlobalSettings names (keys) and their values (values).
   *
   * @return HashMap containing the values of the GlobalSettings indexed by their name.
   */
  public HashMap<String, String> getSettings() {
    HashMap<String, String> settings = new HashMap<String, String>();

    TableModel model = table.getModel();
    int nrow = model.getRowCount();

    for (int i = 0; i < nrow; i++) {
      if (model.getValueAt(i, 1) instanceof Boolean) {
        settings.put(
            (String) model.getValueAt(i, 0), Boolean.toString((Boolean) model.getValueAt(i, 1)));
      } else if (model.getValueAt(i, 1) instanceof Integer) {
        settings.put(
            (String) model.getValueAt(i, 0), Integer.toString((Integer) model.getValueAt(i, 1)));
      } else if (model.getValueAt(i, 1) instanceof Double) {
        settings.put(
            (String) model.getValueAt(i, 0), Double.toString((Double) model.getValueAt(i, 1)));
      } else {
        settings.put((String) model.getValueAt(i, 0), (String) model.getValueAt(i, 1));
      }
    }

    return settings;
  }

  /**
   * Returns true if the table has changed.
   *
   * @return True if they have, false otherwise.
   */
  public boolean hasChanged() {
    return valuesChanged_;
  }

  /** Sets the current table state to unchanged. */
  public void registerChange() {
    valuesChanged_ = false;
  }

  /*
   * Renders cells' text with a bold font.
   * Adapted from https://stackoverflow.com/questions/22325138/cellrenderer-making-text-bold.
   */
  private class BoldTableCellRenderer extends DefaultTableCellRenderer {

    private static final long serialVersionUID = 1L;

    public Component getTableCellRendererComponent(
        JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      Component compo =
          super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      if (column == 0) {
        compo.setFont(compo.getFont().deriveFont(Font.BOLD));
      } else {
        compo.setFont(compo.getFont().deriveFont(Font.PLAIN));
      }

      return compo;
    }
  }
}
