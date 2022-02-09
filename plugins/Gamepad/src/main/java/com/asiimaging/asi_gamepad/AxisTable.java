///////////////////////////////////////////////////////////////////////////////
//FILE:          AxisTable.java
//PROJECT:       Micro-Manager 
//SUBSYSTEM:     asi gamepad plugin
//-----------------------------------------------------------------------------
//
// AUTHOR:       Vikram Kopuri
//
// COPYRIGHT:    Applied Scientific Instrumentation (ASI), 2018
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

package com.asiimaging.asi_gamepad;

import javax.swing.JPanel;

import org.micromanager.Studio;
import org.micromanager.propertymap.MutablePropertyMapView;

import javax.swing.DefaultCellEditor;
import javax.swing.JComboBox;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.Arrays;

import com.ivan.xinput.enums.XInputAxis;

public class AxisTable extends JPanel implements TableModelListener {

	private static final long serialVersionUID = 206955328057031300L;
	private static final String NO_DEVICE = "";
	private static final String DEVICE = "_Device";
	private static final String PROPERTY = "_Property";
	private static final String MULTIPLIER = "_Multiplier";
	private Studio studio_;
	private MutablePropertyMapView settings_;
	public JTable table_;

	public AxisTable(Studio studio) {
		super(new GridLayout(1,0));

		studio_ = studio;
		settings_ = studio_.profile().getSettings(this.getClass());

		String[] columnNames = {"Joystick",
				"Device",
				"Property",
				"Multiplier"};

		Object[][] data= new Object[XInputAxis.values().length][columnNames.length];

		int idx=0;

		for (XInputAxis joystick : XInputAxis.values() ) {
			data[idx][0]= joystick;
			data[idx][1]= settings_.getString(joystick.name() + DEVICE, "");
			data[idx][2]= settings_.getString(joystick.name() + PROPERTY, "");
			data[idx][3]= settings_.getFloat(joystick.name() + MULTIPLIER, 1.0f);
			idx++;
		}

		DefaultTableModel model = new DefaultTableModel(data,columnNames){

			private static final long serialVersionUID = 1313905834950710481L;

			@Override
			public boolean isCellEditable(int row, int column) {
				//First column is readonly
				return column != 0;
			}
		};

		table_ = new JTable(model);

		table_.setPreferredScrollableViewportSize(new Dimension(400, XInputAxis.values().length* table_.getRowHeight()));
		table_.setFillsViewportHeight(true);

		//Create the scroll pane and add the table to it.
		JScrollPane scrollPane = new JScrollPane(table_);

		setupCellEditors();

		//Add the scroll pane to this panel.
		add(scrollPane);

		//Listener
		table_.getModel().addTableModelListener(this);

	}// end of constructor

/**
 * create and assign celleditor for device column.
 */
	public void setupCellEditors() {
		setupDeviceColumn(table_.getColumnModel().getColumn(1));

	} 
/**
 * setup column's cell editor with a combobox with currently loaded devices 
 * @param deviceColumn column in table with devices
 */
	private void setupDeviceColumn(TableColumn deviceColumn) {
		//Set up the celleditor for device column.

		ArrayList<String> devices = new ArrayList<>();
		devices.add(NO_DEVICE);
		devices.addAll((Arrays.asList(studio_.getCMMCore().getLoadedDevices().toArray())));
		String[] dummy = new String[devices.size()];
		JComboBox<String> comboBox = new JComboBox<>(devices.toArray(dummy));

		deviceColumn.setCellEditor(new DefaultCellEditor(comboBox));

		//Set up tool tips for the sport cells.
		DefaultTableCellRenderer renderer = new DefaultTableCellRenderer();
		renderer.setToolTipText("Assign Device to Joystick");
		deviceColumn.setCellRenderer(renderer);
	}//end of setUpSportColumn



	/**
	 * Based on Device selected in column 1, setup cell editor for properties column
	 * 
	 */
	@Override
	public void tableChanged(TableModelEvent arg0) {

		int row = arg0.getFirstRow();
		int column = arg0.getColumn();

		DefaultTableModel model = (DefaultTableModel) arg0.getSource();

		if(column==1) {
			// if first column was changed , we need to populate 2nd column with properties
			String devName = (String) model.getValueAt(row, column);

			if (!devName.equals(NO_DEVICE)) {
				try {
					JComboBox<String> comboBox = new JComboBox<>(
							studio_.getCMMCore().getDevicePropertyNames(devName).toArray());
					table_.getColumnModel().getColumn(2).setCellEditor(
							new DefaultCellEditor(comboBox));
					//Set up tool tips for the sport cells.
					DefaultTableCellRenderer renderer = new DefaultTableCellRenderer();
					renderer.setToolTipText("Pick property to control");
					table_.getColumnModel().getColumn(2).setCellRenderer(renderer);
				} catch (Exception e) {
					e.printStackTrace();
				}
			} else {
				table_.getModel().removeTableModelListener(this);
				table_.getModel().setValueAt("", row, 2);
				settings_.putString(table_.getValueAt(row, 0) + PROPERTY,
						(String) table_.getValueAt(row, 2));
				table_.getModel().addTableModelListener(this);
			}
		}

		switch (column) {
			case 1:
				settings_.putString(table_.getValueAt(row, 0) + DEVICE,
						(String) table_.getValueAt(row, column));
				break;
			case 2:
				settings_.putString(table_.getValueAt(row, 0) + PROPERTY,
						(String) table_.getValueAt(row, column));
				break;
			case 3:
				settings_.putFloat(table_.getValueAt(row, 0) + MULTIPLIER,
						Float.parseFloat((String)table_.getValueAt(row, column)));
						   //(Float) table_.getValueAt(row, column));
				break;
		}

		if (row == -1 && column == -1) {
			// save the complete table
			for (int r = 0; r < table_.getModel().getRowCount(); r++) {
				settings_.putString(table_.getValueAt(r, 0) + DEVICE,
						(String) table_.getValueAt(r, 1));
				settings_.putString(table_.getValueAt(r, 0) + PROPERTY,
						(String) table_.getValueAt(r, 2));
				settings_.putFloat(table_.getValueAt(r, 0) + MULTIPLIER,
						(Float) table_.getValueAt(r, 3));
			}
		}
	}//end of tableChanged

}//end of class
