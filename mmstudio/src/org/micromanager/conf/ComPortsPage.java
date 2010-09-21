///////////////////////////////////////////////////////////////////////////////
//FILE:          ComPortsPage.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nenad Amodaj, nenad@amodaj.com, November 12, 2006
//
// COPYRIGHT:    University of California, San Francisco, 2006
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
//
// CVS:          $Id$
//
package org.micromanager.conf;

import java.awt.Color;
import java.awt.Component;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Vector;
import java.util.prefs.Preferences;

import javax.swing.InputMap;
import javax.swing.JToggleButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.border.LineBorder;
import javax.swing.table.TableColumn;
import mmcorej.MMCoreJ;
import org.micromanager.utils.GUIUtils;
import org.micromanager.utils.PropertyItem;
import org.micromanager.utils.PropertyNameCellRenderer;
import org.micromanager.utils.PropertyValueCellEditor;
import org.micromanager.utils.PropertyValueCellRenderer;
//import org.apache.commons.collections;

/**
 * Config Wizard COM ports page.
 * Select and configure COM ports.
 */
public class ComPortsPage extends PagePanel {

   private static final long serialVersionUID = 1L;
   private JTable portTable_;
   private JList portList_;
   private static final String HELP_FILE_NAME = "conf_comport_page.html";

   class CBListCellRenderer extends JToggleButton implements ListCellRenderer {

      private static final long serialVersionUID = 1L;

      public Component getListCellRendererComponent(
              JList list,
              Object value, // value to display
              int index, // cell index
              boolean isSelected, // is the cell selected
              boolean cellHasFocus) // the list and the cell have the focus
      {
         String s = ((JToggleButton) value).getText();
         setSelected(((JToggleButton) value).isSelected());
         setText(s);
         setBackground(list.getBackground());
         setForeground(list.getForeground());
         setEnabled(list.isEnabled());
         setFont(list.getFont());
         setOpaque(true);
         return this;
      }
   }

   /**
    * Create the panel
    */
   public ComPortsPage(Preferences prefs) {
      super();
      title_ = "Setup COM ports";
      prefs_ = prefs;
      setLayout(null);
      setHelpFileName(HELP_FILE_NAME);

      portList_ = new JList();
      portList_.setBorder(new LineBorder(Color.black, 1, false));
      portList_.setBackground(Color.LIGHT_GRAY);
      portList_.setBounds(10, 44, 157, 194);
      add(portList_);
      portList_.setCellRenderer(new CBListCellRenderer());

      MouseListener mouseListener = new MouseAdapter() {

         public void mouseClicked(MouseEvent e) {
            // show user all serial ports
           /* if (e.getClickCount() == 1) {
               int nSerialPorts = portList_.getModel().getSize();
               if (1 < nSerialPorts) {
                  // only need to change selection if more than one serial port is configured
                  int index = portList_.locationToIndex(e.getPoint());
                  JToggleButton box = (JToggleButton) (portList_.getModel().getElementAt(index));
                  if (!box.isSelected()) {
                     // if the user clicked on a serial port other than the one already displayed
                     // de-select any currently selected port
                     for (int iport = 0; iport < nSerialPorts; ++iport) {
                        JToggleButton otherBox = (JToggleButton) (portList_.getModel().getElementAt(iport));
                        if (otherBox.isSelected()) {
                           otherBox.setSelected(false);
                           //model_.useSerialPort(iport, false);
                        }
                     }
                     // now select the box the user clicked
                     box.setSelected(true);
                     //model_.useSerialPort(index, box.isSelected());
                     portList_.repaint();
                  }
               }
            }*/
            rebuildTable();
         }
      };
      portList_.addMouseListener(mouseListener);

      final JScrollPane scrollPane = new JScrollPane();
      scrollPane.setBounds(173, 23, 397, 215);
      add(scrollPane);

      portTable_ = new JTable();
      portTable_.setAutoCreateColumnsFromModel(false);
      portTable_.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      InputMap im = portTable_.getInputMap(JTable.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
      im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "none");

      scrollPane.setViewportView(portTable_);

      final JLabel usePortsLabel = new JLabel();
      usePortsLabel.setText("Port Setting for Device:");
      usePortsLabel.setBounds(10, 23, 357, 14);
      add(usePortsLabel);
      //
   }

