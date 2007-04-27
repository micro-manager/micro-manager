///////////////////////////////////////////////////////////////////////////////
//FILE:          MMAcquisitionEngineMT.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nenad Amodaj, nenad@amodaj.com, Dec 1, 2007
//
// COPYRIGHT:    University of California, San Francisco, 2007
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
// CVS:          $Id: MMAcquisitionEngine.java 38 2007-04-03 01:26:31Z nenad $

package org.micromanager;

import ij.ImagePlus;
import ij.io.FileSaver;
import ij.plugin.Memory;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import ij.process.ShortProcessor;

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.Timer;
import java.util.TimerTask;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import mmcorej.CMMCore;
import mmcorej.Configuration;
import mmcorej.StrVector;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.image5d.ChannelCalibration;
import org.micromanager.image5d.ChannelControl;
import org.micromanager.image5d.ChannelDisplayProperties;
import org.micromanager.image5d.Image5D;
import org.micromanager.image5d.Image5DWindow;
import org.micromanager.metadata.ImageKey;
import org.micromanager.metadata.ImagePropertyKeys;
import org.micromanager.metadata.SummaryKeys;
import org.micromanager.navigation.MultiStagePosition;
import org.micromanager.navigation.PositionList;
import org.micromanager.utils.AcquisitionEngine;
import org.micromanager.utils.ChannelSpec;
import org.micromanager.utils.ContrastSettings;
import org.micromanager.utils.DeviceControlGUI;
import org.micromanager.utils.MMException;
import org.micromanager.utils.PositionMode;
import org.micromanager.utils.SliceMode;

import com.quirkware.guid.PlatformIndependentGuidGen;

/**
 * Multi-threaded acquisition engine.
 * Runs Image5d acquisition based on the protocol and generates
 * metadata in JSON format.
 */
public class MMAcquisitionEngineMT implements AcquisitionEngine {
   // presistent properties (app settings)
   
   private String channelGroup_;
      
   // metadata keys
   
   private String cameraConfig_ = "";
   private Configuration oldCameraState_;
   private Configuration oldChannelState_;
   private double oldExposure_ = 10.0;
   
   private int numFrames_;
   private long startTimeMs_=0;
   private double frameIntervalMs_;
   private Image5D img5d_[];
   private Image5DWindow i5dWin_[];
   private int frameCount_;
   private int posCount_;
   private int xWindowPos = 100;
   private int yWindowPos = 100;
   
   private boolean saveFiles_ = false;
   private boolean acquisitionLagging_ = false;
   private String dirName_;
   private String outDirName_;
   private String rootName_;
   private File outputDir_[];
   
   CMMCore core_;
   PositionList posList_;
   DeviceControlGUI parentGUI_;
   String zStage_;
   
   ArrayList<ChannelSpec> channels_;
   double[] sliceDeltaZ_;
   double bottomZPos_;
   double topZPos_;
   double deltaZ_;
   
   private double startPos_;
   private Timer acqTimer_;
   private FileWriter metaWriter_[];
   private JSONObject metadata_[];
   private boolean absoluteZ_ = false;
   private String comment_ = "";
   private boolean useSliceSetting_ = true;
   
   // physical dimensions which should be uniform for the entire acquistion
   private long imgWidth_ = 0;
   private long imgHeight_ = 0;
   private long imgDepth_ = 0;

   private PlatformIndependentGuidGen guidgen_;

   private boolean useMultiplePositions_;

   private int posMode_ = PositionMode.TIME_LAPSE;
   private int sliceMode_ = SliceMode.CHANNELS_FIRST;

   private int previousPosIdx_;
   private boolean acqInterrupted_;
   private boolean oldFocusEnabled_;

   private AcqFrameTask acqTask_;
   
   /**
    * Timer task routine triggered at each frame. 
    */
   private class AcqFrameTask extends TimerTask {
      private boolean running_ = false;
      private boolean active_ = true;
      
