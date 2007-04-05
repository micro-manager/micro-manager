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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.GregorianCalendar;

import javax.swing.JOptionPane;
import javax.swing.Timer;

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
   private Image5D img5d_;
   Image5DWindow i5dWin_;
   private int frameCount_;
   private int xWindowPos = 100;
   private int yWindowPos = 100;
   
   private boolean saveFiles_ = false;
   private boolean acquisitionLagging_ = false;
   private String dirName_;
   private String rootName_;
   private File outputDir_;
   
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
   private FileWriter metaWriter_;
   private JSONObject metadata_;
   private boolean absoluteZ_ = false;
   private String comment_ = "";
   private boolean useSliceSetting_ = true;
   
   // physical dimensions which should be uniform for the entire acquistion
   private long imgWidth_ = 0;
   private long imgHeight_ = 0;
   private long imgDepth_ = 0;

   private PlatformIndependentGuidGen guidgen_;

   private boolean useMultiplePositions_;

   private int posMode_ = PositionMode.MULTI_FIELD;
   private int sliceMode_ = SliceMode.CHANNELS_FIRST;
   
   private class AcqRunnable implements Runnable {
      public void run() {
         try {
            acquire();
         } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
         }
      }
   }
         
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
      metadata_ = new JSONObject();
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
   public void acquire() throws Exception{
      if (isAcquisitionRunning()) {
         throw new Exception("Busy with the current acquisition.");
      }

      // check if the parent GUI is in the adequate state
      if (parentGUI_ != null)
      {
         parentGUI_.stopAllActivity();
         if (!parentGUI_.okToAcquire())
            throw new Exception( "Unable to start acquisition.\n" +
                   "Cancel 'Live' mode or other currently executing process in the main control panel.");
      }

      oldCameraState_ = null;
      oldChannelState_ = null;
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
      
      acquisitionLagging_ = false;
      
      startAcquisition();
   }
   
   /**
    * Resets the engine.
    */
   public void clear() {
      channels_.clear();
      frameCount_ = 0;
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
      
      ActionListener timerHandler = new ActionListener() {
         public void actionPerformed(ActionEvent evt) {
            acquireOneFrame();
         }
      };
      acqTimer_ = new Timer((int)frameIntervalMs_, timerHandler);
      acqTimer_.setInitialDelay(0);
      if (numFrames_ > 0)
         acqTimer_.start();
   }

   /**
    * Acquires a single frame in the acquistion sequence.
    *
    */
   public void acquireOneFrame() {
            
      GregorianCalendar cld = null;
      GregorianCalendar cldStart = new GregorianCalendar();
      int numSlices = useSliceSetting_ ? sliceDeltaZ_.length : 1;
      
      System.out.println("Frame " + frameCount_ + " at " + GregorianCalendar.getInstance().getTime());
      for (int j=0; j<numSlices; j++) {         
         try {
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
                  setupImage5d();
                  acquisitionSetup();
                  System.out.println("Sequence size: " + imgWidth_ + "," + imgHeight_);
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
               img5d_.setPixels(img, k+1, j+1, frameCount_+1);
               if (!i5dWin_.isPlaybackRunning())
                  img5d_.setCurrentPosition(0, 0, k, j, frameCount_);
               
               // autoscale channels based on the first slice of the first frame
               if (j==0 && frameCount_==0) {
                  ImageStatistics stats = img5d_.getStatistics(); // get uncalibrated stats
                  double min = stats.min;
                  double max = stats.max;
                  img5d_.setChannelMinMax(k+1, min, max);                  
               }
                     
               i5dWin_.getImagePlus().updateAndDraw();
               i5dWin_.getCanvas().paint(i5dWin_.getCanvas().getGraphics());
               
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
               jsonData.put(ImagePropertyKeys.Z_UM, zCur);
               jsonData.put(ImagePropertyKeys.EXPOSURE_MS, exposureMs);
                              
               // insert the metadata for the current image
               metadata_.put(ImageKey.generateFrameKey(frameCount_, k, j), jsonData);
               
               if (saveFiles_) {
                  saveImageFile(outputDir_ + "/" + fname, img, (int)imgWidth_, (int)imgHeight_);
               }              
            }
         } catch(MMException e) {
            stop();
            restoreSystem();
            if (e.getMessage().length() > 0)
               JOptionPane.showMessageDialog(i5dWin_, e.getMessage());     
          return;
         } catch (Exception e) {
            stop();
            restoreSystem();
            if (e.getMessage().length() > 0)
               JOptionPane.showMessageDialog(i5dWin_, e.getMessage());     
            return;
         } catch (OutOfMemoryError e) {
            JOptionPane.showMessageDialog(i5dWin_, e.getMessage() + "\nOut of memory - acquistion stopped.\n" +
            "In the future you can try to increase the amount of memory available to the Java VM (ImageJ).");     
            stop();
            restoreSystem();
            return;       
         }
      }   
         
      // Processing for the first frame in the sequence
      if (frameCount_ == 0) {
         // insert contrast settings metadata
         try {
            JSONObject summary = metadata_.getJSONObject(SummaryKeys.SUMMARY);
            JSONArray minArray = new JSONArray();
            JSONArray maxArray = new JSONArray();
            
            // contrast settings
            for (int i=0; i<channels_.size(); i++) {
               ChannelDisplayProperties cdp = img5d_.getChannelDisplayProperties(i+1);
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
      
      // update number of frames in the summary
      frameCount_++;
      i5dWin_.startCountdown((long)frameIntervalMs_ - (cld.getTimeInMillis() - cldStart.getTimeInMillis()), numFrames_ - frameCount_);
      try {
         JSONObject summary = metadata_.getJSONObject(SummaryKeys.SUMMARY);
         summary.put(SummaryKeys.NUM_FRAMES, frameCount_);
      } catch (JSONException e) {
         // TODO Auto-generated catch block
         e.printStackTrace();
      }
      
      if(frameCount_ >= numFrames_) {
         stop();
         i5dWin_.setTitle("Acquisition (completed) " + cld.getTime()); 
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
      String metaStream = new String();
      try {
         metaStream = metadata_.toString(3);
      } catch (JSONException e1) {
         // TODO Auto-generated catch block
         e1.printStackTrace();
      }
      saveMetadata(metaStream);
         
      if (acqTimer_ != null) {
         acqTimer_.stop();
         System.out.println("Acquisition stopped!!!");
      }
      
      if (i5dWin_ != null) {
         i5dWin_.stopCountdown();
         i5dWin_.setTitle("Acquisition (aborted) ");
         i5dWin_.setMetadata(metaStream);
         i5dWin_.setActive(false);
         i5dWin_.setAcquitionEngine(null); // disengage from the acquistion
         i5dWin_.setPlaybackFrames(frameCount_);
      }
      
      // img5d_.setDimensions(img5d_.getNChannels(), img5d_.getNSlices(), this.frameCount_);
      System.out.println("Number of frames: " + (img5d_ != null ? img5d_.getNFrames() : 0));
   }

   private void saveMetadata(String meta) {     
      if (metaWriter_ != null && saveFiles_)
         try {
            metaWriter_.write(meta);
            metaWriter_.close();
            metaWriter_ = null;
         } catch (IOException e) {
            // do not complain here
            e.printStackTrace();
         }
   }
   
   public boolean isAcquisitionRunning() {
      if (acqTimer_ != null)
         return acqTimer_.isRunning();
      else
         return false;
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
   private void acquisitionSetup() throws IOException, JSONException {
      metaWriter_ = null;
      
      if (saveFiles_) {
         // create new acq directory
         int suffixCounter = 0;
         String outDirName; 
         do {
            outDirName = rootName_ + "/" + dirName_ + "_" + suffixCounter;
            outputDir_ = new File(outDirName);
            suffixCounter++;
         } while (outputDir_.exists());
         if (!outputDir_.mkdirs())
            throw new IOException("Invalid root directory name");
         
         File metaFile = new File(outDirName + "/" + ImageKey.METADATA_FILE_NAME);
         metaWriter_ = new FileWriter(metaFile);
      }
      metadata_ = new JSONObject();      
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
      
      ImageProcessor ip = i5dWin_.getImagePlus().getProcessor();
      i5dData.put(SummaryKeys.IMAGE_WIDTH, ip.getWidth());
      i5dData.put(SummaryKeys.IMAGE_HEIGHT, ip.getHeight());
      if (ip instanceof ByteProcessor)
         i5dData.put(SummaryKeys.IMAGE_DEPTH, 1);
      else if (ip instanceof ShortProcessor)
         i5dData.put(SummaryKeys.IMAGE_DEPTH, 2);
      
      JSONArray colors = new JSONArray();
      JSONArray names = new JSONArray();
      for (int i=0; i < channels_.size(); i++) {
         Color c = (channels_.get(i)).color_;
         colors.put(i, c.getRGB());
         names.put(i, (channels_.get(i)).config_);
      }
      i5dData.put(SummaryKeys.CHANNEL_COLORS, colors);
      i5dData.put(SummaryKeys.CHANNEL_NAMES, names);
         
      i5dData.put(SummaryKeys.IJ_IMAGE_TYPE, i5dWin_.getImagePlus().getType());
      
      metadata_.put(SummaryKeys.SUMMARY, i5dData);
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
   private void setupImage5d() throws Exception {
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
      img5d_ = new Image5D(dirName_, type,
            (int)imgWidth_, (int)imgHeight_, channels_.size(),
            numSlices, numFrames_, false);
      for (int i=0; i<channels_.size(); i++) {
         ChannelCalibration chcal = new ChannelCalibration();
         chcal.setLabel((channels_.get(i)).config_);
         img5d_.setChannelCalibration(i+1, chcal);
                  
         // set color
         img5d_.setChannelColorModel(i+1, ChannelDisplayProperties.createModelFromColor(((ChannelSpec)channels_.get(i)).color_));            
      }
      
      // pop-up 5d image window
      i5dWin_ = new Image5DWindow(img5d_);

      // set the desired display mode.  This needs to be called after opening the Window
      // Note that OVERLAY mode is much slower than others, so show a single channel in a fast mode
      if (channels_.size()==1)
         img5d_.setDisplayMode(ChannelControl.ONE_CHANNEL_COLOR);
      else
         img5d_.setDisplayMode(ChannelControl.OVERLAY);

      //WindowManager.addWindow(i5dWin_);
      ChannelSpec[] cs = new ChannelSpec[channels_.size()];
      for (int i=0; i<channels_.size(); i++) {
         cs[i] = channels_.get(i);
      }
      i5dWin_.setMMChannelData(cs);
      i5dWin_.setLocation(xWindowPos, yWindowPos);
      
      // add listener to the IJ window to detect when it closes
      WindowListener wndCloser = new WindowAdapter() {
         public void windowClosing(WindowEvent e) {
            Rectangle r = i5dWin_.getBounds();
            // record the position of the IJ window
            xWindowPos = r.x;
            yWindowPos = r.y;
         }
      };      

      i5dWin_.addWindowListener(wndCloser);
                
      // acquire the sequence
      i5dWin_.setAcquitionEngine(this);
      GregorianCalendar cld = new GregorianCalendar();
      i5dWin_.setTitle("Acquisition (Started) " + cld.getTime());
      startTimeMs_ = cld.getTimeInMillis();
      
      i5dWin_.setTitle("Acquisition (started)" + cld.getTime());
      i5dWin_.setActive(true);

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

   public void acquireMT() {
      new Thread(new AcqRunnable()).start();
   }

}
