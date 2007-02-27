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

import java.awt.Component;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.Vector;
import java.util.prefs.Preferences;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.table.TableColumn;

/**
 * Config Wizard COM ports page.
 * Select and configure COM ports.
 */
public class ComPortsPage extends PagePanel {

   private JTable portTable_;
   private JList portList_;
   private static final String HELP_FILE_NAME = "conf_comport_page.html";

   class CBListCellRenderer extends JCheckBox implements ListCellRenderer {

      // This is the only method defined by ListCellRenderer.
      // We just reconfigure the JLabel each time we're called.

      public Component getListCellRendererComponent(
            JList list,
            Object value,            // value to display
            int index,               // cell index
            boolean isSelected,      // is the cell selected
            boolean cellHasFocus)    // the list and the cell have the focus
      {
         String s = ((JCheckBox)value).getText();
         setSelected(((JCheckBox)value).isSelected());
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
      portList_.setBounds(10, 44, 157, 90);
      add(portList_);
      portList_.setCellRenderer(new CBListCellRenderer());
      
      MouseListener mouseListener = new MouseAdapter() {
         public void mouseClicked(MouseEvent e) {
             if (e.getClickCount() == 1) {
                 int index = portList_.locationToIndex(e.getPoint());
                 JCheckBox box = (JCheckBox)(portList_.getModel().getElementAt(index));
                 box.setSelected(!box.isSelected());
                 model_.useSerialPort(index, box.isSelected());
                 System.out.println("Item " + index + " clicked");
                 portList_.repaint();
              }
             
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
      scrollPane.setViewportView(portTable_);

      final JLabel usePortsLabel = new JLabel();
      usePortsLabel.setText("Use ports:");
      usePortsLabel.setBounds(10, 23, 157, 14);
      add(usePortsLabel);
      //
   }

   public boolean enterPage(boolean next) {
      model_.removeDuplicateComPorts();
      Device ports[] = model_.getAvailableSerialPorts();
      
      Vector portsVect = new Vector();
      for (int i=0; i<ports.length; i++) {
         JCheckBox box = new JCheckBox(ports[i].getAdapterName());
         box.setSelected(model_.isPortInUse(i));
         portsVect.add(box);
      }
      
      portList_.setListData(portsVect);
      rebuildTable();
      
      return true;
  }

   public boolean exitPage(boolean toNextPage) {
      try {
         if (toNextPage) {
            // apply the properties
            PropertyTableModel ptm = (PropertyTableModel)portTable_.getModel();
            for (int i=0; i<ptm.getRowCount(); i++) {
               Setting s = ptm.getSetting(i);
               core_.setProperty(s.deviceName_, s.propertyName_, s.propertyValue_);
               Device dev = model_.findSerialPort(s.deviceName_);
               Property prop = dev.findSetupProperty(s.propertyName_);
               if (prop == null)
                  dev.addSetupProperty(new Property(s.propertyName_, s.propertyValue_, true));
               else
                  prop.value_ = s.propertyValue_;
            }
         }
      } catch (Exception e) {
         handleException(e);
         if (toNextPage)
            return false;
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
   
   private void rebuildTable() {
      
      // load com ports
     try {
         core_.unloadAllDevices();
         Device ports[] = model_.getAvailableSerialPorts();
         for (int i=0; i<ports.length; i++)
            if (model_.isPortInUse(ports[i])) {
               core_.loadDevice(ports[i].getAdapterName(), ports[i].getLibrary(), ports[i].getAdapterName());
               // apply setup properties
               for (int j=0; j<ports[i].getNumberOfSetupProperties(); j++) {
                  Property p = ports[i].getSetupProperty(j);
                  core_.setProperty(ports[i].getName(), p.name_, p.value_);
               }
               
               ports[i].loadDataFromHardware(core_);
            }
       } catch (Exception e) {
         handleException(e);
      }
      
      PropertyTableModel tm = new PropertyTableModel(this, model_, PropertyTableModel.COMPORT);
      portTable_.setModel(tm);
      PropertyCellEditor cellEditor = new PropertyCellEditor();
      PropertyCellRenderer renderer = new PropertyCellRenderer();
     
      if (portTable_.getColumnCount() == 0) {
         for (int k=0; k < tm.getColumnCount(); k++) {
            TableColumn column = new TableColumn(k, 200, renderer, cellEditor);
            portTable_.addColumn(column);
         }
      }

      tm.fireTableStructureChanged();
      portTable_.repaint();
   }
}
