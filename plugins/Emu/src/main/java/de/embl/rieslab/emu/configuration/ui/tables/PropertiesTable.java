package de.embl.rieslab.emu.configuration.ui.tables;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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

import de.embl.rieslab.emu.configuration.data.GlobalConfiguration;
import de.embl.rieslab.emu.configuration.ui.ConfigurationWizardUI;
import de.embl.rieslab.emu.configuration.ui.HelpWindow;
import de.embl.rieslab.emu.micromanager.mmproperties.MMPropertiesRegistry;
import de.embl.rieslab.emu.micromanager.mmproperties.MMProperty;
import de.embl.rieslab.emu.ui.uiproperties.MultiStateUIProperty;
import de.embl.rieslab.emu.ui.uiproperties.RescaledUIProperty;
import de.embl.rieslab.emu.ui.uiproperties.SingleStateUIProperty;
import de.embl.rieslab.emu.ui.uiproperties.TwoStateUIProperty;
import de.embl.rieslab.emu.ui.uiproperties.UIProperty;

/**
 * JPanel displaying a table allowing the user to allocate device properties from Micro-Manager with
 * UIProperty from the current UI. 
 * 
 * @author Joran Deschamps
 *
 */
public class PropertiesTable extends JPanel {

	private static final long serialVersionUID = 1L;
	
	private JComboBox<String> devices;
	private JTable table;
		
	private Map<String, UIProperty> uipropertySet_;
	private MMPropertiesRegistry mmproperties_;
	private String[] uipropkeys_;
	private HelpWindow help_;

	/**
	 * Constructor called when no configuration exists. Creates a table with non-allocated fields.
	 * 
	 * @param uipropertySet Map of the UI properties of the user interface indexed by their name.
	 * @param mmproperties Object containing the device properties from the current Micro-manager session. 
	 * @param help Help window.
	 */
	public PropertiesTable(Map<String, UIProperty> uipropertySet, MMPropertiesRegistry mmproperties, HelpWindow help) {

		uipropertySet_ = uipropertySet;
		mmproperties_ = mmproperties;
		help_ = help;


		// Combobox holding the devices name sorted alphabetically
		devices = new JComboBox<String>();
		String[] strarray = mmproperties_.getDevicesList().clone();
		Arrays.sort(strarray);
		
		devices.addItem(GlobalConfiguration.KEY_UNALLOCATED);
		for (int k = 0; k < strarray.length; k++) {
			devices.addItem(strarray[k]);
		}

		// Extracts uiproperties name from the UI map and sort them alphabetically
		String[] temp = new String[uipropertySet_.size()];
		uipropkeys_ = uipropertySet_.keySet().toArray(temp);
		Arrays.sort(uipropkeys_);

		// Defines table model
		DefaultTableModel model = getDefaultModel();

		// For each property of the UI
		for (int i = 0; i < uipropkeys_.length; i++) {
			// adds a row corresponding to the allocation of the UI property to a MM device property
			model.addRow(new Object[] { uipropkeys_[i], GlobalConfiguration.KEY_UNALLOCATED, GlobalConfiguration.KEY_UNALLOCATED });
			
			if (uipropertySet.get(uipropkeys_[i]) instanceof TwoStateUIProperty) {
				// if property is a toggle property, adds a line for the on and off values respectively.
				model.addRow(new Object[] {uipropkeys_[i] + TwoStateUIProperty.getOnStateLabel(),"", ConfigurationWizardUI.KEY_ENTERVALUE });
				model.addRow(new Object[] {uipropkeys_[i] + TwoStateUIProperty.getOffStateLabel(),"", ConfigurationWizardUI.KEY_ENTERVALUE });
				
			} else if (uipropertySet.get(uipropkeys_[i]) instanceof SingleStateUIProperty) {
				// if property is a single value property, adds a line for the value the property must take
				model.addRow(new Object[] {uipropkeys_[i] + SingleStateUIProperty.getStateLabel(),"", ConfigurationWizardUI.KEY_ENTERVALUE });
				
			} else if (uipropertySet.get(uipropkeys_[i]) instanceof MultiStateUIProperty) {
				// if multiple values property, adds a line for each of the value to be allocated
				int numpos = ((MultiStateUIProperty) uipropertySet.get(uipropkeys_[i])).getNumberOfStates();
				for(int j=0;j<numpos;j++){
					model.addRow(new Object[] {uipropkeys_[i] + MultiStateUIProperty.getConfigurationStateLabel(j),"", ConfigurationWizardUI.KEY_ENTERVALUE });
				}
			}  else if (uipropertySet.get(uipropkeys_[i]) instanceof RescaledUIProperty) {
				// if rescaled property, adds two lines for slope and offset
				double slope = ((RescaledUIProperty) uipropertySet.get(uipropkeys_[i])).getSlope();
				double offset = ((RescaledUIProperty) uipropertySet.get(uipropkeys_[i])).getOffset();

				model.addRow(new Object[] {uipropkeys_[i] + RescaledUIProperty.getSlopeLabel(),"", String.valueOf(slope) });
				model.addRow(new Object[] {uipropkeys_[i] + RescaledUIProperty.getOffsetLabel(),"", String.valueOf(offset) });
			} 
		}

		// creates JTable
		createTable(model);

		JScrollPane sc = new JScrollPane(table);

		this.add(sc);
	}

