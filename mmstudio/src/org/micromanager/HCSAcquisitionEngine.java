package org.micromanager;

import java.io.IOException;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Timer;
import java.util.TimerTask;

import javax.swing.JOptionPane;

import org.json.JSONException;
import org.micromanager.image5d.ChannelDisplayProperties;
import org.micromanager.image5d.Image5D;
import org.micromanager.image5d.Image5DWindow;
import org.micromanager.metadata.DisplaySettings;
import org.micromanager.metadata.MMAcqDataException;
import org.micromanager.metadata.WellAcquisitionData;
import org.micromanager.navigation.MultiStagePosition;
import org.micromanager.navigation.StagePosition;
import org.micromanager.utils.ChannelSpec;
import org.micromanager.utils.MMException;
import org.micromanager.utils.PositionMode;
import org.micromanager.utils.SliceMode;

public class HCSAcquisitionEngine extends MMAcquisitionEngineMT{
   private Image5DWindow i5dWinSingle_;
   private Image5D img5dSingle_;
   private Timer acqWellTimer_;
   private AcqWellTask acqWellTask_;

   /**
    * Timer task routine triggered at each frame. 
    */
   private class AcqWellTask extends TimerTask {
      private boolean running_ = false;
      private boolean active_ = true;
      private boolean coreLogInitialized_ = false;

      public void run() {
         running_ = true;

         // this is necessary because logging within core is set-up on per-thread basis
         // Timer task runs in a separate thread and unless we initialize we won't have any
         // log output when the task is executed
         if (!coreLogInitialized_) {
            core_.initializeLogging();
            coreLogInitialized_ = true;
         }

         if (pause_) {
            return;
         }

         acquireOneWellFrame(posCount_);

         running_ = false;
      }
      


      public boolean cancel() {
         boolean ret = super.cancel();
         active_ = false;
         running_ = false;
         return ret;
      }

      public synchronized boolean isRunning() {
         return running_;
      }
      public synchronized boolean isActive() {
         return active_;
      }
   }
   
   public HCSAcquisitionEngine() {
      super();
      i5dWinSingle_ = null;
      img5dSingle_ = null;
   }
   
   /**
    * Starts acquisition of the single well, based on the current protocol, using the supplied
    * acquisition data structure.
    * This command is specially designed for plate scanning and will automatically re-set
    * all appropriate parameters.
    * @throws MMAcqDataException 
    * @throws Exception
    */
   public void acquireWellScan(WellAcquisitionData wad) throws MMException, MMAcqDataException{

      zStage_ = core_.getFocusDevice();
      pause_ = false; // clear pause flag
      
      // force settings adequate for the well scanning
      useMultiplePositions_ = true;
      posMode_ = PositionMode.MULTI_FIELD;
      saveFiles_ = true;

      // check conditions for starting acq.
      if (isAcquisitionRunning()) {
         throw new MMException("Busy with the current acquisition.");
      }

      if (posList_ == null || posList_.getNumberOfPositions() < 1)
         throw new MMException("Multiple position mode is selected but position list is not defined");

      // check if the parent GUI is in the adequate state
      if (parentGUI_ != null)
      {
         oldLiveRunning_ = parentGUI_.getLiveMode();
         parentGUI_.stopAllActivity();
         if (!parentGUI_.okToAcquire())
            throw new MMException( "Unable to start acquisition.\n" +
            "Cancel 'Live' mode or other currently executing process in the main control panel.");
      }

      oldChannelState_ = null;
      try {
         oldExposure_ = core_.getExposure();
         String channelConfig = core_.getCurrentConfig(channelGroup_);
         if (channelConfig.length() > 0){
            oldChannelState_ = core_.getConfigGroupState(channelGroup_);
         }

         if (cameraConfig_.length() > 0) {
            core_.getConfigState(cameraGroup_, cameraConfig_);
            core_.setConfig(cameraGroup_, cameraConfig_);
         }

         // wait until all devices are ready
         core_.waitForSystem();
      } catch (Exception e) {
         throw new MMException(e.getMessage());
      }

      if (autofocusEnabled_ && autofocusPlugin_ == null) {
         throw new MMException( "Auto-focus plugin module (MMAutofocus_.jar) was not found.\n" +
         "Auto-focus option can not be used in this context.");                     
      }

      acquisitionLagging_ = false;
      posCount_ = 0;

      well_ = wad;
      posCount_=0;
      while (posCount_ < posList_.getNumberOfPositions()) {
         MultiStagePosition pos = posList_.getPosition(posCount_);
         try {
            MultiStagePosition.goToPosition(pos, core_);
            core_.waitForSystem();
         } catch (Exception e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
         }
         startWellAcquisition();
         System.out.println("DBG: MFT started");

         // wait until acquisition is done
         while (isAcquisitionRunning() || !acqFinished_) {
            try {
               System.out.println("DBG: Waiting");
               Thread.sleep(1000);
            } catch (InterruptedException e) {
               return;
            }
         }

         if (acqInterrupted_ == true) {
            break;
         }
         posCount_++;

      }
   }