      public void run() {
         running_ = true;
         if (useMultiplePositions_ && posMode_ == PositionMode.TIME_LAPSE) {
            for (int i=0; i<posList_.getNumberOfPositions(); i++)
               acquireOneFrame(i);
         } else {
            System.out.println("Processing position: " + posCount_ + "...");
            acquireOneFrame(posCount_);
            System.out.println("Done.");
         }
         
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

   /**
    * Multi-field thread. 
    */
   private class MultiFieldThread implements Runnable {
      public void run() {
         posCount_=0;
         System.out.println("Multi-field started");
         while (posCount_ < posList_.getNumberOfPositions()) {
            System.out.println("Acq " + posCount_ + " started");
            startAcquisition();
            
            // wait until acq done
            while (isAcquisitionRunning()) {
               try {
                  System.out.println("loop");
                  Thread.sleep(500);
               } catch (InterruptedException e) {
                  return;
               }
            }
            
            if (acqInterrupted_ == true) {
               System.out.println("Acq " + posCount_ + " interrupted");
               break;
            }
            System.out.println("Acq " + posCount_ + " completed");
            posCount_++;
            System.out.println("Position incremented to: " + posCount_);
            
            // shut down window if more data is coming       
//            if (posCount_ < posList_.getNumberOfPositions() && saveFiles_)
//               try {
//                  SwingUtilities.invokeAndWait((new DisposeI5d(i5dWin_[0])));
//               } catch (InterruptedException e) {
//                  // TODO Auto-generated catch block
//                  e.printStackTrace();
//               } catch (InvocationTargetException e) {
//                  // TODO Auto-generated catch block
//                  e.printStackTrace();
//               }
//            }
         }
      }
   }
   
   
   /**
    * Threadsafe image5d window update.
    */
   private class RefreshI5d implements Runnable {
      private Image5DWindow i5dWin_;
      
      public RefreshI5d(Image5DWindow i5dw) {
         i5dWin_ = i5dw;
      }
      public void run() {
         i5dWin_.getImagePlus().updateAndDraw();
         i5dWin_.getCanvas().paint(i5dWin_.getCanvas().getGraphics());                              
      }
   }
   
   /**
    * Threadsafe image5d window shutdown.
    */
   private class DisposeI5d implements Runnable {
      private Image5DWindow i5dWin_;
      
      public DisposeI5d(Image5DWindow i5dw) {
         i5dWin_ = i5dw;
      }
      public void run() {
         i5dWin_.dispose();
      }
   }

   /**
    * Multi-threaded acquition engine.
    */
   public MMAcquisitionEngineMT() {
            
      channels_ = new ArrayList<ChannelSpec>();
      //channels_.add(new ChannelSpec());
      sliceDeltaZ_ = new double[1];
      sliceDeltaZ_[0] = 0.0; // um
      bottomZPos_ = 0.0;
      topZPos_ = 0.0;
      deltaZ_ = 0.0;
      numFrames_ = 1;
      frameIntervalMs_ = 0.0; // ms
      frameCount_ = 0;
      acqInterrupted_ = false;
      posCount_ = 0;
      //metadata_ = new JSONObject();
      rootName_ = new String(DEFAULT_ROOT_NAME);
      guidgen_ = PlatformIndependentGuidGen.getInstance();
      channelGroup_ = new String(ChannelSpec.DEFAULT_CHANNEL_GROUP);
      posList_ = new PositionList();
   }
      
   public void setCore(CMMCore c) {
      core_ = c;
   }
   
   public void setPositionList(PositionList posList) {
      posList_ = posList;
   }
   
   public ArrayList<ChannelSpec> getChannels() {
      return channels_;
   }
   
   public void setChannels(ArrayList<ChannelSpec> ch) {
      channels_ = ch;
   }
   
   /**
    * Set the channel group if the current hardware configuration permits.
    * @param group
    * @return - true if successful
    */
   public boolean setChannelGroup(String group) {
      if (core_.isGroupDefined(group)) {
         channelGroup_ = group;
         return true;
      } else {
         channelGroup_ = ChannelSpec.DEFAULT_CHANNEL_GROUP;
         return false;
      }
   }
   
   public String getChannelGroup() {
      return channelGroup_;
   }
     
   public void setParentGUI(DeviceControlGUI parent) {
      parentGUI_ = parent;
   }
   
   /**
    * Starts acquisition, based on the current protocol.
    * @throws Exception
    */
   public void acquire() throws MMException{

      // check conditions for starting acq.
      if (isAcquisitionRunning()) {
         throw new MMException("Busy with the current acquisition.");
      }
      if (useMultiplePositions_ && (posList_ == null || posList_.getNumberOfPositions() < 1))
         throw new MMException("Multiple position mode is selected but position list is not defined");

      // check if the parent GUI is in the adequate state
      if (parentGUI_ != null)
      {
         parentGUI_.stopAllActivity();
         if (!parentGUI_.okToAcquire())
            throw new MMException( "Unable to start acquisition.\n" +
            "Cancel 'Live' mode or other currently executing process in the main control panel.");
      }

      oldCameraState_ = null;
      oldChannelState_ = null;
      try {
         oldExposure_ = core_.getExposure();
         String channelConfig = core_.getCurrentConfig(channelGroup_);
         if (channelConfig.length() > 0){
            oldChannelState_ = core_.getConfigState(channelGroup_, core_.getCurrentConfig(channelGroup_));
         }

         if (cameraConfig_.length() > 0) {
            // store current camera configuration
            oldCameraState_ = core_.getConfigState(cameraGroup_, cameraConfig_);
            core_.setConfig(cameraGroup_, cameraConfig_);
         }

         // wait until all devices are ready
         core_.waitForSystem();
      } catch (Exception e) {
         throw new MMException(e.getMessage());
      }

      acquisitionLagging_ = false;
      posCount_ = 0;
      
      if (useMultiplePositions_) {
         if (posMode_ == PositionMode.TIME_LAPSE) {
            startAcquisition();
         } else {
            // start a multi-field thread
           MultiFieldThread mft = new MultiFieldThread();
           mft.run();
         }
      } else {
         startAcquisition();
      }
   }
   
   /**
    * Resets the engine.
    */
   public void clear() {
      channels_.clear();
      frameCount_ = 0;
      posCount_= 0;
   }
   
   /**
    * Add new channel if the current state of the hardware permits.
    * 
    * @param config - configuration name
    * @param exp
    * @param zOffset
    * @param c8
    * @param c16
    * @param c
    * @return - true if successful
    */
   public boolean addChannel(String config, double exp, double zOffset, ContrastSettings c8, ContrastSettings c16, Color c) {
      if (isConfigAvailable(config)) {
         ChannelSpec channel = new ChannelSpec();
         channel.config_ = config;
         channel.exposure_ = exp;
         channel.zOffset_ = zOffset;
         channel.contrast8_ = c8;
         channel.contrast16_ = c16;
         channel.color_ = c;
         channels_.add(channel);
         return true;
      } else
         return false;
   }
   
   public void setFrames(int numFrames, double deltaT) {
      numFrames_ = numFrames;
      frameIntervalMs_ = deltaT;
   }
   
   public int getCurrentFrameCount() {
      return frameCount_;
   }
   
   public void setSlices(double bottom, double top, double zStep, boolean absolute) {
      absoluteZ_  = absolute;
      bottomZPos_ = bottom;
      topZPos_ = top;
      if (top >= bottom)
         deltaZ_ = Math.abs(zStep);
      else
         deltaZ_ = -Math.abs(zStep);
      
      int numSlices = 0;
      if (Math.abs(zStep) >= getMinZStepUm())
         numSlices = (int) (Math.abs(top - bottom) / zStep + 0.5) + 1;
      sliceDeltaZ_ = new double[numSlices];
      for (int i=0; i<sliceDeltaZ_.length; i++){
         sliceDeltaZ_[i] = bottom + deltaZ_*i;
      }
      if (numSlices == 0)
      {
         sliceDeltaZ_ = new double[1];
         sliceDeltaZ_[0] = 0.0;
      }
   }
  
   public void setZStageDevice(String label) {
      zStage_ = label;
   }
   
   public void setComment(String txt) {
      comment_ = txt;
   }
   
   /**
    * Find out which channels are currently available for the selected channel group.
    * @return - list of channel (preset) names
    */
   public String[] getChannelConfigs() {
      if (core_ == null)
         return new String[0];
      StrVector vcfgs = core_.getAvailableConfigs(channelGroup_);
      String cfgs[] = new String[(int)vcfgs.size()];
      for (int i=0; i<cfgs.length; i++)
         cfgs[i] = vcfgs.get(i);
      return cfgs;
   }
   
   /**
    * Find out if the configuration is compatible with the current group.
    * This method should be used to verify if the acquistion protocol is consistent
    * with the current settings.
    */
   public boolean isConfigAvailable(String config) {
      StrVector vcfgs = core_.getAvailableConfigs(channelGroup_);
      for (int i=0; i<vcfgs.size(); i++)
         if (config.compareTo(vcfgs.get(i)) == 0)
            return true;
      return false;
   }
   
   public String[] getCameraConfigs() {
      if (core_ == null)
         return new String[0];
      StrVector vcfgs = core_.getAvailableConfigs(cameraGroup_);
      String cfgs[] = new String[(int)vcfgs.size()];
      for (int i=0; i<cfgs.length; i++)
         cfgs[i] = vcfgs.get(i);
      return cfgs;
   }

   public int getNumFrames() {
      return numFrames_;
   }
   
   public double getFrameIntervalMs() {
      return frameIntervalMs_;
   }
   
   public double getSliceZBottomUm() {
      return bottomZPos_;
   }
   
   public double getSliceZStepUm() {
      return deltaZ_;
   }
   
   public double getZTopUm() {
      return topZPos_;
   }
   
   public void setChannel(int row, ChannelSpec channel) {
      channels_.set(row, channel);  
   }

   public void setUpdateLiveWindow(boolean update) {
   }
   
   public boolean isAcquisitionLagging() {
      return acquisitionLagging_;
   }
   
   public void setCameraConfig(String cfg) {
      this.cameraConfig_ = cfg;
   }
   
   /**
    * Starts the acquisition.
    */
   public void startAcquisition(){
      previousPosIdx_ = -1; // initialize
      acqInterrupted_ = false;
      frameCount_ = 0;
      Runtime.getRuntime().gc();
            
      try {
         if (isFocusStageAvailable())
            startPos_ = core_.getPosition(zStage_);
         else
            startPos_ = 0;
      } catch (Exception e) {
         //i5dWin_.setTitle("Acquisition (error)");
         JOptionPane.showMessageDialog(null, e.getMessage());     
      }
      
      //acqTimer_ = new Timer((int)frameIntervalMs_, timerHandler);
      acqTimer_ = new Timer();
      acqTask_ = new AcqFrameTask();
      if (numFrames_ > 0)
         acqTimer_.schedule(acqTask_, 0, (long)frameIntervalMs_);

   }

   /**
    * Acquires a single frame in the acquistion sequence.
    *
    */
   public void acquireOneFrame(int posIdx) {

      GregorianCalendar cld = null;
      GregorianCalendar cldStart = new GregorianCalendar();
      int numSlices = useSliceSetting_ ? sliceDeltaZ_.length : 1;
      
      int posIndexNormalized;
      if (!useMultiplePositions_ || posMode_ == PositionMode.TIME_LAPSE)
         posIndexNormalized = posIdx;
      else
         posIndexNormalized = 0;

      // move to the required position
      try {
         if (useMultiplePositions_ && posMode_ == PositionMode.TIME_LAPSE) {
            // time lapse logic
            if (posIdx != previousPosIdx_) {
               MultiStagePosition pos = posList_.getPosition(posIdx);
               MultiStagePosition.goToPosition(pos, core_);
               core_.waitForSystem();
            }

            previousPosIdx_ = posIdx;
         }

         System.out.println("Frame " + frameCount_ + " at " + GregorianCalendar.getInstance().getTime());

         oldFocusEnabled_ = core_.isContinuousFocusEnabled();
         if (oldFocusEnabled_)
            core_.enableContinuousFocus(false);
         
         for (int j=0; j<numSlices; j++) {         
            double z = 0.0;
            double zOffset = 0.0;
            double zCur = 0.0;

            if (absoluteZ_) {
               z = sliceDeltaZ_[j];
            } else {
               z = startPos_ + sliceDeltaZ_[j];
            }
            if (isFocusStageAvailable() && numSlices > 1) {
               core_.setPosition(zStage_, z);
               System.out.println("Slice " + j + " at " + z);
               zCur = z;
            }
            for (int k=0; k<channels_.size(); k++) {
               ChannelSpec cs = channels_.get(k);

               // apply z-offsets
               if (isFocusStageAvailable() && cs.zOffset_ != 0.0) {
                  core_.waitForDevice(zStage_);
                  zOffset = z + cs.zOffset_;
                  core_.setPosition(zStage_, zOffset);
                  zCur = zOffset;
               }

               core_.setConfig(channelGroup_, cs.config_);
               System.out.println("Binning set to " + core_.getProperty(core_.getCameraDevice(), "Binning"));

               double exposureMs = cs.exposure_;
               core_.setExposure(cs.exposure_);
               cld = new GregorianCalendar();
               core_.snapImage();
               long width = core_.getImageWidth();
               long height = core_.getImageHeight();
               long depth = core_.getBytesPerPixel();

               // processing for the first image in the entire sequence
               if (j==0 && k==0 && frameCount_ == 0) {
                  if (!useMultiplePositions_ || posMode_ == PositionMode.TIME_LAPSE) {
                     if (posIdx == 0) {
                        setupImage5d(posIdx);
                        acquisitionSetup(posIdx);
                        System.out.println("Sequence size: " + imgWidth_ + "," + imgHeight_);
                     }
                  } else {
                     setupImage5d(posIdx);
                     acquisitionSetup(posIdx);
                     System.out.println("Sequence size: " + imgWidth_ + "," + imgHeight_);
                  }
               }

               // processing for the first image in a frame
               if (j==0 && k==0) {                 
                  // check if we have enough memory to acquire the entire frame
                  long freeBytes = freeMemory();
                  long requiredBytes = ((long)numSlices * channels_.size() + 10) * (width * height * depth);
                  //System.out.println("Remaining memory " + freeBytes + " bytes. Required: " + requiredBytes);
                  if (freeBytes <  requiredBytes) {
                     throw new OutOfMemoryError("Remaining memory " + FMT2.format(freeBytes/1048576.0) +
                           " MB. Required for the next step: " + FMT2.format(requiredBytes/1048576.0) + " MB");
                  }
               }

               // get pixels
               Object img = core_.getImage();

               // we won't try to adjust type mismatch
               if (imgDepth_ != depth) {
                  throw new MMException("The byte depth does not match between channels or slices");
               }

               // rescale image if necessary to conform to the entire sequence
               if (imgWidth_!=width || imgHeight_!=height) {
                  System.out.println("Scaling from: " + width + "," + height);
                  ImageProcessor imp;
                  if (imgDepth_ == 1)
                     imp = new ByteProcessor((int)width, (int)height);
                  else
                     imp = new ShortProcessor((int)width, (int)height);
                  imp.setPixels(img);
                  ImageProcessor ip2 = imp.resize((int)imgWidth_, (int)imgHeight_);
                  img = ip2.getPixels();
               }


               // set Image5D
                  
               img5d_[posIndexNormalized].setPixels(img, k+1, j+1, frameCount_+1);
               if (!i5dWin_[posIndexNormalized].isPlaybackRunning())
                  img5d_[posIndexNormalized].setCurrentPosition(0, 0, k, j, frameCount_);

               // autoscale channels based on the first slice of the first frame
               if (j==0 && frameCount_==0) {
                  ImageStatistics stats = img5d_[posIndexNormalized].getStatistics(); // get uncalibrated stats
                  double min = stats.min;
                  double max = stats.max;
                  img5d_[posIndexNormalized].setChannelMinMax(k+1, min, max);                  
               }

               RefreshI5d refresh = new RefreshI5d(i5dWin_[posIndexNormalized]);                            
               //SwingUtilities.invokeAndWait(refresh);
               SwingUtilities.invokeLater(refresh);

               // save file
               String fname = ImageKey.generateFileName(frameCount_, (channels_.get(k)).config_, j);

               // generate metadata
               JSONObject jsonData = new JSONObject();
               jsonData.put(ImagePropertyKeys.FILE, fname);
               jsonData.put(ImagePropertyKeys.FRAME, frameCount_);
               jsonData.put(ImagePropertyKeys.CHANNEL, (channels_.get(k)).config_);
               jsonData.put(ImagePropertyKeys.SLICE, j);
               jsonData.put(ImagePropertyKeys.TIME, cld.getTime());
               jsonData.put(ImagePropertyKeys.ELAPSED_TIME_MS, cld.getTimeInMillis() - startTimeMs_ );
               jsonData.put(ImagePropertyKeys.EXPOSURE_MS, exposureMs);

               // TODO: consider more flexible positional information
               jsonData.put(ImagePropertyKeys.Z_UM, zCur);
               if (useMultiplePositions_) {
                  jsonData.put(ImagePropertyKeys.X_UM, posList_.getPosition(posIdx).getX());
                  jsonData.put(ImagePropertyKeys.Y_UM, posList_.getPosition(posIdx).getY());
               }
               // insert the metadata for the current image
               metadata_[posIndexNormalized].put(ImageKey.generateFrameKey(frameCount_, k, j), jsonData);

               if (saveFiles_) {
                  saveImageFile(outputDir_[posIndexNormalized] + "/" + fname, img, (int)imgWidth_, (int)imgHeight_);
               }              
            }
         }
         
         // turn the contionuous focus back again
         if (oldFocusEnabled_)
            core_.enableContinuousFocus(oldFocusEnabled_);

      } catch(MMException e) {
         acqInterrupted_ = true;
         stop();
         restoreSystem();
         if (e.getMessage().length() > 0)
            JOptionPane.showMessageDialog(i5dWin_[posIdx], e.getMessage());     
         return;
      } catch (Exception e) {
         acqInterrupted_ = true;
         System.out.println(e.getMessage());
         stop();
         restoreSystem();
         if (e.getMessage().length() > 0) {
            if (useMultiplePositions_ && posMode_ == PositionMode.TIME_LAPSE)
               JOptionPane.showMessageDialog(i5dWin_[posIdx], e.getMessage()); 
            else
               JOptionPane.showMessageDialog(i5dWin_[0], e.getMessage()); 
         }
         return;
      } catch (OutOfMemoryError e) {
         JOptionPane.showMessageDialog(i5dWin_[posIdx], e.getMessage() + "\nOut of memory - acquistion stopped.\n" +
         "In the future you can try to increase the amount of memory available to the Java VM (ImageJ).");     
         acqInterrupted_ = true;
         stop();
         restoreSystem();
         return;       
      }
         
      // Processing for the first frame in the sequence
      if (frameCount_ == 0) {
         // insert contrast settings metadata
         try {
            JSONObject summary = metadata_[posIndexNormalized].getJSONObject(SummaryKeys.SUMMARY);
            JSONArray minArray = new JSONArray();
            JSONArray maxArray = new JSONArray();
            
            // contrast settings
            for (int i=0; i<channels_.size(); i++) {
               ChannelDisplayProperties cdp = img5d_[posIndexNormalized].getChannelDisplayProperties(i+1);
               minArray.put(i, cdp.getMinValue());
               maxArray.put(i, cdp.getMaxValue());
            }
            summary.put(SummaryKeys.CHANNEL_CONTRAST_MIN, minArray);
            summary.put(SummaryKeys.CHANNEL_CONTRAST_MAX, maxArray);
         } catch (JSONException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
         }
      }
            
      i5dWin_[posIndexNormalized].startCountdown((long)frameIntervalMs_ - (cld.getTimeInMillis() - cldStart.getTimeInMillis()), numFrames_ - frameCount_);
      try {
         JSONObject summary = metadata_[posIndexNormalized].getJSONObject(SummaryKeys.SUMMARY);
         summary.put(SummaryKeys.NUM_FRAMES, frameCount_);
      } catch (JSONException e) {
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
         stop();
         
         // adjust the title
         if (useMultiplePositions_) {
            if (posMode_ == PositionMode.TIME_LAPSE) {
               for (int pp=0; pp<i5dWin_.length; pp++)
                  i5dWin_[pp].setTitle("Acquisition "  + posList_.getPosition(pp).getLabel() + "(completed)" + cld.getTime());
            } else {
               i5dWin_[0].setTitle("Acquisition (completed) " + posList_.getPosition(posIdx).getLabel() + cld.getTime());
            }
         } else {
            i5dWin_[0].setTitle("Acquisition (completed) " + cld.getTime());
         }
         
         // return to initial state
         restoreSystem();         
         return;
      }
   }
 
   public boolean saveImageFile(String fname, Object img, int width, int height) {
      ImageProcessor ip;
      if (img instanceof byte[]) {
         ip = new ByteProcessor(width, height);
         ip.setPixels((byte[])img);
      }
      else if (img instanceof short[]) {
         ip = new ShortProcessor(width, height);
         ip.setPixels((short[])img);
      }
      else
         return false;
      
      ImagePlus imp = new ImagePlus(fname, ip);
      FileSaver fs = new FileSaver(imp);
      return fs.saveAsTiff(fname);
   }
   
   private void restoreSystem() {
      try {
         //core_.waitForSystem();
         core_.setExposure(oldExposure_);
         if (isFocusStageAvailable())
            core_.setPosition(zStage_, startPos_);
         if (oldCameraState_ != null)
            core_.setSystemState(oldCameraState_); // restore original settings
         if (oldChannelState_ != null)
            core_.setSystemState(oldChannelState_);
         core_.enableContinuousFocus(oldFocusEnabled_); // restore cont focus
         core_.waitForSystem();
         
         // >>> update GUI disabled
//         if (parentGUI_ != null)
//            parentGUI_.updateGUI();
     } catch (Exception e) {
        // do not complain here
     }      
   }
   
   public String getVerboseSummary() {
      int numSlices = useSliceSetting_ ? sliceDeltaZ_.length : 1;
      int totalImages = numFrames_ * numSlices * channels_.size();
      double totalDurationSec = frameIntervalMs_ * numFrames_ / 1000.0;
      int hrs = (int)(totalDurationSec / 3600);
      double remainSec = totalDurationSec - hrs*3600;
      int mins = (int)(remainSec / 60);
      remainSec = remainSec - mins * 60;
      
      Runtime rt = Runtime.getRuntime();
      rt.gc();
      
      String txt;
      txt = "Number of channels: " + channels_.size() + 
            "\nNumber of slices: " + numSlices +
            "\nNumber of frames: " + numFrames_ +
            "\nTotal images: " + totalImages +
            "\nDuration: " + hrs + "h " + mins + "m " + remainSec + "s";
      return txt;
   }
   
   public void stop() {
         
      if (acqTask_ != null) {
         //acqTimer_.stop();
         acqTask_.cancel();
         // wait until task finishes
         while (isAcquisitionRunning()) {
            try {
               Thread.sleep(50);
            } catch (InterruptedException e) {
               // TODO Auto-generated catch block
               e.printStackTrace();
            }
         }
         System.out.println("Acquisition stopped!!!");
      }
      
      if (i5dWin_ == null)
         return;
      
      if (metadata_ == null)
         return;
      
      for (int i=0; i<i5dWin_.length; i++) {
         String metaStream = new String();
         try {
            metaStream = metadata_[i].toString(3);
         } catch (JSONException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
         }
         saveMetadata(metaStream, i);
         
         if (i5dWin_[i] != null) {
            i5dWin_[i].stopCountdown();
            if (useMultiplePositions_)
               i5dWin_[i].setTitle("Acquisition "  + posList_.getPosition(i).getLabel() + "(aborted)");
            else
               i5dWin_[i].setTitle("Acquisition aborted)");
            i5dWin_[i].setMetadata(metaStream);
            i5dWin_[i].setActive(false);
            i5dWin_[i].setAcquitionEngine(null); // disengage from the acquistion
            i5dWin_[i].setPlaybackFrames(frameCount_);
         }
      }
   }

   private void saveMetadata(String meta, int idx) {     
      if (metaWriter_ != null && saveFiles_)
         try {
            metaWriter_[idx].write(meta);
            metaWriter_[idx].close();
            metaWriter_[idx] = null;
         } catch (IOException e) {
            // do not complain here
            e.printStackTrace();
         }
   }
   
   public boolean isAcquisitionRunning() {
      if (acqTask_ == null)
         return false;
     
      return acqTask_.isActive() || acqTask_.isRunning();
   }
   
   private boolean isFocusStageAvailable() {
      if (zStage_ != null && zStage_.length() > 0)
         return true;
      else
         return false;
   }
   
   /**
    * Unconditional shutdown
    *
    */
   public void shutdown() {
      if (isAcquisitionRunning())
         stop();
//      if (i5dWin_ != null) {
//         i5dWin_.dispose();
//      }
   }
   
   public void updateImageGUI() {
         if (parentGUI_ != null) {
            parentGUI_.updateImageGUI();
         }
   }

   public double getCurrentZPos() {
      if (isFocusStageAvailable()) {
         double z = 0.0;
         try {
            //core_.waitForDevice(zStage_);
            z = core_.getPosition(zStage_);
         } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
         }
         return z;
      }
      return 0;
   }
   
