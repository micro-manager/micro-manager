///////////////////////////////////////////////////////////////////////////////
//FILE:          ButtonTable.java
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

/**Button assignment table class
 * @author Vikram Kopuri for ASI
 */


package com.asiimaging.asi_gamepad;

import javax.swing.JPanel;

import javax.swing.DefaultCellEditor;
import javax.swing.JComboBox;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import java.awt.Dimension;
import java.awt.GridLayout;

import com.ivan.xinput.enums.XInputButton ;

//A good table tutorial can be found at https://docs.oracle.com/javase/tutorial/uiswing/components/table.html


public class ButtonTable extends JPanel  {


	private static final long serialVersionUID = 5334839209473245807L;
	public JTable table;
	public ButtonTable() {
		super(new GridLayout(1,0));


		String[] columnNames = {"Button",
				"Action",
		"Beanshell Script"};

		Object[][] data=new Object[XInputButton.values().length][columnNames.length];

		int idx=0;

		for(XInputButton buttons :XInputButton.values() ) {
			data[idx][0]= buttons; 
			data[idx][1]= ButtonActions.ActionItems.Undefined; 
			data[idx][2]= new String(""); 
			idx++;

		}

		DefaultTableModel model = new DefaultTableModel(data,columnNames){

			private static final long serialVersionUID = 1L;

			@Override
			public boolean isCellEditable(int row, int column) {
				//First column is readonly
				if(column==0) return false;

				return true;
			}
		};

		table = new JTable(model);
		
		
		
		table.setPreferredScrollableViewportSize(new Dimension(300, XInputButton.values().length*table.getRowHeight()));
		table.setFillsViewportHeight(true);

		//Create the scroll pane and add the table to it.
		JScrollPane scrollPane = new JScrollPane(table);

		setUpcelleditors();



		//Add the scroll pane to this panel.
		add(scrollPane);

	}// end of constructor

	public void setUpcelleditors() {

		//create and assign celleditor for action column.
		setUpactionColumn(table.getColumnModel().getColumn(1));

		setUpscriptcol(table.getColumnModel().getColumn(2));
	} 
/**
 * setup a combobox with Button actions enum as 2nd column's cell editor
 * @param deviceColumn column that will get the new custom editor
 */
	private void setUpactionColumn(TableColumn deviceColumn) {
		
		JComboBox comboBox = new JComboBox();

		for (ButtonActions.ActionItems ai : ButtonActions.ActionItems.values() ) {

			comboBox.addItem(ai);

		} 


		deviceColumn.setCellEditor(new DefaultCellEditor(comboBox));

		//Set up tool tips for the sport cells.
		DefaultTableCellRenderer renderer =
				new DefaultTableCellRenderer();
		renderer.setToolTipText("Assign actions to buttons");
		deviceColumn.setCellRenderer(renderer);
	}//end of setUpSportColumn

/**
 * Set up the editor for the Beanshell script column.
 * @param deviceColumn column that will get the new custom editor
 */
	private void setUpscriptcol(TableColumn deviceColumn) {
		

		deviceColumn.setCellEditor(new filecelleditor());


	}



}//end of class
