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

/** Class for the Axis assignment table
 * @author Vikram Kopuri for ASI
 */

package com.asiimaging.asi_gamepad;

import javax.swing.JPanel;

import org.micromanager.api.ScriptInterface;

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

import com.ivan.xinput.enums.XInputAxis;

public class AxisTable extends JPanel implements TableModelListener {

	private static final long serialVersionUID = 206955328057031300L;
	private ScriptInterface gui_;
	public JTable table;

	public AxisTable(ScriptInterface gui) {
		super(new GridLayout(1,0));

		gui_=gui;

		String[] columnNames = {"Joystick",
				"Device",
				"Property",
		"Multiplier"};

		Object[][] data= new Object[XInputAxis.values().length][columnNames.length];

		int idx=0;



		for(XInputAxis joysticks :XInputAxis.values() ) {
			data[idx][0]= joysticks; 
			data[idx][1]= new String(""); 
			data[idx][2]= new String("");
			data[idx][3]= new Float(1.0);
			idx++;

		}

		DefaultTableModel model = new DefaultTableModel(data,columnNames){

			private static final long serialVersionUID = 1313905834950710481L;

			@Override
			public boolean isCellEditable(int row, int column) {
				//First column is readonly
				if(column==0) return false;

				return true;
			}
		};

		table = new JTable(model);

		table.setPreferredScrollableViewportSize(new Dimension(400, XInputAxis.values().length*table.getRowHeight()));
		table.setFillsViewportHeight(true);

		//Create the scroll pane and add the table to it.
		JScrollPane scrollPane = new JScrollPane(table);

		setUpcelleditors();

		//Add the scroll pane to this panel.
		add(scrollPane);

		//Listener
		table.getModel().addTableModelListener(this);

	}// end of constructor
/**
 * create and assign celleditor for device column.
 */
	public void setUpcelleditors() {

		
		setUpdeviceColumn(table.getColumnModel().getColumn(1));

	} 
/**
 * setup column's cell editor with a combobox with currently loaded devices 
 * @param deviceColumn
 */
	private void setUpdeviceColumn(TableColumn deviceColumn) {
		//Set up the celleditor for device column.


		JComboBox comboBox = new JComboBox(gui_.getMMCore().getLoadedDevices().toArray());

		deviceColumn.setCellEditor(new DefaultCellEditor(comboBox));

		//Set up tool tips for the sport cells.
		DefaultTableCellRenderer renderer =
				new DefaultTableCellRenderer();
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
			//if first column was changed , we need to populate 2nd column with properties
			String dev_name = (String) model.getValueAt(row, column);


			try {
				JComboBox comboBox = new JComboBox(gui_.getMMCore().getDevicePropertyNames(dev_name).toArray());
				table.getColumnModel().getColumn(2).setCellEditor(new DefaultCellEditor( comboBox));
				//Set up tool tips for the sport cells.
				DefaultTableCellRenderer renderer =
						new DefaultTableCellRenderer();
				renderer.setToolTipText("Pick property to control");
				table.getColumnModel().getColumn(2).setCellRenderer(renderer);
			} catch (Exception e) {

				e.printStackTrace();
			} 




		}


	}//end of tableChanged




}//end of class
