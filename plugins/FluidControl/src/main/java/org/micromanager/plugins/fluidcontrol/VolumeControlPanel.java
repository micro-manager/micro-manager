package org.micromanager.plugins.fluidcontrol;

import javax.swing.JLabel;
import javax.swing.JPanel;
import org.micromanager.Studio;

public class VolumeControlPanel extends JPanel {

   VolumeControlPanel(Studio studio, String[] devices) {
      VolumeControlSubPanel[] panelList = new VolumeControlSubPanel[devices.length];
      for (int i = 0; i < devices.length; i++) {
         panelList[i] = new VolumeControlSubPanel(studio, devices[i]);
         this.add(panelList[i]);
      }
      if (devices.length == 0) {
         this.add(new JLabel("No Volumetric pumps found."));
      }
   }
}
