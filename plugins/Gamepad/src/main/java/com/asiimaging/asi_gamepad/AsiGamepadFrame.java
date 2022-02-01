///////////////////////////////////////////////////////////////////////////////
//FILE:          asi_gamepad_frame.java
//PROJECT:       Micro-Manager 
//SUBSYSTEM:      asi gamepad plugin
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

/** The main frame class of the plugin , since the plugin is simple didn't use model-view-controller convention
 * @author Vikram Kopuri for ASI
 */
package com.asiimaging.asi_gamepad;

import java.awt.Desktop;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.URI;
import java.util.Vector;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.Timer;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;

import org.micromanager.Studio;
import org.micromanager.internal.utils.WindowPositioning;

import com.ivan.xinput.enums.XInputAxis;
import com.ivan.xinput.enums.XInputButton;
import com.ivan.xinput.XInputAxesDelta;
import com.ivan.xinput.XInputButtonsDelta;
import com.ivan.xinput.XInputDevice;

import mmcorej.PropertyType;

import net.miginfocom.swing.MigLayout;

import com.asiimaging.asi_gamepad.ButtonActions.ActionItems;




public class AsiGamepadFrame extends JFrame {

	private static final long serialVersionUID = 1545357641369351650L;
	private Studio studio_;
	private JLabel l_gpad_stat_;
	private Timer mytimer_;
	private ButtonTable btn_table;
	private AxisTable axis_table;
	XInputDevice ctrl;
	boolean was_disconnected; 
	private JButton btn_save;
	private JButton btn_load;
	private JButton btn_asi;
	private JButton btn_help;
	private JFileChooser myJFileChooser;
	ButtonActions ba;


	//Constants
	//public final float plugin_ver = (float) 0.1;

	private final int gpad_poll_time=100;//millisec between polling state
	private final float axis_deadcount =(float) 0.1;
	
	private final String GPAD_CONNECTING ="GamePad:Connecting";
	private final String GPAD_CONNECTED ="GamePad: Found";
	private final String GPAD_NOT_FOUND ="GamePad: NOT Found";
	private final String GPAD_ERROR_CONNECTING ="GamePad:Error Connecting";
	
	private final String MANUAL_LINK = "http://www.asiimaging.com/docs/asi_gamepad";
	private final String ASI_LINK = "http://www.asiimaging.com/";

	FileFilter saveFilter;

	public AsiGamepadFrame(Studio gui) {
		this.setLayout(new MigLayout(
				"",
				"[grow,fill]rel[grow,fill]",
				"[]8[]"));

		// create interface objects used by panels
		studio_ = gui;

		// put frame back where it was last time
		this.setLocation(100, 100);
		WindowPositioning.setUpLocationMemory(this, this.getClass(), null);

		//Lets make sure lib is available
		if(!XInputDevice.isAvailable()) {

			studio_.logs().showMessage("Unable to load plugin because JXInput isn't loaded. Click OK to Exit");
			return;
		}
		//Adding GUI elements///////////////////////////////////

		//label
		l_gpad_stat_=new JLabel("Gamepad Status");
		add(l_gpad_stat_,"wrap,center");

		//save btn
		btn_save= new JButton("SAVE");
		add(btn_save);
		btn_save.addActionListener(e -> saveTable());

		//load btn
		btn_load= new JButton("LOAD");
		add(btn_load,"wrap");
		btn_load.addActionListener(e -> loadTable());

		//LABEL
		add(new JLabel("Axis Assignment Table"),"span");

		//Joystick/Axis table
		axis_table = new AxisTable(studio_);
		axis_table.setOpaque(true);
		add(axis_table,"span");

		//LABEL
		add(new JLabel("Button Assignment Table"),"span");

		//Button Action table
		btn_table=new ButtonTable(studio_);
		btn_table.setOpaque(true);
		add(btn_table,"span");

		//help button
		btn_help = new JButton("HELP");
		add(btn_help,"span");
		btn_help.addActionListener(e -> open_uri(MANUAL_LINK));


		//credit button
		btn_asi = new JButton("Made with \u2764 by ASI");
		//don't want the button to show like a button
		btn_asi.setBorderPainted(false);
		btn_asi.setFocusPainted(false);
		btn_asi.setContentAreaFilled(false);
		add(btn_asi,"span");
		btn_asi.addActionListener(e -> open_uri(ASI_LINK));

		// finalize the window
		setTitle("ASI Gamepad Beta " + AsiGamepad.VERSION_STRING);
		pack();           // shrinks the window as much as it can

		// take care of shutdown tasks when window is closed
		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

		this.setVisible(true);

		////////////////////////////////////////////////////////

		was_disconnected = true;

		//timer
		mytimer_=new Timer(gpad_poll_time, e -> timer_tick());


		try {
			XInputDevice[] devices = XInputDevice.getAllDevices();

			if(devices.length==0) {
				studio_.logs().showMessage("No Gamepad found. Unable to open plugin");
				dispose();
				return;
			}

			ctrl = XInputDevice.getDeviceFor(0);
			l_gpad_stat_.setText(GPAD_CONNECTING);
			ba = new ButtonActions();
			mytimer_.start();
		} catch (Exception e) {

			e.printStackTrace();
			mytimer_.stop();
			l_gpad_stat_.setText(GPAD_ERROR_CONNECTING);
		}

		saveFilter = new FileNameExtensionFilter("Save File","save");

		// change plugin icon to a microscope
		setIconImage(Icons.MICROSCOPE.getImage());

		// allow the API to access the table => plugin must be open
		GamepadAPI.setAxisTable(axis_table);
	}

/**
 * opens url in browser
 * @param link url in string
 */
	private void open_uri(String link) {
		if (Desktop.isDesktopSupported()) {
			try {
				Desktop.getDesktop().browse(new URI(link));
			} catch (Exception e1) { /* TODO: error handling */ }
		} 
	}