   public boolean enterPage(boolean next) {
		if( next){
			try{
			core_.unloadAllDevices();
			}
			catch(Exception u){
			}
		}
      model_.removeDuplicateComPorts();
      Device ports[] = model_.getAvailableSerialPorts();

      for (Device d : ports) {
         try {
            d.loadDataFromHardware(core_);
         } catch (Exception e) {
            handleError(e.getMessage());
         }
      }

      //map the serial ports corresponding to the other devices
      HashMap<String, ArrayList<String>> portUse = new HashMap<String, ArrayList<String>>();

      Device devices[] = model_.getDevices();
      for (int i = 0; i < devices.length; i++) {
         for (int j = 0; j < devices[i].getNumberOfProperties(); j++) {
            PropertyItem p = devices[i].getProperty(j);
            if (p.name.compareTo(MMCoreJ.getG_Keyword_Port()) == 0) {
               // add this device to the devices controlled by this serial port
               ArrayList<String> devicesHere = portUse.get(p.value);
               if (null == devicesHere) {
                  portUse.put(p.value, devicesHere = new ArrayList<String>());
               }
               devicesHere.add(devices[i].getName());
            }
         }
      }

      Vector<JToggleButton> portsVect = new Vector<JToggleButton>();
      int listOffset = 0;
      for (Integer i = 0; i < ports.length; i++) {
         if (model_.isPortInUse(ports[i])) {
            ArrayList<String> ds = portUse.get(ports[i].getAdapterName());
            String value = ports[i].getAdapterName();
            if (null != ds) {
               // if any devices use this serial port, put that name on the button rather than the serial port name
               value = "";
               for (String s : ds) {
                  if( 0 < value.length() )
                     value += " ";
                  value += s;
               }
            }
            JToggleButton box = new JToggleButton(value);
            box.setSelected(true);
            ++listOffset;
            portsVect.add(box);
         }
      }

      portList_.setListData(portsVect);
      rebuildTable();

      return true;
   }

   public boolean exitPage(boolean toNextPage) {
      try {
         if (toNextPage) {
//            reloadDevices();
            // apply the properties
            PropertyTableModel ptm = (PropertyTableModel) portTable_.getModel();
            for (int i = 0; i < ptm.getRowCount(); i++) {
               Setting s = ptm.getSetting(i);
               core_.setProperty(s.deviceName_, s.propertyName_, s.propertyValue_);
               Device dev = model_.findSerialPort(s.deviceName_);
               PropertyItem prop = dev.findSetupProperty(s.propertyName_);
               if (prop == null) {
                  dev.addSetupProperty(new PropertyItem(s.propertyName_, s.propertyValue_, true));
               } else {
                  prop.value = s.propertyValue_;
               }
            }
            // load com ports
            Device ports[] = model_.getAvailableSerialPorts();
            for (int i = 0; i < ports.length; i++) {
               if (model_.isPortInUse(ports[i])) {
                  // core_.loadDevice(ports[i].getName(), ports[i].getLibrary(), ports[i].getAdapterName());

                  // serial ports are associated with devices so we can now set the COM port settings
                  Device d = model_.findSerialPort(ports[i].getName());
                  for (int j = 0; j < d.getNumberOfSetupProperties(); j++) {
                     PropertyItem prop = d.getSetupProperty(j);
                     core_.setProperty(d.getName(), prop.name, prop.value);
                  }
               }
            }

            // initialize the entire system
            core_.initializeAllDevices();
            GUIUtils.preventDisplayAdapterChangeExceptions();
            model_.loadDeviceDataFromHardware(core_);

         } else {
            //core_.unloadAllDevices();
            GUIUtils.preventDisplayAdapterChangeExceptions();

         }
      } catch (Exception e) {
         handleException(e);
         if (toNextPage) {
            return false;
         }
      }
      return true;
   }

   public void refresh() {
   }

   public void loadSettings() {
      // TODO Auto-generated method stub
   }

   public void saveSettings() {
      // TODO Auto-generated method stub
   }

   private void reloadDevices() {
      // load com ports
      try {

         core_.unloadAllDevices();
         Device ports[] = model_.getAvailableSerialPorts();

         // load the serial ports
         for (int i = 0; i < ports.length; i++) {
            if (model_.isPortInUse(ports[i])) {
               core_.loadDevice(ports[i].getAdapterName(), ports[i].getLibrary(), ports[i].getAdapterName());
               // apply setup properties
               for (int j = 0; j < ports[i].getNumberOfSetupProperties(); j++) {
                  PropertyItem p = ports[i].getSetupProperty(j);
                  core_.setProperty(ports[i].getName(), p.name, p.value);
               }

            }
         }

         // load devices
         Device devs[] = model_.getDevices();
         for (int i = 0; i < devs.length; i++) {
            if (!devs[i].isCore()) {
               core_.loadDevice(devs[i].getName(), devs[i].getLibrary(), devs[i].getAdapterName());
            }
         }

         // set the properties into this (fresh) copy of the device adapters



         model_.removeDuplicateComPorts();
         model_.loadDeviceDataFromHardware(core_);
      } catch (Exception e) {
         handleException(e);
      }
   }

   private void rebuildTable() {

//      reloadDevices();

      PropertyTableModel tm = new PropertyTableModel(this, model_, PropertyTableModel.COMPORT);
      portTable_.setModel(tm);
      PropertyValueCellEditor propValueEditor = new PropertyValueCellEditor();
      PropertyValueCellRenderer propValueRenderer = new PropertyValueCellRenderer();
      PropertyNameCellRenderer propNameRenderer = new PropertyNameCellRenderer();


      if (portTable_.getColumnCount() == 0) {
         TableColumn column;
         column = new TableColumn(0, 200, propNameRenderer, null);
         portTable_.addColumn(column);
         column = new TableColumn(1, 200, propNameRenderer, null);
         portTable_.addColumn(column);
         column = new TableColumn(2, 200, propValueRenderer, propValueEditor);
         portTable_.addColumn(column);

      }

      tm.fireTableStructureChanged();
      // tm.fire
      portTable_.repaint();
   }

}
