package de.embl.rieslab.emu.configuration.ui.tables;

import de.embl.rieslab.emu.configuration.ui.HelpWindow;
import de.embl.rieslab.emu.configuration.ui.utils.ColorIcon;
import de.embl.rieslab.emu.configuration.ui.utils.IconListRenderer;
import de.embl.rieslab.emu.configuration.ui.utils.IconTableCellRenderer;
import de.embl.rieslab.emu.ui.uiparameters.BoolUIParameter;
import de.embl.rieslab.emu.ui.uiparameters.ComboUIParameter;
import de.embl.rieslab.emu.ui.uiparameters.UIParameter;
import de.embl.rieslab.emu.ui.uiparameters.UIPropertyParameter;
import de.embl.rieslab.emu.ui.uiproperties.UIProperty;
import de.embl.rieslab.emu.utils.ColorRepository;
import de.embl.rieslab.emu.utils.EmuUtils;
import java.awt.Component;
import java.awt.Font;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import javax.swing.DefaultCellEditor;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableModel;

/**
 * JPanel displaying a table allowing the user to set the values of the UIParameters.
 *
 * @author Joran Deschamps
 */
public class ParametersTable extends JPanel {

   private static final long serialVersionUID = 1L;

   private JTable table;
   private final JComboBox<String> color;

   @SuppressWarnings("rawtypes")
   private final Map<String, UIParameter> uiparameterSet_;
   private final Map<String, UIProperty> uipropertySet_;
   private String[] uiparamkeys_;
   private final HelpWindow help_;

   /**
    * Constructor called when no configuration exists. All the parameter values are set to default values.
    *
    * @param uiparameterSet Map of the UIParameters, indexed by their name
    * @param uipropertySet  Map of the UIProperties, indexed by their name
    * @param help           Help window
    */
   @SuppressWarnings("rawtypes")
   public ParametersTable(Map<String, UIParameter> uiparameterSet,
                          Map<String, UIProperty> uipropertySet, HelpWindow help) {

      uiparameterSet_ = uiparameterSet;
      uipropertySet_ = uipropertySet;
      help_ = help;

      // Color JComboBox: available colors within EMU
      Map<String, ColorIcon> icons = new HashMap<String, ColorIcon>();
      color = new JComboBox<String>();
      String[] colors = ColorRepository.getColors();
      for (int k = 0; k < colors.length; k++) {
         color.addItem(colors[k]);
         icons.put(colors[k], new ColorIcon(ColorRepository.getColor(colors[k])));
      }
      color.setRenderer(new IconListRenderer(icons));

      // Extracts UIParameters' name
      uiparamkeys_ = new String[uiparameterSet_.size()];
      String[] temp = new String[uiparameterSet_.size()];
      uiparamkeys_ = uiparameterSet_.keySet().toArray(temp);
      Arrays.sort(uiparamkeys_);

      // Defines table
      DefaultTableModel model = new DefaultTableModel(new Object[] {"UI parameter", "Value"}, 0);
      for (int i = 0; i < uiparamkeys_.length; i++) {
         if (uiparameterSet_.get(
               uiparamkeys_[i]) instanceof BoolUIParameter) { // if bool, just passes the value
            model.addRow(
                  new Object[] {uiparamkeys_[i], uiparameterSet_.get(uiparamkeys_[i]).getValue()});
         } else { // else, converts the value to a String
            model.addRow(new Object[] {uiparamkeys_[i],
                  uiparameterSet_.get(uiparamkeys_[i]).getStringValue()});
         }
      }

      createTable(model);

      JScrollPane sc = new JScrollPane(table);
      //sc.setPreferredSize(new Dimension(280,590));
      this.add(sc);
   }

   /**
    * Constructor called when a configuration exists.
    *
    * @param uiparameterSet Map of the UI parameters, indexed by their name.
    * @param paramValues    Map of the UIParameter names (keys) and their value (values) from the configuration.
    * @param uipropertySet  Map of the UIProperties, indexed by their name.
    * @param help           Help window
    */
   @SuppressWarnings("rawtypes")
   public ParametersTable(Map<String, UIParameter> uiparameterSet, Map<String, String> paramValues,
                          Map<String, UIProperty> uipropertySet, HelpWindow help) {
      help_ = help;
      uiparameterSet_ = uiparameterSet;
      uipropertySet_ = uipropertySet; // they are used to deal with UIPropertyParameters

      // Color combobox
      Map<String, ColorIcon> icons = new HashMap<String, ColorIcon>();
      color = new JComboBox<String>();
      String[] colors = ColorRepository.getColors();
      for (int k = 0; k < colors.length; k++) {
         color.addItem(colors[k]);
         icons.put(colors[k], new ColorIcon(ColorRepository.getColor(colors[k])));
      }
      color.setRenderer(new IconListRenderer(icons));

      // Extract uiparameters names
      uiparamkeys_ = new String[uiparameterSet_.size()];
      String[] temp = new String[uiparameterSet_.size()];
      uiparamkeys_ = uiparameterSet_.keySet().toArray(temp);
      Arrays.sort(uiparamkeys_);

      // Define table
      DefaultTableModel model = new DefaultTableModel(new Object[] {"UI parameter", "Value"}, 0);
      for (int i = 0; i < uiparamkeys_.length; i++) {
         if (paramValues.containsKey(
               uiparamkeys_[i])) { // if the parameter is found in the configuration, then put its value
            if (uiparameterSet_.get(uiparamkeys_[i]) instanceof BoolUIParameter) {
               model.addRow(new Object[] {uiparamkeys_[i],
                     EmuUtils.convertStringToBool(paramValues.get(uiparamkeys_[i]))});
            } else {
               model.addRow(new Object[] {uiparamkeys_[i], paramValues.get(uiparamkeys_[i])});
            }
         } else { // else put default value
            if (uiparameterSet_.get(uiparamkeys_[i]) instanceof BoolUIParameter) {
               model.addRow(new Object[] {uiparamkeys_[i],
                     uiparameterSet_.get(uiparamkeys_[i]).getValue()});
            } else {
               model.addRow(new Object[] {uiparamkeys_[i],
                     uiparameterSet_.get(uiparamkeys_[i]).getStringValue()});
            }
         }
      }

      createTable(model);

      JScrollPane sc = new JScrollPane(table);
      //sc.setPreferredSize(new Dimension(280,590));

      this.add(sc);
   }

