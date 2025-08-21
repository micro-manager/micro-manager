package org.micromanager.plugins.fluidcontrol;

import javax.swing.JLabel;
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

      panelList = new PressureControlSubPanel[devices.length];
      for (int i = 0; i < devices.length; i++) {
         panelList[i] = new PressureControlSubPanel(studio_, devices[i]);
         this.add(panelList[i]);
      }
      if (devices.length == 0) {
         this.add(new JLabel("No PressurePumps found"));
      }
   }

   public void update() {
      for (PressureControlSubPanel panel : panelList) {
         panel.updatePressure();
      }
   }
}