   public double getMinZStepUm() {
      // TODO: obtain this informaton from hardware
      // hardcoded to 0.1 um
      return 0.1;
   }

   public void setDirName(String dirName_) {
      this.dirName_ = dirName_;
   }

   public String getDirName() {
      return dirName_;
   }

   public void setRootName(String rootName_) {
      this.rootName_ = rootName_;
   }

   public String getRootName() {
      return rootName_;
   }

   public void setSaveFiles(boolean saveFiles_) {
      this.saveFiles_ = saveFiles_;
   }

   /**
    * Acquisition directory set-up.
    * @throws IOException 
    * @throws JSONException 
    */
   private void acquisitionSetup(int posIdx) throws IOException, JSONException {
      metaWriter_ = null;
      outputDir_ = null;
      
      if (!useMultiplePositions_ || posMode_ == PositionMode.TIME_LAPSE)
         metadata_ = new JSONObject[posList_.getNumberOfPositions()];
      else
         metadata_ = new JSONObject[1];

      if (saveFiles_) {
         metaWriter_ = new FileWriter[metadata_.length];
         outputDir_ = new File[metadata_.length];

         System.out.println("Begin setting up meta writers");

         // What follows is some complicated logic to create unique directory names
         // but maintain same sub-directry paths for multiple positions
         File testDir;
         if (!useMultiplePositions_ || posMode_ == PositionMode.TIME_LAPSE) {
            for (int i=0; i<metadata_.length; i++) {
               // create new acq directory
               int suffixCounter = 0;
               String testName;
               do {
                  testName = new String(rootName_ + "/" + dirName_ + "_" + suffixCounter);
                  suffixCounter++;
                  testDir = new File(testName);

               } while (testDir.exists() && i==0);

               if (i==0)
                  outDirName_ = testName;

               if (useMultiplePositions_) {
                  outputDir_[i] = new File(outDirName_ + "/" + posList_.getPosition(i).getLabel());
               } else {
                  outputDir_[i] = new File(outDirName_);
               }

               // finally we attempt to create directory based on the name created above
               System.out.println("Making directory: " + outputDir_[i]);
               if (!outputDir_[i].mkdirs())
                  throw new IOException("Invalid root directory name: " + outputDir_[i]);

               // create a file for metadata
               metaWriter_[i] = new FileWriter(new File(outputDir_[i].getAbsolutePath() + "/" + ImageKey.METADATA_FILE_NAME));
            }
         } else {
            // multi-field
            if (posIdx == 0) {
               // create new acq directory
               int suffixCounter = 0;
               String testName;
               do {
                  testName = new String(rootName_ + "/" + dirName_ + "_" + suffixCounter);
                  suffixCounter++;
                  testDir = new File(testName);

               } while (testDir.exists());
               outDirName_ = testName;
            }

            outputDir_[0] = new File(outDirName_ + "/" + posList_.getPosition(posIdx).getLabel());

            // finally we attempt to create directory based on the name created above
            System.out.println("Making directory: " + outputDir_[0]);
            if (!outputDir_[0].mkdirs())
               throw new IOException("Invalid root directory name: " + outputDir_[0]);

            // create a file for metadata
            metaWriter_[0] = new FileWriter(new File(outputDir_[0].getAbsolutePath() + "/" + ImageKey.METADATA_FILE_NAME));
         }
      }

      for (int i=0; i<metadata_.length; i++) {
         metadata_[i] = new JSONObject();
         JSONObject i5dData = new JSONObject();
         i5dData.put(SummaryKeys.GUID, guidgen_.genNewGuid());
         i5dData.put(SummaryKeys.METADATA_VERSION, SummaryKeys.VERSION);
         i5dData.put(SummaryKeys.METADATA_SOURCE, SummaryKeys.SOURCE);
         i5dData.put(SummaryKeys.NUM_FRAMES, 0);
         i5dData.put(SummaryKeys.NUM_CHANNELS, channels_.size());
         i5dData.put(SummaryKeys.NUM_SLICES, useSliceSetting_ ? sliceDeltaZ_.length : 1);
         GregorianCalendar cld = new GregorianCalendar();
         i5dData.put(SummaryKeys.TIME, cld.getTime());
         i5dData.put(SummaryKeys.COMMENT, comment_);

         ImageProcessor ip = i5dWin_[i].getImagePlus().getProcessor();

         i5dData.put(SummaryKeys.IMAGE_WIDTH, ip.getWidth());
         i5dData.put(SummaryKeys.IMAGE_HEIGHT, ip.getHeight());
         if (ip instanceof ByteProcessor)
            i5dData.put(SummaryKeys.IMAGE_DEPTH, 1);
         else if (ip instanceof ShortProcessor)
            i5dData.put(SummaryKeys.IMAGE_DEPTH, 2);

         JSONArray colors = new JSONArray();
         JSONArray names = new JSONArray();
         for (int j=0; j < channels_.size(); j++) {
            Color c = (channels_.get(j)).color_;
            colors.put(j, c.getRGB());
            names.put(j, (channels_.get(j)).config_);
         }
         i5dData.put(SummaryKeys.CHANNEL_COLORS, colors);
         i5dData.put(SummaryKeys.CHANNEL_NAMES, names);

         i5dData.put(SummaryKeys.IJ_IMAGE_TYPE, i5dWin_[i].getImagePlus().getType());

         if (useMultiplePositions_) {
            // insert position label
            i5dData.put(SummaryKeys.POSITION, posList_.getPosition(i).getLabel());
         }


         metadata_[i].put(SummaryKeys.SUMMARY, i5dData);
         System.out.println("Inserted metadata for acq: " + i);
      }
   }