	/**
	 * Constructor called when modifying an existing configuration. Produces a table filled with preset fields. 
	 * If the fields are not found, then creates non-allocated ones.
	 * 
	 * @param uipropertySet UI properties map of the current UI.
	 * @param mmproperties MM properties found in the current Micro-manager configuration.
	 * @param propertymapping Mapping of UI and MM properties as read from a configuration.
	 * @param help Help window.
	 */
	public PropertiesTable(Map<String, UIProperty> uipropertySet, MMPropertiesRegistry mmproperties, Map<String, String> propertymapping, HelpWindow help) {

		uipropertySet_ = uipropertySet;
		mmproperties_ = mmproperties;
		help_ = help;

		// ComboBox holding the devices name sorted alphabetically
		devices = new JComboBox<String>();
		String[] strarray = mmproperties_.getDevicesList().clone();
		Arrays.sort(strarray);
		
		devices.addItem(GlobalConfiguration.KEY_UNALLOCATED);
		for (int k = 0; k < strarray.length; k++) {
			devices.addItem(strarray[k]);
		}

		// Extracts UIProperties name from the UI map and sort them alphabetically
		String[] temp = new String[uipropertySet_.size()];
		uipropkeys_ = uipropertySet_.keySet().toArray(temp);
		Arrays.sort(uipropkeys_);

		// Defines table model
		DefaultTableModel model = getDefaultModel();

		// Defines string for extraction of the values set for each property in the configuration
		String mmprop, uion, uioff, uisingle, uitemp;
		
		// For each property of the UI
		for (int i = 0; i < uipropkeys_.length; i++) {
			// gets the MM property corresponding to the UI property in the configuration
			mmprop = propertymapping.get(uipropkeys_[i]);
		
			// if the configuration indeed contains the property and is mapped in the configuration with an existing MM property
			if (propertymapping.containsKey(uipropkeys_[i]) && mmproperties.isProperty(mmprop)) { 
				// adds the UI property, its corresponding device and device property friendly name
				model.addRow(new Object[] { uipropkeys_[i], mmproperties.getProperty(mmprop).getDeviceLabel(), mmproperties.getProperty(mmprop).getMMPropertyLabel() });
				
				// if the property is an instance of SingleState, TwoState or MultiState property, then looks for the assigned values
				if (uipropertySet.get(uipropkeys_[i]) instanceof TwoStateUIProperty) { // if two state
					// gets values corresponding to the on and off states
					uion = propertymapping.get(uipropkeys_[i]+ TwoStateUIProperty.getOnStateLabel());
					uioff = propertymapping.get(uipropkeys_[i]+ TwoStateUIProperty.getOffStateLabel());
					
					// if null then sets to default
					if(uion == null){
						uion = ConfigurationWizardUI.KEY_ENTERVALUE;
					}
					if(uioff == null){
						uioff = ConfigurationWizardUI.KEY_ENTERVALUE;
					}
					
					// adds a row for each with the preset state value
					model.addRow(new Object[] {uipropkeys_[i] + TwoStateUIProperty.getOnStateLabel(),"", uion });
					model.addRow(new Object[] {uipropkeys_[i]+ TwoStateUIProperty.getOffStateLabel(), "", uioff });
				} else if (uipropertySet.get(uipropkeys_[i]) instanceof SingleStateUIProperty) { // if single value property
					// gets the value of the state and adds the corresponding row
					uisingle = propertymapping.get(uipropkeys_[i]+ SingleStateUIProperty.getStateLabel());
					if(uisingle == null){
						uion = ConfigurationWizardUI.KEY_ENTERVALUE;
					}
					model.addRow(new Object[] {	uipropkeys_[i] + SingleStateUIProperty.getStateLabel(), "", uisingle });
				} else if (uipropertySet.get(uipropkeys_[i]) instanceof MultiStateUIProperty) { // if multiple values property
					// gets the number of positions and extracts all position values. If null then sets to default. Finally creates the corresponding row.
					int numpos = ((MultiStateUIProperty) uipropertySet.get(uipropkeys_[i])).getNumberOfStates();
					for(int j=0;j<numpos;j++){
						uitemp = propertymapping.get(uipropkeys_[i]+ MultiStateUIProperty.getConfigurationStateLabel(j));

						if(uitemp == null){
							uitemp = ConfigurationWizardUI.KEY_ENTERVALUE;
						}
						
						model.addRow(new Object[] {	uipropkeys_[i] +  MultiStateUIProperty.getConfigurationStateLabel(j), "", uitemp });
					}
				} else if (uipropertySet.get(uipropkeys_[i]) instanceof RescaledUIProperty) { 
					// slope
					uitemp = propertymapping.get(uipropkeys_[i]+ RescaledUIProperty.getSlopeLabel());
					if(uitemp == null) { // get the default one
						uitemp = String.valueOf(((RescaledUIProperty) uipropertySet.get(uipropkeys_[i])).getSlope());
					}
					model.addRow(new Object[] {	uipropkeys_[i] +  RescaledUIProperty.getSlopeLabel(), "", uitemp });
					
					
					// offset
					uitemp = propertymapping.get(uipropkeys_[i]+ RescaledUIProperty.getOffsetLabel());
					if(uitemp == null) { // get the default one
						uitemp = String.valueOf(((RescaledUIProperty) uipropertySet.get(uipropkeys_[i])).getOffset());
					}
					model.addRow(new Object[] {	uipropkeys_[i] +  RescaledUIProperty.getOffsetLabel(), "", uitemp });
					
				} 
			} else {
				// if it is not found in the configuration
				model.addRow(new Object[] {uipropkeys_[i],GlobalConfiguration.KEY_UNALLOCATED,GlobalConfiguration.KEY_UNALLOCATED });
				
				// if the property is an instance of SingleState, TwoState or MultiState property, creates rows for the states value
				if (uipropertySet.get(uipropkeys_[i]) instanceof TwoStateUIProperty) {
					model.addRow(new Object[] {uipropkeys_[i] + TwoStateUIProperty.getOnStateLabel(),"", ConfigurationWizardUI.KEY_ENTERVALUE });
					model.addRow(new Object[] {uipropkeys_[i]+ TwoStateUIProperty.getOffStateLabel(), "",ConfigurationWizardUI.KEY_ENTERVALUE });
				} else if (uipropertySet.get(uipropkeys_[i]) instanceof SingleStateUIProperty) {
					model.addRow(new Object[] {uipropkeys_[i]+ SingleStateUIProperty.getStateLabel(), "",ConfigurationWizardUI.KEY_ENTERVALUE });
				} else if (uipropertySet.get(uipropkeys_[i]) instanceof MultiStateUIProperty) { // if multiple values property
					int numpos = ((MultiStateUIProperty) uipropertySet.get(uipropkeys_[i])).getNumberOfStates();
					for(int j=0;j<numpos;j++){
						model.addRow(new Object[] {uipropkeys_[i]+ MultiStateUIProperty.getConfigurationStateLabel(j), "",ConfigurationWizardUI.KEY_ENTERVALUE });
					}
				} else if (uipropertySet.get(uipropkeys_[i]) instanceof RescaledUIProperty) {
					// if rescaled property, adds two lines for slope and offset
					double slope = ((RescaledUIProperty) uipropertySet.get(uipropkeys_[i])).getSlope();
					double offset = ((RescaledUIProperty) uipropertySet.get(uipropkeys_[i])).getOffset();

					model.addRow(new Object[] {uipropkeys_[i] + RescaledUIProperty.getSlopeLabel(),"", String.valueOf(slope) });
					model.addRow(new Object[] {uipropkeys_[i] + RescaledUIProperty.getOffsetLabel(),"", String.valueOf(offset) });
				} 
			}
		}

		// Creates table
		createTable(model);

		JScrollPane sc = new JScrollPane(table);
		this.add(sc);
	}	
	
