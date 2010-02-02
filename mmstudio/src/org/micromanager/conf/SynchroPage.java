///////////////////////////////////////////////////////////////////////////////
//FILE:          SynchroPage.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nenad Amodaj, nenad@amodaj.com, October 29, 2006
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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Vector;
import java.util.prefs.Preferences;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.border.LineBorder;

/**
 * Wizard page for setting image synchronization device list. 
 *
 */
public class SynchroPage extends PagePanel {
   private static final long serialVersionUID = 1L;
   private JList deviceList_;
   private JList synchroList_;
   private Device[] availableDevices_;
   /**
    * Create the panel
    */
   public SynchroPage(Preferences prefs) {
      super();
      title_ = "Select devices to synchronize with image acquisition";
      helpText_ = "Select devices to be automatically synchronized with the camera.\n" +
      "The camera will wait for each device in the sycnhro list to finish current actions, before taking an image.\n\n" +
      
      "Selecting a large number of devices for the sycnhro list may degrade the peformance of the system." +
      "Select only devices that are likely to interfere with imaging, such as filter wheels and stages.";
      prefs_ = prefs;
      setHelpFileName("conf_synchro_page.html");
      setLayout(null);
      
      synchroList_ = new JList();
      synchroList_.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      synchroList_.setBorder(new LineBorder(Color.black, 1, false));
      synchroList_.setBounds(10, 33, 164, 244);
      add(synchroList_);
      
      deviceList_ = new JList();
      deviceList_.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

      JScrollPane scrollPane = new JScrollPane();
      scrollPane.setBounds(320, 33, 164, 244);

      scrollPane.getViewport().setView(deviceList_);


      add(scrollPane);
      
      final JButton button = new JButton();
      button.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            Object sel = deviceList_.getSelectedValue();
            if (sel == null)
               return;
            addSynchro((String)sel);
         }
      });
      button.setText("<< Add");
      button.setBounds(181, 108, 133, 23);
      add(button);
      
      final JButton removeButton = new JButton();
      removeButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            Object sel = synchroList_.getSelectedValue();
            if (sel == null)
               return;
            removeSynchro((String)sel);
         }
      });
      removeButton.setText("Remove >>");
      removeButton.setBounds(181, 137, 133, 23);
      add(removeButton);
      
      final JLabel synchronizedDevicesLabel = new JLabel();
      synchronizedDevicesLabel.setText("Synchronized devices");
      synchronizedDevicesLabel.setBounds(10, 13, 157, 14);
      add(synchronizedDevicesLabel);
      
      final JLabel availabledevicesLabel = new JLabel();
      availabledevicesLabel.setText("Available Devices");
      availabledevicesLabel.setBounds(320, 13, 172, 14);
      add(availabledevicesLabel);
      //
   }
   
   public boolean enterPage(boolean next) {
      String synchro[] = model_.getSynchroList(); 
      Vector<String> synchroData = new Vector<String>();
      for (int i=0; i<synchro.length; i++)
         synchroData.add(synchro[i]);
      
      synchroList_.setListData(synchroData);
      
      availableDevices_ = model_.getDevices();
      Vector<String> listData = new Vector<String>();
      for (int i=0; i<availableDevices_.length; i++)
         if (!isInSynchroList(availableDevices_[i].getName()) && !availableDevices_[i].isCore() && !availableDevices_[i].isCamera())
            listData.add(availableDevices_[i].getName());
      
      deviceList_.setListData(listData);
      
      return true;
   }
   
   public boolean exitPage(boolean next) {
      model_.clearSynchroDevices();
      for (int i=0; i<synchroList_.getModel().getSize(); i++) {
         model_.addSynchroDevice((String)synchroList_.getModel().getElementAt(i));
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
   
   private boolean isInSynchroList(String devName) {
      for (int i=0; i<synchroList_.getModel().getSize(); i++)
         if(((String)synchroList_.getModel().getElementAt(i)).compareTo(devName) == 0)
            return true;
      return false;
   }
   
   private void addSynchro(String name) {
      // add to synchro list
      Vector<Object> data = new Vector<Object>();
      for (int i=0; i<synchroList_.getModel().getSize(); i++)
         data.add(synchroList_.getModel().getElementAt(i));
      data.add(name);
      synchroList_.setListData(data);     
      
      // remove from device list
      Vector<Object> devData = new Vector<Object>();
      for (int i=0; i<deviceList_.getModel().getSize(); i++)
         if (name.compareTo((String)deviceList_.getModel().getElementAt(i)) != 0)
            devData.add(deviceList_.getModel().getElementAt(i));
      deviceList_.setListData(devData);     
   }
   
   private void removeSynchro(String name) {
      // add to synchro list
      Vector<Object> data = new Vector<Object>();
      for (int i=0; i<deviceList_.getModel().getSize(); i++)
         data.add(deviceList_.getModel().getElementAt(i));
      data.add(name);
      deviceList_.setListData(data);     
      
      // remove from device list
      Vector<Object> syncData = new Vector<Object>();
      for (int i=0; i<synchroList_.getModel().getSize(); i++)
         if (name.compareTo((String)synchroList_.getModel().getElementAt(i)) != 0)
            syncData.add(synchroList_.getModel().getElementAt(i));
      synchroList_.setListData(syncData);     
   }
   
}