   public void enableZSliceSetting(boolean b) {
      useSliceSetting_  = b;
   }
   
   public boolean isZSliceSettingEnabled() {
      return useSliceSetting_;
   }
   
   public void enableMultiPosition(boolean b) {
      useMultiplePositions_ = b;
   }
   
   public boolean isMultiPositionEnabled() {
      return useMultiplePositions_;
   }
   
   
   /**
    * Returns the currently allocated memory.
    */
   public static long currentMemory() {
      long freeMem = Runtime.getRuntime().freeMemory();
      long totMem = Runtime.getRuntime().totalMemory();
      return totMem-freeMem;
   }
   
   public static long freeMemory() {
      long maxMemory = Runtime.getRuntime().maxMemory();
      long totalMemory = Runtime.getRuntime().totalMemory();
      long freeMemory = Runtime.getRuntime().freeMemory();
      
      //System.out.println("Memory MAX=" + maxMemory + ", TOT=" + totalMemory + ", FREE=" + freememory);
      
      return maxMemory - (totalMemory - freeMemory);
   }

   /** Returns the maximum amount of memory available */
   public static long maxMemory() {
      Memory mem = new Memory();
      long maxMemory = mem.getMemorySetting();
         if (maxMemory==0L)
            maxMemory = mem.maxMemory();
      return maxMemory;
   }