	private DefaultTableModel getDefaultModel(){
		return new DefaultTableModel(new Object[] {"UI property", "Device", "Property" }, 0);
	}
	
	private void createTable(DefaultTableModel model){
		// Creates table
		table = new JTable(model) {
			private static final long serialVersionUID = 1L;

			@Override
			public TableCellRenderer getCellRenderer(int row, int column) {
				switch (column) {
				case 0:
					return new BoldTableCellRenderer(); // first column is written in bold font
				case 1:
					return new ColoredUneditedTableRenderer(); // column 1 takes a colored default renderer (previously just default)
				case 2:
					return new ColoredUneditedTableRenderer(); // column 2 takes a colored default renderer
				default:
					return super.getCellRenderer(row, column);
				}
			}

			@Override
			public TableCellEditor getCellEditor(int row, int column) {
				String s = (String) table.getValueAt(row, 0);
				if (column == 2 && isStateValue(s)) { 
					// if in the last column and corresponds to a field value, returns a textfield cell editor
					return new DefaultCellEditor(new JTextField(ConfigurationWizardUI.KEY_ENTERVALUE));
				} else {
					// if not a field value or not in third column
					switch (column) {
					case 0:	// in the first column return default cell editor
						return super.getCellEditor(row, column);
					case 1: // in the second column return a JComboBox cell editor with the devices name
						return new DefaultCellEditor(devices);
					case 2: // in the last column return a JcomboBox cell editor with the properties name corresponding to the selected device
						return new DefaultCellEditor(getDevicePropertiesComboBox((String) getValueAt(row, 1), (String) getValueAt(row, 0)));
					default:
						return super.getCellEditor(row, column);
					}
				}
			}

			@Override
			public boolean isCellEditable(int row, int col) { // first column is non-editable and second as well if it is a field value row
				String s = (String) table.getValueAt(row, 0);
				if (col < 1) {
					return false;
				} else if (col == 1 && isStateValue(s)) {
					return false;
				} else {
					return true;
				}
			}
		};
		
		table.setAutoCreateRowSorter(false);

		table.setRowHeight(23);
		table.getColumnModel().getColumn(0).setMinWidth(160);
		table.getColumnModel().getColumn(1).setMinWidth(80);
		table.getColumnModel().getColumn(2).setMinWidth(100);

		// adds mouse listener to update the helper window
		table.addMouseListener(new java.awt.event.MouseAdapter() {
			@Override
			public void mouseClicked(java.awt.event.MouseEvent evt) {
				int row = table.rowAtPoint(evt.getPoint());
				updateHelper(row);
			}
		});
	}
	