   private void createTable(DefaultTableModel model) {

      table = new JTable(model) {

         private static final long serialVersionUID = 1L;

         @Override
         public TableCellRenderer getCellRenderer(int row, int column) {
            switch (column) {
               case 0:
                  return new BoldTableCellRenderer();
               case 1:
                  String s = (String) table.getValueAt(row, 0);
                  if (uiparameterSet_.get(s).getType()
                        .equals(UIParameter.UIParameterType.COLOR)) { // if Color parameter
                     return new IconTableCellRenderer();
                  } else if (uiparameterSet_.get(s).getType()
                        .equals(UIParameter.UIParameterType.BOOL)) { // if checkbox
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
                  if (uiparameterSet_.get(s).getType().equals(UIParameter.UIParameterType.COLOR)) {
                     return new DefaultCellEditor(color);
                  } else if (uiparameterSet_.get(s) instanceof ComboUIParameter) {
                     return new DefaultCellEditor(new JComboBox<String>(
                           ((ComboUIParameter) uiparameterSet_.get(s)).getComboValues()));
                  } else if (uiparameterSet_.get(s).getType()
                        .equals(UIParameter.UIParameterType.BOOL)) {
                     return super.getDefaultEditor(Boolean.class);
                  } else if (uiparameterSet_.get(s).getType()
                        .equals(UIParameter.UIParameterType.UIPROPERTY)) {
                     return new DefaultCellEditor(new JComboBox<String>(
                           getAvailableProperties((UIPropertyParameter) uiparameterSet_.get(s))));
                  } else {
                     return new DefaultCellEditor(new JTextField());
                  }
               default:
                  return super.getCellEditor(row, column);
            }
         }

         @Override
         public boolean isCellEditable(int row, int col) { // only second column is editable
            return col >= 1;
         }
      };
      table.setAutoCreateRowSorter(false);
      table.setRowHeight(23);

      table.addMouseListener(new java.awt.event.MouseAdapter() {
         @Override
         public void mouseClicked(java.awt.event.MouseEvent evt) {
            int row = table.rowAtPoint(evt.getPoint());
            updateHelper(row);
         }
      });
   }

   private String[] getAvailableProperties(UIPropertyParameter param) {
      ArrayList<String> props = new ArrayList<String>();

      Iterator<String> it = uipropertySet_.keySet().iterator();
      String s;
      while (it.hasNext()) {
         s = it.next();
         if (uipropertySet_.get(s).getFlag().compareTo(param.getFlag()) == 0) {
            props.add(s);
         }
      }
      String[] stot = props.toArray(new String[0]);
      Arrays.sort(stot);

      String[] sfin = new String[stot.length + 1];
      sfin[0] = UIPropertyParameter.NO_PROPERTY;
      for (int i = 0; i < stot.length; i++) {
         sfin[i + 1] = stot[i];
      }

      return sfin;
   }


   /**
    * Shows the help window and updates its content with the description of the parameter currently selected.
    *
    * @param b True if the window is to be displayed, false otherwise.
    */
   public void showHelp(boolean b) {
      help_.showHelp(b);
      updateHelper(table.getSelectedRow());
   }

   private void updateHelper(int row) {
      String s = (String) table.getValueAt(row, 0);
      help_.update(s, uiparameterSet_.get(s).getDescription());
   }

   /**
    * Returns the map of the UIParameter names (keys) and their values (values).
    *
    * @return HashMap of the UIParameters
    */
   public HashMap<String, String> getSettings() {
      HashMap<String, String> settings = new HashMap<String, String>();

      TableModel model = table.getModel();
      int nrow = model.getRowCount();

      for (int i = 0; i < nrow; i++) {
         if (model.getValueAt(i, 1) instanceof Boolean) {
            settings.put((String) model.getValueAt(i, 0),
                  Boolean.toString((Boolean) model.getValueAt(i, 1)));
         } else {
            settings.put((String) model.getValueAt(i, 0), (String) model.getValueAt(i, 1));
         }
      }

      return settings;
   }

   /**
    * Renders cell text with a bold font. Adapted from: https://stackoverflow.com/questions/22325138/cellrenderer-making-text-bold
    */
   class BoldTableCellRenderer extends DefaultTableCellRenderer {

      private static final long serialVersionUID = 1L;

      public Component getTableCellRendererComponent(JTable table,
                                                     Object value, boolean isSelected,
                                                     boolean hasFocus,
                                                     int row, int column) {
         Component compo = super.getTableCellRendererComponent(table,
               value, isSelected, hasFocus, row, column);
         if (column == 0) {
            compo.setFont(compo.getFont().deriveFont(Font.BOLD));
         } else {
            compo.setFont(compo.getFont().deriveFont(Font.PLAIN));
         }

         return compo;
      }
   }

}