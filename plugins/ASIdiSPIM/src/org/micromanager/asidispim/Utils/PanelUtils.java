///////////////////////////////////////////////////////////////////////////////
//FILE:          PanelUtils.java
//PROJECT:       Micro-Manager 
//SUBSYSTEM:     ASIdiSPIM plugin
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman, Jon Daniels
//
// COPYRIGHT:    University of California, San Francisco, & ASI, 2013
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

package org.micromanager.asidispim.Utils;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.prefs.Preferences;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JSlider;
import org.micromanager.asidispim.Data.Devices;

/**
 *
 * @author nico
 */
public class PanelUtils {
   
   /**
    * Listener for Selection boxes that attach joysticks to drives
    */
   public class StageSelectionBoxListener implements ActionListener, DevicesListenerInterface {
      Devices.JoystickDevice joystickDevice_;
      JComboBox jc_;
      Devices devices_;
      Preferences prefs_;
      String prefName_;

      public StageSelectionBoxListener(Devices.JoystickDevice joyStickDevice, 
              JComboBox jc, Devices devices, Preferences prefs, String prefName) {
         joystickDevice_ = joyStickDevice;
         jc_ = jc;
         devices_ = devices;
         prefs_ = prefs;
         prefName_ = prefName;
      }    

      /**
       * This will be called whenever the user selects a new item, but also
       * when the tab to which this selectionbox belongs is selected
       * @param ae 
       */
      public void actionPerformed(ActionEvent ae) {
                  String stage = (String) jc_.getSelectedItem();
         String[] items = stage.split("-");
         DirectionalDevice dd;
         if (items.length > 1) {
            dd = new DirectionalDevice(items[0], 
                    Labels.REVDIRECTIONS.get(items[1]));
         } else {
            dd = new DirectionalDevice(items[0], Labels.Directions.X);
         }
         devices_.setJoystickOutput(joystickDevice_, dd);
         prefs_.put(prefName_, stage);
      }

      public void devicesChangedAlert() {
         jc_.removeAllItems();
         String[] devices;
         if (joystickDevice_ == Devices.JoystickDevice.JOYSTICK) { 
            devices = devices_.getTwoAxisTigerStages();
         } else {
            devices = devices_.getTigerStages();
         }
         for (String device : devices) {
            jc_.addItem(device);
         }              
      }

   }
   
   
   public JComboBox makeJoystickSelectionBox(Devices.JoystickDevice joystickDevice, 
           String[] selections, String selectedItem, Devices devices_, 
           Preferences prefs, String prefName) {
      JComboBox jcb = new JComboBox(selections);
      jcb.setSelectedItem(selectedItem);  
      jcb.addActionListener(new StageSelectionBoxListener(joystickDevice , jcb, 
              devices_, prefs, prefName));
 
      return jcb;
   }
   
   public JSlider makeSlider(String name, int min, int max, int init) {
      JSlider js = new JSlider(JSlider.HORIZONTAL, min, max, init);
      js.setMajorTickSpacing(max - min);
      js.setMinorTickSpacing(1);
      js.setPaintTicks(true);
      js.setPaintLabels(true);

      return js;
   }

   /**
    * Constructs the JCheckBox through which the user can select sides
    * @param fastAxisDir name under which this axis is known in the Devices class
    * @return constructed JCheckBox
    */
   public JCheckBox makeCheckBox(String name, Labels.Sides side) {
      JCheckBox jc = new JCheckBox("Side " + Labels.SIDESMAP.get(side));
      //jc.setSelected(devices_.getFastAxisRevInfo(fastAxisDir));
      //jc.addActionListener(new DevicesPanel.ReverseCheckBoxListener(fastAxisDir, jc));
      
      return jc;
   }
   
}