	//https://coderanch.com/t/620911/java/Saving-Loading-content-JTable
	/**
	 * action on save button press
	 */
	private void saveTable() {
		myJFileChooser = new JFileChooser(new File("."));
		myJFileChooser.setFileFilter(saveFilter);
		if (myJFileChooser.showSaveDialog(this) ==
				JFileChooser.APPROVE_OPTION ) {
			saveTable(myJFileChooser.getSelectedFile());
		}
	}

	/**
	 * saves the contents of the two tables to a file
	 * @param savefile absolute path and file name of save file
	 */
	private void saveTable(File savefile) {
		try {

			//check and add extension
			if(!savefile.getName().contains(".")) {
            savefile = new File(savefile.toString()+".save");
         }

			ObjectOutputStream out = new ObjectOutputStream(
					new FileOutputStream(savefile));
			//note the order , the order data written is the same order it should be retrived. 
			//lets write plugin version to file , maybe useful in future
			
			out.writeFloat(Float.parseFloat(AsiGamepad.VERSION_STRING));
			
			DefaultTableModel model = (DefaultTableModel) btn_table.table.getModel();

			out.writeObject(model.getDataVector());
			model = (DefaultTableModel) axis_table.table_.getModel();
			out.writeObject(model.getDataVector());
			out.close();
		}
		catch (Exception ex) {
			ex.printStackTrace();
		}
	}

/**
 * action on load file button press
 */
	private void loadTable() {
		myJFileChooser = new JFileChooser(new File("."));
		myJFileChooser.setFileFilter(saveFilter);
		if (myJFileChooser.showOpenDialog(this) ==
				JFileChooser.APPROVE_OPTION )
			loadTable(myJFileChooser.getSelectedFile());
	}
/**
 * Reads the previous save file and loads the data into the axis and button tables
 * @param loadfile  absolute path and file name of load file
 */
	private void loadTable(File loadfile) {
		try {

			if(!loadfile.exists()) {
				return; //leave if file doesn't exist
			}

			ObjectInputStream in = new ObjectInputStream(
					new FileInputStream(loadfile));
			
			float file_ver=in.readFloat();//not useful now, but maybe handy in future to help compatibility
			
			Vector<?> rowData = (Vector<?>)in.readObject();
			DefaultTableModel model= (DefaultTableModel) btn_table.table.getModel();
			model.setDataVector(rowData, getColumnNames(btn_table.table));
			rowData = (Vector<?>)in.readObject();
			model = (DefaultTableModel) axis_table.table_.getModel();
			model.setDataVector(rowData, getColumnNames(axis_table.table_));

			in.close();

			//need to setup celleditor and renderers again
			btn_table.setupCellEditors();
			axis_table.setupCellEditors();

			((DefaultTableModel) btn_table.table.getModel()).fireTableDataChanged();
			((DefaultTableModel) axis_table.table_.getModel()).fireTableDataChanged();


		}
		catch (Exception ex) {
			studio_.logs().logError(ex);
		}
	}

/**
 * Column names are always same , so getting them from table itself rather than save file
 * @param table
 * @return column names as vectors
 */
	private Vector<String> getColumnNames(JTable table) {
		Vector<String> columnNames = new Vector<>();
		for (int i = 0; i < table.getColumnCount(); i++)
			columnNames.add(table.getColumnName(i) );
		return columnNames;
	}