	// Creates a JComboBox containing all the Micro-manager device properties corresponding to the device.
	private JComboBox<String> getDevicePropertiesComboBox(String device, String uipropName) {
		JComboBox<String>  cb = new JComboBox<String> ();
		cb.addItem(GlobalConfiguration.KEY_UNALLOCATED);

		if (!device.equals(GlobalConfiguration.KEY_UNALLOCATED)) {
			// RescaledUIProperty are only compatible with Integer- and FloatMMProperty with limits, so
			// we filter them here
			boolean rescaledUIProp = false;
			if(uipropertySet_.containsKey(uipropName) && uipropertySet_.get(uipropName) instanceof RescaledUIProperty) {
				rescaledUIProp = true;
			}
			
			String[] props = mmproperties_.getDevice(device).getPropertyLabels(); // fill with the name of the property itself
			if(!rescaledUIProp) {
				for (int i = 0; i < props.length; i++) {
					cb.addItem(props[i]);
				}
			} else {
				@SuppressWarnings("rawtypes")
				HashMap<String, MMProperty> mmprops = mmproperties_.getDevice(device).getProperties();
				ArrayList<String> list = new ArrayList<String>();
				
				Iterator<String> it = mmprops.keySet().iterator();
				while(it.hasNext()) {
					String s = it.next();
					if(uipropertySet_.get(uipropName).isCompatibleMMProperty(mmprops.get(s))) {
						list.add(mmprops.get(s).getMMPropertyLabel());
					}
				}
				Collections.sort(list);

				for(String s: list) {
					cb.addItem(s);
				}
			}
		}
		return cb;

	}
	