   private void startWellAcquisition() {
      previousPosIdx_ = -1; // initialize
      acqInterrupted_ = false;
      acqFinished_ = false;
      frameCount_ = 0;
      Runtime.getRuntime().gc();

      try {
         if (isFocusStageAvailable())
            startZPosUm_ = core_.getPosition(zStage_);
         else
            startZPosUm_ = 0;
      } catch (Exception e) {
         //i5dWin_.setTitle("Acquisition (error)");
         JOptionPane.showMessageDialog(null, e.getMessage());     
      }

      //acqTimer_ = new Timer((int)frameIntervalMs_, timerHandler);
      acqWellTimer_ = new Timer();
      acqWellTask_ = new AcqWellTask();
      // a frame interval of 0 ms does not make sense to the timer.  set it to the smallest possible value
      if (frameIntervalMs_ < 1)
         frameIntervalMs_ = 1;
      if (numFrames_ > 0)
         acqWellTimer_.schedule(acqWellTask_, 0, (long)frameIntervalMs_);

   }
   
   private void executeWellProtocolBody(ChannelSpec cs, double z,
         double cur, int j, int k, int posIdx, int numSlices,
         int posIndexNormalized) {
      // TODO Auto-generated method stub
      
   }
   
   private void acquireOneWellFrame(int posIdx) {

      GregorianCalendar cldStart = new GregorianCalendar();
      int numSlices = useSliceSetting_ ? sliceDeltaZ_.length : 1;

      int posIndexNormalized;
      if (!useMultiplePositions_ || posMode_ == PositionMode.TIME_LAPSE)
         posIndexNormalized = posIdx;
      else
         posIndexNormalized = 0;

      // move to the required position
      try {
         if (useMultiplePositions_ /* && posMode_ == PositionMode.TIME_LAPSE */) {
            // time lapse logic
            MultiStagePosition pos = null;
            if (posIdx != previousPosIdx_) {
               pos = posList_.getPosition(posIdx);
               // TODO: in the case of multi-field mode the command below is redundant
               MultiStagePosition.goToPosition(pos, core_);
               core_.waitForSystem();
            } else
               pos = posList_.getPosition(previousPosIdx_);

            // perform auto focusing if the module is available
            if (autofocusPlugin_ != null && autofocusEnabled_) {
               autofocusPlugin_.fullFocus();

               // update the Z-position
               if (pos != null)
               {
                  double zFocus = core_.getPosition(zStage_);
                  StagePosition sp = pos.get(zStage_);
                  sp.x = zFocus; // assuming this is a single-axis stage set the first axis to the z value
               }
            }

            previousPosIdx_ = posIdx;

            // refresh the current z position
            try {
               if (isFocusStageAvailable())
                  startZPosUm_ = core_.getPosition(zStage_);
               else
                  startZPosUm_ = 0;
            } catch (Exception e) {
               //i5dWin_.setTitle("Acquisition (error)");
               JOptionPane.showMessageDialog(null, e.getMessage());     
            }
         }

         oldFocusEnabled_ = core_.isContinuousFocusEnabled();
         if (oldFocusEnabled_) {
            // wait up to 3 second for focus to lock:
            int waitMs= 0;
            int interval = 100;
            while (!core_.isContinuousFocusLocked() && waitMs < 3000) {
               Thread.sleep(interval);
               waitMs += interval;
            }
            core_.enableContinuousFocus(false);
         }

         if (sliceMode_ == SliceMode.CHANNELS_FIRST) {
            for (int j=0; j<numSlices; j++) {         
               double z = 0.0;
               double zCur = 0.0;

               if (absoluteZ_) {
                  z = sliceDeltaZ_[j];
               } else {
                  z = startZPosUm_ + sliceDeltaZ_[j];
               }
               if (isFocusStageAvailable() && numSlices > 1) {
                  core_.setPosition(zStage_, z);
                  zCur = z;
               }
               for (int k=0; k<channels_.size(); k++) {
                  ChannelSpec cs = channels_.get(k);

                  executeWellProtocolBody(cs, z, zCur, j, k, posIdx, numSlices, posIndexNormalized);
               }
            }
         } else if (sliceMode_ == SliceMode.SLICES_FIRST) {

            for (int k=0; k<channels_.size(); k++) {
               ChannelSpec cs = channels_.get(k);
               for (int j=0; j<numSlices; j++) {         
                  double z = 0.0;
                  double zCur = 0.0;

                  if (absoluteZ_) {
                     z = sliceDeltaZ_[j];
                  } else {
                     z = startZPosUm_ + sliceDeltaZ_[j];
                  }
                  if (isFocusStageAvailable() && numSlices > 1) {
                     core_.setPosition(zStage_, z);
                     zCur = z;
                  }
                  executeWellProtocolBody(cs, z, zCur, j, k, posIdx, numSlices, posIndexNormalized);                  
               }
            }
         } else {
            throw new MMException("Unrecognized slice mode: " + sliceMode_);
         }

         // return to the starting position
         if (isFocusStageAvailable() && numSlices > 1) {
            core_.setPosition(zStage_, startZPosUm_);
            core_.waitForDevice(zStage_);
         }

         // turn the continuous focus back again
         if (oldFocusEnabled_)
            core_.enableContinuousFocus(oldFocusEnabled_);

      } catch(MMException e) {
         stop(true);
         restoreSystem();
         acqFinished_ = true;
         Image5DWindow parentWnd = (useMultiplePositions_ && posMode_ == PositionMode.TIME_LAPSE) ? i5dWin_[posIdx] : i5dWin_[0];
         if (e.getMessage().length() > 0)
            JOptionPane.showMessageDialog(parentWnd, e.getMessage());     
         return;
      }  catch (OutOfMemoryError e) {
         stop(true);
         restoreSystem();
         acqFinished_ = true;
         Image5DWindow parentWnd = (useMultiplePositions_ && posMode_ == PositionMode.TIME_LAPSE) ? i5dWin_[posIdx] : i5dWin_[0];
         JOptionPane.showMessageDialog(parentWnd, e.getMessage() + "\nOut of memory - acquistion stopped.\n" +
         "In the future you can try to increase the amount of memory available to the Java VM (ImageJ).");     
         return;       
      } catch (IOException e) {
         stop(true);
         restoreSystem();
         acqFinished_ = true;
         Image5DWindow parentWnd = (useMultiplePositions_ && posMode_ == PositionMode.TIME_LAPSE) ? i5dWin_[posIdx] : i5dWin_[0];
         JOptionPane.showMessageDialog(parentWnd, e.getMessage()); 
         return;
      } catch (JSONException e) {
         stop(true);
         restoreSystem();
         acqFinished_ = true;
         Image5DWindow parentWnd = (useMultiplePositions_ && posMode_ == PositionMode.TIME_LAPSE) ? i5dWin_[posIdx] : i5dWin_[0];
         JOptionPane.showMessageDialog(parentWnd, e.getMessage()); 
         return;
      } catch (Exception e) {
         e.printStackTrace();
         stop(true);
         restoreSystem();
         acqFinished_ = true;
         Image5DWindow parentWnd = (useMultiplePositions_ && posMode_ == PositionMode.TIME_LAPSE) ? i5dWin_[posIdx] : i5dWin_[0];
         if (e.getMessage().length() > 0) {
            JOptionPane.showMessageDialog(parentWnd, e.getMessage()); 
         }
         return;
      }

      // Processing for the first frame in the sequence
      if (frameCount_ == 0) {
         // insert contrast settings metadata
         try {
            // contrast settings
            for (int i=0; i<channels_.size(); i++) {

               ChannelDisplayProperties cdp = img5d_[posIndexNormalized].getChannelDisplayProperties(i+1);               
               DisplaySettings ds = new DisplaySettings();
               ds.min = cdp.getMinValue();
               ds.max = cdp.getMaxValue();
               acqData_[posIndexNormalized].setChannelDisplaySetting(i, ds);
            }            
         } catch (MMAcqDataException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
         }
      }

      i5dWin_[posIndexNormalized].startCountdown((long)frameIntervalMs_ - (GregorianCalendar.getInstance().getTimeInMillis() - cldStart.getTimeInMillis()), numFrames_ - frameCount_);
      try {
         acqData_[posIndexNormalized].setDimensions(frameCount_+1, channels_.size(), useSliceSetting_ ? sliceDeltaZ_.length : 1);
      } catch (MMAcqDataException e) {
         // TODO Auto-generated catch block
         e.printStackTrace();
      }

      // update frame counter
      if (useMultiplePositions_ && posMode_ == PositionMode.TIME_LAPSE) {
         if (posIdx == posList_.getNumberOfPositions() - 1)
            frameCount_++;
      } else {
         frameCount_++;      
      }

      // check the termination criterion
      if(frameCount_ >= numFrames_) {
         // acquisition finished
         stop(false);

         // adjust the title
         Date enddate = GregorianCalendar.getInstance().getTime();
         if (useMultiplePositions_) {
            if (posMode_ == PositionMode.TIME_LAPSE) {
               for (int pp=0; pp<i5dWin_.length; pp++)
                  i5dWin_[pp].setTitle("Acquisition "  + posList_.getPosition(pp).getLabel() + "(completed)" + enddate);
            } else {
               i5dWin_[0].setTitle("Acquisition (completed) " + posList_.getPosition(posIdx).getLabel() + enddate);
            }
         } else {
            i5dWin_[0].setTitle("Acquisition (completed) " + enddate);
         }

         // return to initial state
         restoreSystem();
         acqFinished_ = true;

         return;
      }
   }

}
