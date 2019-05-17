///////////////////////////////////////////////////////////////////////////////
// AUTHOR:       Henry Pinkard, henry.pinkard@gmail.com
//
// COPYRIGHT:    University of California, San Francisco, 2015
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
package org.micromanager.magellan.acq;

import org.micromanager.magellan.gui.GUI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
import org.micromanager.magellan.main.Magellan;
import org.micromanager.magellan.misc.Log;

/**
 *
 * @author Henry
 */
public class AcquisitionsManager {

   private ArrayList<MagellanGUIAcquisitionSettings> acqSettingsList_ = new ArrayList<MagellanGUIAcquisitionSettings>();

   private String[] acqStatus_;
   private GUI gui_;
   private volatile MagellanGUIAcquisition currentAcq_;
   private volatile int currentAcqIndex_;
   private ExecutorService acqManageExecuterService_;
   ArrayList<Future> acqFutures_;

   public AcquisitionsManager(GUI gui) {
      gui_ = gui;
      acqSettingsList_.add(new MagellanGUIAcquisitionSettings());
      acqManageExecuterService_ = Executors.newSingleThreadScheduledExecutor((Runnable r) -> new Thread(r, "Acquisition manager thread"));
   }

   public MagellanGUIAcquisitionSettings getAcquisitionSettings(int index) {
      if (index < acqSettingsList_.size()) {
         return acqSettingsList_.get(index);
      }
      return null;
   }

   public int getNumberOfAcquisitions() {
      return acqSettingsList_.size();
   }

   public String getAcquisitionName(int index) {
      return acqSettingsList_.get(index).name_;
   }

   public String setAcquisitionName(int index, String newName) {
      return acqSettingsList_.get(index).name_ = newName;
   }

   /**
    * change in position of selected acq
    */
   public int moveUp(int index) {
      acqStatus_ = null;
      if (index == 0) {
         //nothing to do
         return 0;
      } else {
         acqSettingsList_.add(index - 1, acqSettingsList_.remove(index));
         return -1;
      }
   }

   public int moveDown(int index) {
      acqStatus_ = null;
      if (index == acqSettingsList_.size() - 1) {
         //nothing to do
         return 0;
      } else {
         acqSettingsList_.add(index + 1, acqSettingsList_.remove(index));
         return 1;
      }
   }

   public void addNew() {
      acqStatus_ = null;
      acqSettingsList_.add(new MagellanGUIAcquisitionSettings());
   }

   public void remove(int index) {
      acqStatus_ = null;
      //must always have at least one acquisition
      if (index != -1 && acqSettingsList_.size() > 1) {
         acqSettingsList_.remove(index);
      }
   }

   public void abort() {
      int result = JOptionPane.showConfirmDialog(null, "Abort current acquisition and cancel future ones?", "Finish acquisitions?", JOptionPane.OK_CANCEL_OPTION);
      if (result != JOptionPane.OK_OPTION) {
         return;
      }
      //stop future acquisitions
      for (Future f : acqFutures_) {
         f.cancel(true);
      }
      //mark them as aborted
      for (int i = currentAcqIndex_; i < acqStatus_.length; i++) {
         acqStatus_[i] = "Cancelled";
      }
      //abort current acquisition
      if (currentAcq_ != null) {
         currentAcq_.abort();
      }

   }

   public void runAllAcquisitions() {
      for (MagellanGUIAcquisitionSettings settings : acqSettingsList_) {
         try {
            validateSettings(settings);
         } catch (Exception ex) {
           JOptionPane.showMessageDialog(gui_, "Problem with acquisition settings: \n" + ex.getMessage());
           return;
         }
      }
      
      //submit initialization events
      acqManageExecuterService_.submit(() -> {
         //TODO: once an API, disable adding and deleting of acquisitions here or just copy the list 
         gui_.enableMultiAcquisitionControls(false); //disallow changes while running
         acqStatus_ = new String[acqSettingsList_.size()];
         Arrays.fill(acqStatus_, "Waiting");
         gui_.repaint();
      });

      //submit acquisition events
      acqFutures_ = new ArrayList<Future>();
      for (int acqIndex = 0; acqIndex < acqSettingsList_.size(); acqIndex++) {
         final int index = acqIndex;
         MagellanGUIAcquisitionSettings settings = acqSettingsList_.get(index);
         acqFutures_.add(acqManageExecuterService_.submit(() -> {
            acqStatus_[index] = "Running";
            gui_.acquisitionRunning(true);
            try {
               currentAcq_ = new MagellanGUIAcquisition(settings);
               currentAcqIndex_ = index;
               boolean aborted = !currentAcq_.waitForCompletion();
               acqStatus_[index] = aborted ? "Aborted" : "Complete";
            } catch (Exception e) {
               acqStatus_[index] = "Error";
               e.printStackTrace();
               Log.log(e);
            }
            gui_.acquisitionRunning(false);
            gui_.acquisitionSettingsChanged(); //so that the available disk space label updates
            gui_.repaint();
         }));
      }
      //submit finishing events
      acqManageExecuterService_.submit(() -> {
         //run acquisitions
         gui_.enableMultiAcquisitionControls(true);
      });
   }

