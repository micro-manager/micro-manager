package org.micromanager.plugins.fluidcontrol;

import java.awt.event.ActionListener;
import java.util.Arrays;
import javax.swing.JPanel;
import javax.swing.Timer;
import mmcorej.DeviceType;
import net.miginfocom.swing.MigLayout;
import org.micromanager.Studio;

public class FluidControlPanel extends JPanel {
   private static final int DELAY = 1000; // Delay for requesting new pressure in ms

   private final Studio studio_;
   private Config config_;
   private PressureControlPanel pressurePanel;
   private VolumeControlPanel volumePanel;

   private ActionListener updateTask;
   private Timer timer;

   FluidControlPanel(Studio studio, Config config) {
      super.setLayout(new MigLayout("fill, insets 2, gap 2, flowx"));

      this.studio_ = studio;
      this.config_ = config;

      // Initialize all devices
      try {
         config_.pressurePumpSelected.clear();
         config_.pressurePumpSelected.addAll(
               Arrays.asList(studio_.core()
                     .getLoadedDevicesOfType(DeviceType.PressurePumpDevice)
                     .toArray()
               )
         );
         config_.volumePumpSelected.clear();
         config_.volumePumpSelected.addAll(
               Arrays.asList(studio_.core()
                     .getLoadedDevicesOfType(DeviceType.VolumetricPumpDevice)
                     .toArray()
               )
         );
      } catch (Exception e) {
         studio_.getLogManager().logError(e);
         return;
      }

      pressurePanel =
            new PressureControlPanel(studio_, config_.pressurePumpSelected.toArray(new String[0]));
      this.add(pressurePanel);
      volumePanel =
            new VolumeControlPanel(studio_, config_.volumePumpSelected.toArray(new String[0]));
      this.add(volumePanel);

      initializeUpdater();
      startUpdater();
   }

   private void initializeUpdater() {
      updateTask = evt -> {
         pressurePanel.update();
         // Volume pumps don't need to be updated
      };
      timer = new Timer(DELAY, updateTask);
   }

   /**
    * Starts automatic pressure updater. Requests the pressure of each channel every
    * DELAY milliseconds. DELAY is currently fixed to 1000 ms, but might be
    * made a setting in the future.
    */
   public void startUpdater() {
      if (!timer.isRunning()) {
         timer.start();
      }
   }

   /**
    * Stops automatic pressure updater. Requests the pressure of each channel every
    * DELAY milliseconds. DELAY is currently fixed to 1000 ms, but might be
    * made a setting in the future.
    */
   public void stopUpdater() {
      if (timer.isRepeats()) {
         timer.stop();
      }
   }
}