	/**
	 * Displays or hides the help window.
	 * 
	 * @param b True if the help window is to be displayed.
	 */
	public void showHelp(boolean b){
		help_.showHelp(b);
		updateHelper(table.getSelectedRow());
	}

	// Updates the helper window text with the description of the UI property represented by the selected row in the table.
	private void updateHelper(int row){
		String s = (String) table.getValueAt(row, 0);
	
		if(s.matches(MultiStateUIProperty.getGenericStateName())){
			int nmb = 0;
			for(int i=row;i>=1;i--){
				String str = (String) table.getValueAt(i-1, 0);
				if(!str.matches(MultiStateUIProperty.getGenericStateName())){
					nmb = i-1;
					break;
				}
			}
			int diff = row-nmb-1;
			s = (String) table.getValueAt(nmb, 0);
			help_.update(s,"Set the device property value corresponding to the state number "+diff+". The allowed values can be read out from the device property browser. State values cannot be left unconfigured if the property is allocated.");
		} else if (s.contains(TwoStateUIProperty.getOnStateLabel())){
			s = (String) table.getValueAt(row-1, 0);
			help_.update(s,"Enter the device property value corresponding to the ON state. The allowed values can be read out from the device property browser. State values cannot be left unconfigured if the property is allocated.");
		} else if (s.contains(TwoStateUIProperty.getOffStateLabel())){
			s = (String) table.getValueAt(row-2, 0);
			help_.update(s,"Enter the device property value corresponding to the OFF state. The allowed values can be read out from the device property browser. State values cannot be left unconfigured if the property is allocated.");
		}  else if (s.contains(RescaledUIProperty.getSlopeLabel())){
			s = (String) table.getValueAt(row-1, 0);
			help_.update(s,"The values set in the UI will be rescaled to slope*value+offset before setting the device property state. Enter here a value for the slope.");
		}  else if (s.contains(RescaledUIProperty.getOffsetLabel())){
			s = (String) table.getValueAt(row-2, 0);
			help_.update(s,"The values set in the UI will be rescaled to slope*value+offset before setting the device property state. Enter here a value for the offset.");
		} else if (s.contains(SingleStateUIProperty.getStateLabel())){
			s = (String) table.getValueAt(row-1, 0);
			help_.update(s,"Enter the device property value corresponding to the single state.");
		} else if(uipropertySet_.containsKey(s)){
			help_.update(s,uipropertySet_.get(s).getDescription());
		}
	}
	
