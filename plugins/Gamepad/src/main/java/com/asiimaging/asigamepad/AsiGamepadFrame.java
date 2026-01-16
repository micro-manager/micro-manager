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

/**
 * The main frame class of the plugin , since the plugin is simple didn't
 * use model-view-controller convention
 *
 * @author Vikram Kopuri for ASI
 */

package com.asiimaging.asigamepad;

import com.asiimaging.asigamepad.ButtonActions.ActionItems;
import com.ivan.xinput.XInputAxesDelta;
import com.ivan.xinput.XInputButtonsDelta;
import com.ivan.xinput.XInputDevice;
import com.ivan.xinput.enums.XInputAxis;
import com.ivan.xinput.enums.XInputButton;
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
import mmcorej.PropertyType;
import net.miginfocom.swing.MigLayout;
import org.micromanager.Studio;
import org.micromanager.internal.utils.WindowPositioning;


public class AsiGamepadFrame extends JFrame {

   private static final long serialVersionUID = 1545357641369351650L;
   private Studio studio_;
   private JLabel lGpadStat;
   private Timer myTimer_;
   private ButtonTable btnTable;
   private AxisTable axisTable;
   XInputDevice ctrl;
   boolean wasDisconnected;
   private JButton btnSave;
   private JButton btnLoad;
   private JButton btnAsi;
   private JButton btnHelp;
   private JFileChooser myJFileChooser;
   ButtonActions ba;


   //Constants
   //public final float plugin_ver = (float) 0.1;

   private final int gpadPollTime = 100; //millisec between polling state
   private final float axisDeadcount = (float) 0.1;

   private final String gpadConnecting = "GamePad:Connecting";
   private final String gpadConnected = "GamePad: Found";
   private final String gpadNotFound = "GamePad: NOT Found";
   private final String gpadErrorConnecting = "GamePad:Error Connecting";

   private final String manualLink = "http://www.asiimaging.com/docs/asi_gamepad";
   private final String asiLink = "http://www.asiimaging.com/";

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
      if (!XInputDevice.isAvailable()) {

         studio_.logs()
               .showMessage("Unable to load plugin because JXInput isn't loaded. Click OK to Exit");
         return;
      }
      //Adding GUI elements///////////////////////////////////

      //label
      lGpadStat = new JLabel("Gamepad Status");
      add(lGpadStat, "wrap,center");

      //save btn
      btnSave = new JButton("SAVE");
      add(btnSave);
      btnSave.addActionListener(e -> saveTable());

      //load btn
      btnLoad = new JButton("LOAD");
      add(btnLoad, "wrap");
      btnLoad.addActionListener(e -> loadTable());

      //LABEL
      add(new JLabel("Axis Assignment Table"), "span");

      //Joystick/Axis table
      axisTable = new AxisTable(studio_);
      axisTable.setOpaque(true);
      add(axisTable, "span");

      //LABEL
      add(new JLabel("Button Assignment Table"), "span");

      //Button Action table
      btnTable = new ButtonTable(studio_);
      btnTable.setOpaque(true);
      add(btnTable, "span");

      //help button
      btnHelp = new JButton("HELP");
      add(btnHelp, "span");
      btnHelp.addActionListener(e -> openUri(manualLink));


      //credit button
      btnAsi = new JButton("Made with \u2764 by ASI"); // Love?
      //don't want the button to show like a button
      btnAsi.setBorderPainted(false);
      btnAsi.setFocusPainted(false);
      btnAsi.setContentAreaFilled(false);
      add(btnAsi, "span");
      btnAsi.addActionListener(e -> openUri(asiLink));

      // finalize the window
      setTitle("ASI Gamepad Beta " + AsiGamepad.VERSION_STRING);
      pack();           // shrinks the window as much as it can

      // take care of shutdown tasks when window is closed
      setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

      this.setVisible(true);

      ////////////////////////////////////////////////////////

      wasDisconnected = true;

      //timer
      myTimer_ = new Timer(gpadPollTime, e -> timerTick());


      try {
         XInputDevice[] devices = XInputDevice.getAllDevices();

         if (devices.length == 0) {
            studio_.logs().showMessage("No Gamepad found. Unable to open plugin");
            dispose();
            return;
         }

         ctrl = XInputDevice.getDeviceFor(0);
         lGpadStat.setText(gpadConnecting);
         ba = new ButtonActions();
         myTimer_.start();
      } catch (Exception e) {

         e.printStackTrace();
         myTimer_.stop();
         lGpadStat.setText(gpadErrorConnecting);
      }

      saveFilter = new FileNameExtensionFilter("Save File", "save");

      // change plugin icon to a microscope
      setIconImage(Icons.MICROSCOPE.getImage());