   public String[] getAvailableGroups() {
      StrVector groups = core_.getAvailableConfigGroups();
      String strGroups[] = new String[(int)groups.size()];
      for (int i=0; i<groups.size(); i++) {
         strGroups[i] = groups.get(i);
      }
      return strGroups;
   }
   
   /**
    * Creates and configures the Image5d and associated window based
    * on the acquisition protocol.
    *  
    * @throws Exception
    */
   private void setupImage5d(int posIndex) throws Exception {
      imgWidth_ = core_.getImageWidth();
      imgHeight_ = core_.getImageHeight();
      imgDepth_ = core_.getBytesPerPixel();
      
      int type;
      if (imgDepth_ == 1)
         type = ImagePlus.GRAY8;
      else if (imgDepth_ == 2)
         type = ImagePlus.GRAY16;
      else
         throw new Exception("Unsupported pixel depth");
      
      // create a new Image5D object
      int numSlices = useSliceSetting_ ? sliceDeltaZ_.length : 1;
      
      if (useMultiplePositions_ && posMode_ == PositionMode.TIME_LAPSE) {
         img5d_ = new Image5D[posList_.getNumberOfPositions()]; 
         i5dWin_ = new Image5DWindow[posList_.getNumberOfPositions()];
      } else {
         img5d_ = new Image5D[1];
         i5dWin_ = new Image5DWindow[1];
      }
               
      for (int i=0; i < img5d_.length; i++) {
         img5d_[i] = new Image5D(dirName_, type, (int)imgWidth_, (int)imgHeight_, channels_.size(), numSlices, numFrames_, false);
         
         for (int j=0; j<channels_.size(); j++) {
            ChannelCalibration chcal = new ChannelCalibration();
            chcal.setLabel((channels_.get(j)).config_);
            img5d_[i].setChannelCalibration(j+1, chcal);
                     
            // set color
            img5d_[i].setChannelColorModel(j+1, ChannelDisplayProperties.createModelFromColor(((ChannelSpec)channels_.get(j)).color_));            
         }
         
         // pop-up 5d image window
         i5dWin_[i] = new Image5DWindow(img5d_[i]);
         System.out.println("Created 5D window: " + i);

         // set the desired display mode.  This needs to be called after opening the Window
         // Note that OVERLAY mode is much slower than others, so show a single channel in a fast mode
         if (channels_.size()==1)
            img5d_[i].setDisplayMode(ChannelControl.ONE_CHANNEL_COLOR);
         else
            img5d_[i].setDisplayMode(ChannelControl.OVERLAY);

         //WindowManager.addWindow(i5dWin_);
         ChannelSpec[] cs = new ChannelSpec[channels_.size()];
         for (int j=0; j<channels_.size(); j++) {
            cs[j] = channels_.get(j);
         }
         i5dWin_[i].setMMChannelData(cs);
         i5dWin_[i].setLocation(xWindowPos + i*30, yWindowPos + i*30);
         
         if (i==0) {
            // add listener to the IJ window to detect when it closes
            // (use only the first window in the multi-pos case)
            WindowListener wndCloser = new WindowAdapter() {
               public void windowClosing(WindowEvent e) {
                  Rectangle r = i5dWin_[0].getBounds();
                  // record the position of the IJ window
                  xWindowPos = r.x;
                  yWindowPos = r.y;
               }
            };      

            i5dWin_[0].addWindowListener(wndCloser);
         }
         
         // hook up with the acquistion engine
         i5dWin_[i].setAcquitionEngine(this);
         GregorianCalendar cld = new GregorianCalendar();
         startTimeMs_ = cld.getTimeInMillis();
         if (useMultiplePositions_) {
            if (posMode_ == PositionMode.TIME_LAPSE)
               // time-lapse
               i5dWin_[i].setTitle("Acquisition " + posList_.getPosition(i).getLabel() + " (started)" + cld.getTime());
            else
               // multi-field
               i5dWin_[i].setTitle("Acquisition " + posList_.getPosition(posIndex).getLabel() + " (started)" + cld.getTime());
         } else
            // single position
            i5dWin_[i].setTitle("Acquisition (started)" + cld.getTime());
         
         i5dWin_[i].setActive(true);
      }
      System.out.println("Finished setting up 5D windows");
   }

   public int getPositionMode() {
      return posMode_;
   }

   public int getSliceMode() {
      return sliceMode_;
   }

   public void setPositionMode(int mode) {
      posMode_ = mode;
   }

   public void setSliceMode(int mode) {
      sliceMode_ = mode;
   }

}