	/**
	 * Extracts the fields filled by the user. 
	 * 
	 * @return HashMap of the UI properties and the corresponding MM properties and states value.
	 */
	public HashMap<String,String> getSettings(){
		HashMap<String,String> map = new HashMap<String,String>();
		
		TableModel model = table.getModel();
		int nrow = model.getRowCount();
		
		for(int i=0;i<nrow;i++){
			// if device column is not empty (from state property, e.g. single state value or multistates) and is not unallocated
			if (!isStateValue((String) model.getValueAt(i, 0)) && !((String) model.getValueAt(i, 1)).equals(GlobalConfiguration.KEY_UNALLOCATED)) { 
				String propertyname = (String) model.getValueAt(i, 2);
				if( mmproperties_.getDevice((String) model.getValueAt(i, 1)).hasLabelProperty(propertyname)){
					String hash = mmproperties_.getDevice((String) model.getValueAt(i, 1)).getHashFromLabel(propertyname);
					map.put((String) model.getValueAt(i, 0), hash); // put with the property hash
				} else {
					map.put((String) model.getValueAt(i, 0), GlobalConfiguration.KEY_UNALLOCATED); // set to unallocated
				}
			} else {
				map.put((String) model.getValueAt(i, 0), (String) model.getValueAt(i, 2)); // keep the unallocated as is
			}
		}
		
		return map;
	}
	
	
	/**
	 * Tests if the string has been generated by a SingelStateUIProperty, a TwoStateUIProperty, a MultiStateUIProperty or a RescaledUIProperty.
	 * 
	 * @param s String to test, value in the first column of the table
	 * @return True if corresponds to a field value.
	 */
	public boolean isStateValue(String s){
		if (s.contains(SingleStateUIProperty.getStateLabel())
				|| s.contains(TwoStateUIProperty.getOnStateLabel()) 
				|| s.contains(TwoStateUIProperty.getOffStateLabel())
				|| s.contains(RescaledUIProperty.getSlopeLabel())
				|| s.contains(RescaledUIProperty.getOffsetLabel())
				|| s.matches(".*"+MultiStateUIProperty.getGenericStateName()+".*")){
			return true;
		}
		return false;
	}
	
	/**
	 * Renders cell text with a bold font. Adapted from: https://stackoverflow.com/questions/22325138/cellrenderer-making-text-bold
	 */
	public class BoldTableCellRenderer extends DefaultTableCellRenderer {

		private static final long serialVersionUID = 1L;

		public Component getTableCellRendererComponent(JTable table,
                Object value, boolean isSelected, boolean hasFocus,
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
	
	
	public static class ColoredUneditedTableRenderer extends DefaultTableCellRenderer {

		private static final long serialVersionUID = 1L;

		@Override
	    public Component getTableCellRendererComponent(JTable table, Object value,
	            boolean isSelected, boolean hasFocus, int row, int column) {

	        Component c = super.getTableCellRendererComponent(table, value, isSelected,
	                hasFocus, row, column);

	        if (column > 0) {
	            String versionVal = (String) value;

	            if (versionVal.equals(ConfigurationWizardUI.KEY_ENTERVALUE) || versionVal.equals(GlobalConfiguration.KEY_UNALLOCATED)) {
	                c.setForeground(Color.RED);
	            } else {
	                c.setForeground(Color.BLACK);
	            }
	        }
	        return c;
	    }
	}
	
}