      // allow the API to access the table => plugin must be open
      GamepadAPI.setAxisTable(axisTable);
   }

   /**
    * opens url in browser.
    *
    * @param link url in string
    */
   private void openUri(String link) {
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
      if (myJFileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
         saveTable(myJFileChooser.getSelectedFile());
      }
   }

   /**
    * saves the contents of the two tables to a file
    *
    * @param savefile absolute path and file name of save file
    */
   private void saveTable(File savefile) {
      try {

         //check and add extension
         if (!savefile.getName().contains(".")) {
            savefile = new File(savefile.toString() + ".save");
         }

         ObjectOutputStream out = new ObjectOutputStream(
               new FileOutputStream(savefile));
         //note the order , the order data written is the same order it should be retrived.
         //lets write plugin version to file , maybe useful in future

         out.writeFloat(Float.parseFloat(AsiGamepad.VERSION_STRING));

         DefaultTableModel model = (DefaultTableModel) btnTable.table.getModel();

         out.writeObject(model.getDataVector());
         model = (DefaultTableModel) axisTable.table_.getModel();
         out.writeObject(model.getDataVector());
         out.close();
      } catch (Exception ex) {
         ex.printStackTrace();
      }
   }

   /**
    * action on load file button press
    */
   private void loadTable() {
      myJFileChooser = new JFileChooser(new File("."));
      myJFileChooser.setFileFilter(saveFilter);
      if (myJFileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
         loadTable(myJFileChooser.getSelectedFile());
      }
   }

   /**
    * Reads the previous save file and loads the data into the axis and button tables
    *
    * @param loadfile absolute path and file name of load file
    */
   private void loadTable(File loadfile) {
      try {

         if (!loadfile.exists()) {
            return; //leave if file doesn't exist
         }

         ObjectInputStream in = new ObjectInputStream(
               new FileInputStream(loadfile));

         float fileVer =
               in.readFloat(); //not useful now, but maybe handy in future to help compatibility

         Vector<? extends Vector<?>> rowData = (Vector<? extends Vector<?>>) in.readObject();
         DefaultTableModel model = (DefaultTableModel) btnTable.table.getModel();
         model.setDataVector(rowData, getColumnNames(btnTable.table));
         rowData = (Vector<? extends Vector<?>>) in.readObject(); // cast must match the variable type
         model = (DefaultTableModel) axisTable.table_.getModel();
         model.setDataVector(rowData, getColumnNames(axisTable.table_));

         in.close();

         //need to setup celleditor and renderers again
         btnTable.setupCellEditors();
         axisTable.setupCellEditors();

         ((DefaultTableModel) btnTable.table.getModel()).fireTableDataChanged();
         ((DefaultTableModel) axisTable.table_.getModel()).fireTableDataChanged();


      } catch (Exception ex) {
         studio_.logs().logError(ex);
      }
   }

   /**
    * Column names are always same , so getting them from table itself rather than save file
    *
    * @param table
    * @return column names as vectors
    */
   private Vector<String> getColumnNames(JTable table) {
      Vector<String> columnNames = new Vector<>();
      for (int i = 0; i < table.getColumnCount(); i++) {
         columnNames.add(table.getColumnName(i));
      }
      return columnNames;
   }

   /**
    * The program loop for the plugin , polls the device and acts on actions
    */
   private void timerTick() {

      if (!ctrl.poll()) {
         lGpadStat.setText(gpadNotFound);
         wasDisconnected = true;
         return;
      }

      if (!ctrl.isConnected()) {
         lGpadStat.setText(gpadNotFound);
         wasDisconnected = true;
         return;

      }

      // Yay gamepad found , let the fun stuff begin
      if (wasDisconnected) {
         lGpadStat.setText(gpadConnected);
         wasDisconnected = false;
      }

      // Let's get the deltas
      XInputButtonsDelta btnDelta = ctrl.getDelta().getButtons();
      XInputAxesDelta axisDelta = ctrl.getDelta().getAxes();

      ActionItems actionTodo;
      String actionString;

      int rowIdx;

      // Let's do buttons //////////////////////////////////
      for (rowIdx = 0; rowIdx < btnTable.table.getRowCount(); rowIdx++) {

         //is pressed is denounced, only true once after first press
         if (btnDelta.isPressed((XInputButton) btnTable.table.getValueAt(rowIdx, 0))) {
            //Button pressed execute action
            actionTodo = (ActionItems) btnTable.table.getModel().getValueAt(rowIdx, 1);
            actionString = (String) btnTable.table.getModel().getValueAt(rowIdx, 2);

            ba.executeAction(actionTodo, actionString);
         }

      } //end of for

      // Let's do Axes /////////////////////////////
      float temp;
      XInputAxis ca;
      String devName;
      String propName;
      float mul;

      for (rowIdx = 0; rowIdx < axisTable.table_.getRowCount(); rowIdx++) {

         ca = (XInputAxis) axisTable.table_.getValueAt(rowIdx, 0);
         devName = axisTable.table_.getValueAt(rowIdx, 1).toString();
         propName = axisTable.table_.getValueAt(rowIdx, 2).toString();
         mul = Float.parseFloat(axisTable.table_.getValueAt(rowIdx, 3).toString());

         if (devName.isEmpty() | propName.isEmpty() | mul == 0) {
            continue;
         }

         if (axisDelta.getDelta(ca) != 0) {

            temp = ctrl.getComponents().getAxes().get(ca);
            if (Math.abs(temp) < axisDeadcount) {
               temp = 0;
            }
            temp *= mul;

            try {
               // check if prop has limits

               if (studio_.getCMMCore().hasPropertyLimits(devName, propName)) {
                  double min = studio_.getCMMCore().getPropertyLowerLimit(devName, propName);
                  double max = studio_.getCMMCore().getPropertyUpperLimit(devName, propName);
                  if (temp > max) {
                     temp = (float) max;
                  } else if (temp < min) {
                     temp = (float) min;
                  }
               }

               PropertyType pt = studio_.getCMMCore().getPropertyType(devName, propName);

               if (pt == PropertyType.Integer) {
                  studio_.getCMMCore().setProperty(devName, propName, (int) temp);
               } else if (pt == PropertyType.String) {
                  studio_.getCMMCore().setProperty(devName, propName, Float.toString(temp));
               } else if (pt == PropertyType.Float) {
                  studio_.getCMMCore().setProperty(devName, propName, temp);
               }
            } catch (Exception e) {
               e.printStackTrace();
            }
         }

      } //end of for

   } //end of timer_tick

   public void dispose() {
      //ReportingUtils.logMessage("!!!!closed from main frame!!!!");
      myTimer_.stop();
      myTimer_ = null;
      btnTable = null;
      axisTable = null;
      ctrl = null;
      ba = null;
      super.dispose();
   }

}
