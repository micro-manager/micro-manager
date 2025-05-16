package org.micromanager.plugins.fluidcontrol;

import javax.swing.JPanel;
import org.micromanager.Studio;

/**
 * Panel that controls all pressure pumps.
 */
public class PressureControlPanel extends JPanel {
   private final Studio studio_;
   private Config config_;

   private final int nSelected;
   private final PressureControlSubPanel[] panelList;
   private final String[] devices_;

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

   /**
    * Updater for measured pressure. Is called by FluidControlPanel at regular intervals.
    */
   public void update() {
      for (PressureControlSubPanel panel : panelList) {
         panel.updatePressure();
      }
   }
}