	/**
	 * The program loop for the plugin , polls the device and acts on actions
	 */
	private void timer_tick() {

		if(!ctrl.poll()) {
			l_gpad_stat_.setText(GPAD_NOT_FOUND);
			was_disconnected = true;
			return;
		}

		if(!ctrl.isConnected()) {
			l_gpad_stat_.setText(GPAD_NOT_FOUND);
			was_disconnected = true;
			return;

		}

		// Yay gamepad found , let the fun stuff begin
		if(was_disconnected) {
			l_gpad_stat_.setText(GPAD_CONNECTED);
			was_disconnected=false;
		}

		// Let's get the deltas
		XInputButtonsDelta btn_delta = ctrl.getDelta().getButtons(); 
		XInputAxesDelta axis_delta = ctrl.getDelta().getAxes();

		ActionItems action_todo;
		String action_string;

		int row_idx;

		// Let's do buttons //////////////////////////////////
		for(row_idx = 0;row_idx < btn_table.table.getRowCount(); row_idx++) {

			//is pressed is denounced, only true once after first press
			if(btn_delta.isPressed((XInputButton)btn_table.table.getValueAt(row_idx,0))){
				//Button pressed execute action
				action_todo = (ActionItems) btn_table.table.getModel().getValueAt(row_idx,1);
				action_string = (String) btn_table.table.getModel().getValueAt(row_idx,2);

				ba.ExecuteAction(action_todo, action_string);
			}

		}//end of for

		// Let's do Axes /////////////////////////////
		float temp;
		XInputAxis ca ;
		String dev_name; 
		String prop_name;
		float mul;       

		for(row_idx=0; row_idx<axis_table.table_.getRowCount(); row_idx++) {

			ca = (XInputAxis)axis_table.table_.getValueAt(row_idx,0);
			dev_name = axis_table.table_.getValueAt(row_idx,1).toString();
			prop_name =axis_table.table_.getValueAt(row_idx,2).toString();
			mul= Float.parseFloat(axis_table.table_.getValueAt(row_idx,3).toString());

			if(dev_name.isEmpty() | prop_name.isEmpty() | mul==0) continue;

			if(axis_delta.getDelta(ca)!=0) {

				temp=ctrl.getComponents().getAxes().get(ca);
				if(Math.abs(temp)<axis_deadcount) { 
					temp= 0;
				}
				temp*=mul;

				try {
					// check if prop has limits

					if(studio_.getCMMCore().hasPropertyLimits(dev_name, prop_name)) {
						double min = studio_.getCMMCore().getPropertyLowerLimit(dev_name, prop_name);
						double max = studio_.getCMMCore().getPropertyUpperLimit(dev_name, prop_name);
						if (temp>max) {
							temp=(float) max;
						} else if (temp<min) {
							temp=(float) min;
						}
					}

					PropertyType pt = studio_.getCMMCore().getPropertyType(dev_name, prop_name);

					if(pt== PropertyType.Integer) {
						studio_.getCMMCore().setProperty(dev_name, prop_name,(int)temp);
					}else if(pt== PropertyType.String) {
						studio_.getCMMCore().setProperty(dev_name, prop_name,Float.toString(temp));
					}else if(pt== PropertyType.Float) { 
						studio_.getCMMCore().setProperty(dev_name, prop_name, temp);
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}

		}//end of for

	}//end of timer_tick

	public void dispose() {
		//ReportingUtils.logMessage("!!!!closed from main frame!!!!");
		mytimer_.stop();
		mytimer_ = null;
		btn_table = null;
		axis_table = null;
		ctrl = null;
		ba = null;
		super.dispose();
	}

}
