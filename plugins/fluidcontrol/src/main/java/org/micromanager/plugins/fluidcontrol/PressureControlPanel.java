package org.micromanager.plugins.fluidcontrol;

import javax.swing.JPanel;
import org.micromanager.Studio;

public class PressureControlPanel extends JPanel {
   private Studio studio_;
   private Config config_;

   private int nSelected;
   private PressureControlSubPanel[] panelList;
   private String[] devices_;

   PressureControlPanel(Studio studio, String[] devices) {
      this.studio_ = studio;
      this.devices_ = devices;

      nSelected = devices.length;
      panelList = new PressureControlSubPanel[nSelected];
      for (int i = 0; i < nSelected; i++) {
         panelList[i] = new PressureControlSubPanel(studio_, devices[i]);
         this.add(panelList[i]);
      }
   }

   public void update() {
      for (PressureControlSubPanel panel : panelList) {
         panel.updatePressure();
      }
   }
}
