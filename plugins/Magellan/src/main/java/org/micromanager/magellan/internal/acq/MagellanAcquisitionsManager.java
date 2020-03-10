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
package org.micromanager.magellan.internal.acq;

import org.micromanager.magellan.internal.gui.GUI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import javax.swing.JOptionPane;
import org.micromanager.magellan.api.MagellanAcquisitionAPI;
import org.micromanager.magellan.internal.main.Magellan;
import org.micromanager.magellan.internal.misc.Log;

/**
 *
 * @author Henry
 */
public class MagellanAcquisitionsManager {

   private static MagellanAcquisitionsManager singleton_;

   private CopyOnWriteArrayList<Acquisition> acqList_ = new CopyOnWriteArrayList<Acquisition>();
   private String[] acqStatus_;
   private GUI gui_;
   private volatile Acquisition currentAcq_;
   private volatile int currentAcqIndex_;
   private ExecutorService acqManageExecuterService_;
   ArrayList<Future> acqFutures_;

   public MagellanAcquisitionsManager(GUI gui) {
      singleton_ = this;
      gui_ = gui;
      acqList_.add(new MagellanGUIAcquisition(new MagellanGUIAcquisitionSettings()));
      acqManageExecuterService_ = Executors.newSingleThreadScheduledExecutor((Runnable r) -> new Thread(r, "Acquisition manager thread"));
   }

   public static MagellanAcquisitionsManager getInstance() {
      return singleton_;
   }

   public Acquisition getAcquisition(int index) {
      if (index < acqList_.size()) {
         return acqList_.get(index);
      }
      return null;
   }

   public int getNumberOfAcquisitions() {
      return acqList_.size();
   }

   public String getAcquisitionSettingsName(int index) {
      return ((MagellanGUIAcquisition) acqList_.get(index)).settings_.name_;
   }

   public String setAcquisitionName(int index, String newName) {
      return ((MagellanGUIAcquisition) acqList_.get(index)).settings_.name_ = newName;
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
         acqList_.add(index - 1, acqList_.remove(index));
         return -1;
      }
   }

   public int moveDown(int index) {
      acqStatus_ = null;
      if (index == acqList_.size() - 1) {
         //nothing to do
         return 0;
      } else {
         acqList_.add(index + 1, acqList_.remove(index));
         return 1;
      }
   }

   public MagellanAcquisitionAPI addNew() {
      acqStatus_ = null;
      Acquisition acq = new MagellanGUIAcquisition(new MagellanGUIAcquisitionSettings());
      acqList_.add(acq);
      gui_.acquisitionSettingsChanged();
      return acq;
   }

   public void remove(int index) {
      acqStatus_ = null;
      //must always have at least one acquisition
      if (index != -1 && acqList_.size() > 1) {
         acqList_.remove(index);
      } else {
         throw new RuntimeException("At least one acquistiion must exist");
      }
      gui_.acquisitionSettingsChanged();
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
      for (Acquisition acq : acqList_) {
         try {
            validateSettings(((MagellanGUIAcquisition) acq).settings_);
         } catch (Exception ex) {
            JOptionPane.showMessageDialog(gui_, "Problem with acquisition settings: \n" + ex.getMessage());
            return;
         }
      }

      //submit initialization events
      acqManageExecuterService_.submit(() -> {
         //TODO: once an API, disable adding and deleting of acquisitions here or just copy the list 
         gui_.enableMultiAcquisitionControls(false); //disallow changes while running
         acqStatus_ = new String[acqList_.size()];
         Arrays.fill(acqStatus_, "Waiting");
         gui_.repaint();
      });

      //submit acquisitions
      acqManageExecuterService_.submit(() -> {
         acqFutures_ = new ArrayList<Future>();
         for (int acqIndex = 0; acqIndex < acqList_.size(); acqIndex++) {
            final int index = acqIndex;
            Acquisition acq = acqList_.get(index);
            acqFutures_.add(acqManageExecuterService_.submit(() -> {
               acqStatus_[index] = "Running";
               gui_.acquisitionRunning(true);
               try {
                  currentAcq_ = acq;
                  currentAcq_.start();
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
      });
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

   public void markAsAborted(MagellanGUIAcquisition acq) {
      if (acqStatus_ != null) {
         acqStatus_[acqList_.indexOf(acq)] = "Aborted";
         gui_.repaint();
      }
   }

   public String getAcquisitionDescription(int index) {
      return acqList_.get(index).toString();
   }

   private void validateSettings(MagellanGUIAcquisitionSettings settings) throws Exception {
      //space
      //non null surface
      if (settings.spaceMode_ == MagellanGUIAcquisitionSettings.SURFACE_FIXED_DISTANCE_Z_STACK && settings.fixedSurface_ == null) {
         Log.log("Error: No surface selected for " + settings.name_, true);
         throw new Exception();
      }
      if (settings.spaceMode_ == MagellanGUIAcquisitionSettings.VOLUME_BETWEEN_SURFACES_Z_STACK
              && (settings.topSurface_ == null || settings.bottomSurface_ == null)) {
         throw new Exception("Error: No surface selected for " + settings.name_);
      }
      //correct coordinate devices--XY
      if ((settings.spaceMode_ == MagellanGUIAcquisitionSettings.REGION_2D || 
              settings.spaceMode_ == MagellanGUIAcquisitionSettings.REGION_2D_SURFACE_GUIDED ||
              settings.spaceMode_ == MagellanGUIAcquisitionSettings.CUBOID_Z_STACK)
              && settings.xyFootprint_ != null 
              && !settings.xyFootprint_.getXYDevice().equals(Magellan.getCore().getXYStageDevice())) {
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

   public MagellanGUIAcquisitionSettings getAcquisitionSettings(int index) {
      return ((MagellanGUIAcquisition) acqList_.get(index)).settings_;
   }

   public MagellanAcquisitionAPI getAcquisitionByHashCode(String hashCode) {
      for (Acquisition acq : acqList_) {
         if (Integer.toHexString(System.identityHashCode(acq)).equals(hashCode)) {
            return acq;
         }
      }
      throw new RuntimeException("Acquisition not found. Need to create a new acquisition or refresh available ones?");
   }

   void acquisitionFinished(MagellanGUIAcquisition acq) {
      //replace with new object so it can be run again
      int index = acqList_.indexOf(acq);
      MagellanGUIAcquisition done = (MagellanGUIAcquisition) acqList_.remove(index);
      acqList_.add(index, new MagellanGUIAcquisition(done.settings_));
   }

}
