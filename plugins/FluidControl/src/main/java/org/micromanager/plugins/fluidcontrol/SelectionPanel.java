package org.micromanager.plugins.fluidcontrol;

import java.awt.Window;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Arrays;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import mmcorej.DeviceType;
import net.miginfocom.swing.MigLayout;
import org.micromanager.Studio;

public class SelectionPanel extends JPanel {
   private Studio studio_;
   private Config config_;

   private ArrayList<String> pressurePumps;
   private ArrayList<String> pressurePumpsSelected;
   private final JCheckBox[] pressurePumpToggles;
   private final JLabel[] pressurePumpDescriptions;

   private ArrayList<String> volumePumps;
   private ArrayList<String> volumePumpsSelected;
   private final JCheckBox[] volumePumpToggles;
   private final JLabel[] volumePumpDescriptions;

   private ActionListener confirmAction;
   public JButton confirmButton;

   SelectionPanel(Studio studio, Config config) {
      this.setLayout(new MigLayout("wrap 3"));

      studio_ = studio;
      config_ = config;
      pressurePumps = new ArrayList<>();
      volumePumps = new ArrayList<>();
      pressurePumpsSelected = new ArrayList<>();
      volumePumpsSelected = new ArrayList<>();
      getAllFluidDevices();

      pressurePumpToggles = new JCheckBox[pressurePumps.size()];
      pressurePumpDescriptions = new JLabel[pressurePumps.size()];
      for (int i = 0; i < pressurePumps.size(); i++) {
         pressurePumpToggles[i] =
               new JCheckBox("", pressurePumpsSelected.contains(pressurePumps.get(i)));
         pressurePumpDescriptions[i] = new JLabel(pressurePumps.get(i));
      }

      volumePumpToggles = new JCheckBox[volumePumps.size()];
      volumePumpDescriptions = new JLabel[volumePumps.size()];
      for (int i = 0; i < volumePumps.size(); i++) {
         volumePumpToggles[i] = new JCheckBox("", volumePumpsSelected.contains(volumePumps.get(i)));
         volumePumpDescriptions[i] = new JLabel(volumePumps.get(i));
      }

      confirmButton = new JButton("Confirm");
      addConfirmAction();

      drawComponents();
   }

   private void addConfirmAction() {
      confirmAction = e -> {
         updateSelected();
         config_.pressurePumpSelected = pressurePumpsSelected;
         config_.volumePumpSelected = volumePumpsSelected;
         config_.setProperty("hasChanged", true);

         Window win = SwingUtilities.getWindowAncestor((JComponent) e.getSource());
         win.dispose();
      };
      confirmButton.addActionListener(confirmAction);
   }

   private void drawComponents() {
      for (int i = 0; i < pressurePumps.size(); i++) {
         this.add(pressurePumpDescriptions[i]);
         this.add(pressurePumpToggles[i], "wrap");
      }
      for (int i = 0; i < volumePumps.size(); i++) {
         this.add(volumePumpDescriptions[i]);
         this.add(volumePumpToggles[i], "wrap");
      }
      this.add(confirmButton);
   }

   private void getAllFluidDevices() {
      pressurePumps.clear();
      volumePumps.clear();
      try {
         pressurePumps
               .addAll(Arrays.asList(studio_
                     .core()
                     .getLoadedDevicesOfType(DeviceType.PressurePumpDevice)
                     .toArray()));
         volumePumps
               .addAll(Arrays.asList(studio_
                     .core()
                     .getLoadedDevicesOfType(DeviceType.VolumetricPumpDevice)
                     .toArray()));
      } catch (Exception e) {
         studio_.getLogManager().logMessage("Could not retrieve Pump labels.");
         studio_.getLogManager().logError(e);
      }
   }

   private void updateSelected() {
      pressurePumpsSelected.clear();
      for (int i = 0; i < pressurePumps.size(); i++) {
         if (pressurePumpToggles[i].isSelected()) {
            pressurePumpsSelected.add(pressurePumps.get(i));
         }
      }
      volumePumpsSelected.clear();
      for (int i = 0; i < volumePumps.size(); i++) {
         if (volumePumpToggles[i].isSelected()) {
            volumePumpsSelected.add(volumePumps.get(i));
         }
      }
   }
}