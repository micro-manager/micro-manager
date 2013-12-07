///////////////////////////////////////////////////////////////////////////////
//FILE:          NavigationPanel.java
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

package org.micromanager.asidispim;

import org.micromanager.asidispim.Data.Devices;
import org.micromanager.asidispim.Utils.ListeningJPanel;
import org.micromanager.asidispim.Utils.PanelUtils;
import javax.swing.JLabel;
import net.miginfocom.swing.MigLayout;

/**
 *
 * @author nico
 */
public class NavigationPanel extends ListeningJPanel {
   Devices devices_;
   
   public NavigationPanel(Devices devices) {    
      super (new MigLayout(
              "", 
              "[right]8[align center]16[right]8[center]8[center]8[center]",
              "[]16[]"));
      devices_ = devices;
       
      PanelUtils pu = new PanelUtils();
       
      add(new JLabel("Joystick:"));
      add(pu.makeJoystickSelectionBox(Devices.JoystickDevice.JOYSTICK, 
               devices_.getTwoAxisTigerDrives(), devices_), "wrap");
       
      add(new JLabel("Right knob:"));
      add(pu.makeJoystickSelectionBox(Devices.JoystickDevice.RIGHT_KNOB, 
               devices_.getTigerDrives(), devices_), "wrap");
       
      add(new JLabel("Left knob:"));
      add(pu.makeJoystickSelectionBox(Devices.JoystickDevice.LEFT_KNOB,
               devices_.getTigerDrives(), devices_), "wrap");
      
   }
   
}
