package org.micromanager.plugins.fluidcontrol;

import javax.swing.JPanel;
import org.micromanager.Studio;

public class VolumeControlPanel extends JPanel {
   private Studio studio_;
   private Config config_;

   private int nSelected;
   private VolumeControlSubPanel[] panelList;
   private String[] devices_;


   VolumeControlPanel(Studio studio, String[] devices) {
      this.studio_ = studio;
      this.devices_ = devices;

      nSelected = devices.length;
      panelList = new VolumeControlSubPanel[nSelected];
      for (int i = 0; i < nSelected; i++) {
         panelList[i] = new VolumeControlSubPanel(studio_, devices[i]);
         this.add(panelList[i]);
      }
   }
}