   public String getAcqStatus(int index) {
      if (acqStatus_ == null) {
         return "";
      }
      return acqStatus_[index];
   }

   public void markAsAborted(MagellanGUIAcquisitionSettings settings) {
      if (acqStatus_ != null) {
         acqStatus_[acqSettingsList_.indexOf(settings)] = "Aborted";
         gui_.repaint();
      }
   }

   public String getAcquisitionDescription(int index) {
      return acqSettingsList_.get(index).toString();
   }

   private void validateSettings(MagellanGUIAcquisitionSettings settings) throws Exception {
      //space
      //non null surface
      if ((settings.spaceMode_ == MagellanGUIAcquisitionSettings.REGION_2D || settings.spaceMode_ == MagellanGUIAcquisitionSettings.CUBOID_Z_STACK)
              && settings.footprint_ == null) {
         throw new Exception("Error: No surface or region selected for " + settings.name_);
      }
      if (settings.spaceMode_ == MagellanGUIAcquisitionSettings.SURFACE_FIXED_DISTANCE_Z_STACK && settings.fixedSurface_ == null) {
         Log.log("Error: No surface selected for " + settings.name_, true);
         throw new Exception();
      }
      if (settings.spaceMode_ == MagellanGUIAcquisitionSettings.SURFACE_FIXED_DISTANCE_Z_STACK && settings.footprint_ == null) {
         throw new Exception("Error: No xy footprint selected for " + settings.name_);
      }
      if (settings.spaceMode_ == MagellanGUIAcquisitionSettings.VOLUME_BETWEEN_SURFACES_Z_STACK
              && (settings.topSurface_ == null || settings.bottomSurface_ == null)) {
         throw new Exception("Error: No surface selected for " + settings.name_);
      }
      //correct coordinate devices--XY
      if ((settings.spaceMode_ == MagellanGUIAcquisitionSettings.REGION_2D || settings.spaceMode_ == MagellanGUIAcquisitionSettings.CUBOID_Z_STACK)
              && !settings.footprint_.getXYDevice().equals(Magellan.getCore().getXYStageDevice())) {
         throw new Exception("Error: XY device for surface/grid does match XY device in MM core in " + settings.name_);
      }
      if (settings.spaceMode_ == MagellanGUIAcquisitionSettings.SURFACE_FIXED_DISTANCE_Z_STACK
              && !settings.fixedSurface_.getXYDevice().equals(Magellan.getCore().getXYStageDevice())) {
         Log.log("Error: XY device for surface does match XY device in MM core in " + settings.name_, true);
         throw new Exception();
      }
      if (settings.spaceMode_ == MagellanGUIAcquisitionSettings.VOLUME_BETWEEN_SURFACES_Z_STACK
              && (!settings.topSurface_.getXYDevice().equals(Magellan.getCore().getXYStageDevice())
              || !settings.bottomSurface_.getXYDevice().equals(Magellan.getCore().getXYStageDevice()))) {
         throw new Exception("Error: XY device for surface does match XY device in MM core in " + settings.name_);
      }
      //correct coordinate device--Z
      if (settings.spaceMode_ == MagellanGUIAcquisitionSettings.SURFACE_FIXED_DISTANCE_Z_STACK
              && !settings.fixedSurface_.getZDevice().equals(Magellan.getCore().getFocusDevice())) {
         throw new Exception("Error: Z device for surface does match Z device in MM core in " + settings.name_);
      }
      if (settings.spaceMode_ == MagellanGUIAcquisitionSettings.VOLUME_BETWEEN_SURFACES_Z_STACK
              && (!settings.topSurface_.getZDevice().equals(Magellan.getCore().getFocusDevice())
              || !settings.bottomSurface_.getZDevice().equals(Magellan.getCore().getFocusDevice()))) {
         throw new Exception("Error: Z device for surface does match Z device in MM core in " + settings.name_);
      }

      //channels
//       if (settings.channels_.isEmpty()) {
//           Log.log("Error: no channels selected for " + settings.name_);
//           throw new Exception();
//       }
   }

}
