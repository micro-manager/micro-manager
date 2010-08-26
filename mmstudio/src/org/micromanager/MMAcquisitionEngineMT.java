///////////////////////////////////////////////////////////////////////////////
//FILE:          MMAcquisitionEngineMT.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//AUTHOR:       Nenad Amodaj, nenad@amodaj.com, Dec 1, 2007, Arthur Edelstein, Nico Stuurman, 2008, 2009
//COPYRIGHT:    University of California, San Francisco, 2007, 2008, 2009
//              100X Imaging Inc, www.100ximaging.com, 2008
//LICENSE:      This file is distributed under the BSD license.
//License text is included with the source distribution.
//This file is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty
//of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//CVS:          $Id: MMAcquisitionEngine.java 38 2007-04-03 01:26:31Z nenad $
package org.micromanager;

import ij.IJ;
import ij.ImagePlus;
import ij.io.FileSaver;
import ij.measure.Calibration;
import ij.plugin.frame.PlugInFrame;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import ij.process.ShortProcessor;

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.awt.image.DirectColorModel;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Timer;
import java.util.TimerTask;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import mmcorej.CMMCore;
import mmcorej.Configuration;
import mmcorej.PropertySetting;
import mmcorej.StrVector;

import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.api.TaggedImageProcessor;
import org.micromanager.api.AcquisitionEngine;
import org.micromanager.api.DeviceControlGUI;
import org.micromanager.image5d.ChannelCalibration;
import org.micromanager.image5d.ChannelControl;
import org.micromanager.image5d.ChannelDisplayProperties;
import org.micromanager.image5d.Image5D;
import org.micromanager.image5d.Image5DWindow;
import org.micromanager.metadata.AcquisitionData;
import org.micromanager.metadata.DisplaySettings;
import org.micromanager.metadata.ImageKey;
import org.micromanager.metadata.ImagePropertyKeys;
import org.micromanager.metadata.MMAcqDataException;
import org.micromanager.metadata.SummaryKeys;
import org.micromanager.metadata.WellAcquisitionData;
import org.micromanager.navigation.MultiStagePosition;
import org.micromanager.navigation.PositionList;
import org.micromanager.navigation.StagePosition;
import org.micromanager.utils.Annotator;
import org.micromanager.utils.AutofocusManager;
import org.micromanager.utils.ChannelSpec;
import org.micromanager.utils.ContrastSettings;
import org.micromanager.utils.MMException;
import org.micromanager.utils.MemoryUtils;
import org.micromanager.utils.NumberUtils;
import org.micromanager.utils.PositionMode;
import org.micromanager.utils.ReportingUtils;
import org.micromanager.utils.SliceMode;

/**
 * Multi-threaded acquisition engine.
 * Runs Image5d acquisition based on the protocol and generates
 * metadata in JSON format.
 */
public class MMAcquisitionEngineMT implements AcquisitionEngine {

   public void addProcessor(TaggedImageProcessor processor) {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public void removeProcessor(TaggedImageProcessor processor) {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   // Class to hold some image metadata.  Start to refactor out some parts of Image5D
   private class ImageData {

      public long imgWidth_;
      public long imgHeight_;
      public long imgDepth_;
      //public double pixelSize_um_;
      //public double pixelAspect_;
   }
   // persistent properties (app settings) 

   private Preferences prefs_;
   protected Image5D img5d_[];
   protected Image5DWindow i5dWin_[];
   private ImageData imgData_[];
   protected String acqName_;
   private String rootName_;
   private int xWindowPos = 100;
   private int yWindowPos = 100;
   protected boolean singleFrame_ = false;
   private boolean singleWindow_ = false;
   private Timer acqTimer_;
   private AcqFrameTask acqTask_;
   protected AcquisitionData acqData_[];
   private MultiFieldThread multiFieldThread_;
   private double pixelSize_um_;
   private double pixelAspect_;
   private String fileSeparator_;
   //protected String channelGroup_;
   protected String cameraConfig_ = "";
   protected Configuration oldChannelState_;
   protected double oldExposure_ = 10.0;
   protected int numFrames_;
   protected int requestedNumFrames_;
   protected int afSkipInterval_;
   protected double frameIntervalMs_;
   protected int frameCount_;
   protected int posCount_;
   protected boolean saveFiles_ = false;
   protected boolean acquisitionLagging_ = false;
   protected CMMCore core_;
   protected PositionList posList_;
   protected DeviceControlGUI parentGUI_;
   protected String zStage_;
   protected ArrayList<ChannelSpec> channels_;
   protected ArrayList<ChannelSpec> requestedChannels_;
   protected double[] sliceDeltaZ_;
   double bottomZPos_;
   double topZPos_;
   double deltaZ_;
   protected double startZPosUm_;
   protected WellAcquisitionData well_;
   boolean absoluteZ_ = false;
   private String comment_ = "";
   protected boolean useSliceSetting_ = true;
   protected boolean keepShutterOpenForStack_ = false;
   protected boolean keepShutterOpenForChannels_ = false;
   private boolean useFramesSetting_ = false;
   private boolean useChannelsSetting_;
   // physical dimensions which should be uniform for the entire acquisition
   protected long imgWidth_ = 0;
   protected long imgHeight_ = 0;
   protected long imgDepth_ = 0;
   protected boolean useMultiplePositions_;
   protected int posMode_ = PositionMode.TIME_LAPSE;
   int sliceMode_ = SliceMode.CHANNELS_FIRST;
   protected boolean pause_ = false;
   protected int previousPosIdx_;
   protected boolean acqInterrupted_;
   protected boolean oldLiveRunning_;
   protected boolean acqFinished_;
   // auto-focus module
   private AutofocusManager afMgr_;
   private boolean autofocusEnabled_ = false;
   private boolean continuousFocusingWasEnabled_;
   private boolean autofocusHasFailed_;
   private boolean originalAutoShutterSetting_;
   private boolean shutterIsOpen_ = false;
   private String lastImageFilePath_;
   private boolean abortRequest_;

   /**
    * Timer task routine triggered at each frame. 
    */
   private class AcqFrameTask extends TimerTask {

      private boolean running_ = false;
      private boolean active_ = true;
      private boolean coreLogInitialized_ = false;

      public void run() {
         setRunning(true);

         // this is necessary because logging within core is set-up on per-thread basis
         // Timer task runs in a separate thread and unless we initialize we won't have any
         // log output when the task is executed
         if (!coreLogInitialized_) {
            core_.initializeLogging();
            coreLogInitialized_ = true;
         }

         if (pause_) {
            setRunning(false);
            return;
         }

         if (useMultiplePositions_ && posMode_ == PositionMode.TIME_LAPSE) {
            for (int i = 0; i < posList_.getNumberOfPositions(); i++) {
               if (isRunning())
                  acquireOneFrame(i);
               else
                  break;
            }
         } else {
            acquireOneFrame(posCount_);
         }

         setRunning(false);
      }

      public boolean cancel() {
         boolean ret = super.cancel();
         setActive(false);
         // The running_ field will be set to false when the
         // currently executing task iteration finishes.
         return ret;
      }

      public synchronized boolean isRunning() {
         return running_;
      }

      private synchronized void setRunning(boolean running) {
         running_ = running;
      }

      public synchronized boolean isActive() {
         return active_;
      }

      private synchronized void setActive(boolean active) {
         active_ = active;
      }
   }

   /**
    * Multi-field thread. 
    */
   private class MultiFieldThread extends Thread {

      private volatile boolean error_ = false;

      public void run() {
         posCount_ = 0;
         while (posCount_ < posList_.getNumberOfPositions()) {
            if (isError()) {
               return;
            }
            try {
               goToPosition(posCount_);
            } catch (Exception e1) {
               // TODO Auto-generated catch block
               ReportingUtils.showError(e1.getMessage());
               error_ = true;
               return;
            }
            try {
               startAcquisition();
            } catch (MMException e) {
               ReportingUtils.showError(e);
               error_ = true;
               return;
            }

            // wait until acquisition is done
            waitForAcquisitionToStop();

            if (acqInterrupted_ == true) {
               break;
            }
            posCount_++;

         }
      }

      /**
       * @param error_ the error_ to set
       */
      public void setError(boolean error_) {
         this.error_ = error_;
      }

      /**
       * @return the error_
       */
      public boolean isError() {
         return error_;
      }
   }

   /**
    * Threadsafe image5d window update.
    */
   protected class RefreshI5d implements Runnable {

      private Image5DWindow i5dWin_;

      public RefreshI5d(Image5DWindow i5dw) {
         i5dWin_ = i5dw;
      }

      public void run() {
         try {
            if (isAcquisitionRunning() && i5dWin_ != null) {
               i5dWin_.getImagePlus().updateAndDraw();
            }
            if (isAcquisitionRunning() && i5dWin_ != null) {
               i5dWin_.getCanvas().paint(i5dWin_.getCanvas().getGraphics());
            }
         } catch (NullPointerException e) {
            if (i5dWin_ != null) {
               ReportingUtils.logError(e);
            }
         }
      }
   }

   /**
    * Threadsafe image5d window shutdown.
    */
   @SuppressWarnings("unused")
   private class DisposeI5d implements Runnable {

      private Image5DWindow i5dWin_;

      public DisposeI5d(Image5DWindow i5dw) {
         i5dWin_ = i5dw;
      }

      public void run() {
         i5dWin_.close();
         //cleanup();
      }
   }

   /**
    * Multi-threaded acquisition engine.
    */
   public MMAcquisitionEngineMT() {

      channels_ = new ArrayList<ChannelSpec>();
      requestedChannels_ = new ArrayList<ChannelSpec>();

      sliceDeltaZ_ = new double[1];
      sliceDeltaZ_[0] = 0.0; // um
      bottomZPos_ = 0.0;
      topZPos_ = 0.0;
      deltaZ_ = 0.0;
      numFrames_ = 1;
      afSkipInterval_ = 0;
      frameIntervalMs_ = 1.0; // ms
      frameCount_ = 0;
      acqInterrupted_ = false;
      acqFinished_ = true;
      posCount_ = 0;
      rootName_ = new String(DEFAULT_ROOT_NAME);
      posList_ = new PositionList();
      afMgr_ = null; // instantiated later when core_ becomes available

      // fileSeparator_ is used for display in Window title 
      fileSeparator_ = System.getProperty("file.separator");
      if (fileSeparator_ == null) {
         fileSeparator_ = "/";
      }
   }

   public void setCore(CMMCore c, AutofocusManager afmgr) {
      core_ = c;
      afMgr_ = afmgr;
   }

   public void setPositionList(PositionList posList) {
      posList_ = posList;
   }

   public ArrayList<ChannelSpec> getChannels() {
      return requestedChannels_;
   }

   public void setChannels(ArrayList<ChannelSpec> ch) {
      requestedChannels_ = ch;

      // if the list empty create one "dummy" channel
      if (requestedChannels_.size() == 0 || !useChannelsSetting_) {
         channels_ = new ArrayList<ChannelSpec>();
         ChannelSpec cs = new ChannelSpec();
         try {
            cs.exposure_ = core_.getExposure();
         } catch (Exception e) {
            ReportingUtils.logError(e);
            cs.exposure_ = 10.0;
            ;
         }
         channels_.add(cs);
      } else {
         channels_ = requestedChannels_;
      }
   }

   /**
    * Set the channel group if the current hardware configuration permits.
    * @param group
    * @return - true if successful
    */
   public boolean setChannelGroup(String group) {
      if (groupIsEligibleChannel(group)) {
         try {
            core_.setChannelGroup(group);
         } catch (Exception e) {
            try {
               core_.setChannelGroup("");
            } catch (Exception ex) {
               ReportingUtils.showError(e);
            }
            return false;
         }
         return true;
      } else {
         return false;
      }
   }

   public String getChannelGroup() {
      return core_.getChannelGroup();
   }

   public void setParentGUI(DeviceControlGUI parent) {
      parentGUI_ = parent;
   }

   /**
    * Starts acquisition, based on the current protocol.
    * @throws MMAcqDataException 
    * @throws Exception
    */
   public void acquire() throws MMException, MMAcqDataException {
      cleanup();
      
      zStage_ = core_.getFocusDevice();
      pause_ = false; // clear pause flag
      autofocusHasFailed_ = false;

      
      // check conditions for starting acq.
      if (isAcquisitionRunning()) {
         throw new MMException("Busy with the current acquisition.");
      }

      numFrames_ = useFramesSetting_ ? requestedNumFrames_ : 1;

      if (useMultiplePositions_ && (posList_ == null || posList_.getNumberOfPositions() < 1)) {
         throw new MMException("\"Multiple positions\" is selected but position list is not defined");
      }

      if (posMode_ == PositionMode.MULTI_FIELD && !saveFiles_) {
         ReportingUtils.showMessage("To use \"Time first\" mode, you must check the box\nlabeled \"Save image files to acquisition directory.\"");
         return;
      }

      // check if the parent GUI is in the adequate state
      if (parentGUI_ != null) {
         oldLiveRunning_ = parentGUI_.getLiveMode();
         parentGUI_.stopAllActivity();
         if (!parentGUI_.okToAcquire()) {
            throw new MMException("Unable to start acquisition.\n"
                    + "Cancel 'Live' mode or other currently executing process in the main control panel.");
         }
      }

      oldChannelState_ = null;
      try {
         oldExposure_ = core_.getExposure();
         String channelConfig = core_.getCurrentConfig(core_.getChannelGroup());
         if (channelConfig.length() > 0) {
            oldChannelState_ = core_.getConfigGroupState(core_.getChannelGroup());
         }

         if (useChannelsSetting_ && (cameraConfig_.length() > 0)) {
            core_.getConfigState(cameraGroup_, cameraConfig_);
            core_.setConfig(cameraGroup_, cameraConfig_);
         }

         // wait until all devices are ready
         core_.waitForSystem();
      } catch (Exception e) {
         throw new MMException(e.getMessage());
      }

      if (autofocusEnabled_ && afMgr_.getDevice() == null) {
         throw new MMException("Auto-focus module was not loaded.\n"
                 + "Auto-focus option can not be used in this context.");

      }

      continuousFocusingWasEnabled_ = false;
      if (autofocusEnabled_) {
         continuousFocusingWasEnabled_ = afMgr_.getDevice().isContinuousFocusEnabled();
         if (continuousFocusingWasEnabled_) {
            afMgr_.getDevice().enableContinuousFocus(false);
         }
      }

      acquisitionLagging_ = false;
      posCount_ = 0;

      well_ = null;
      if (useMultiplePositions_) {
         // create metadata structures
         well_ = new WellAcquisitionData();
         if (saveFiles_) {
            well_.createNew(acqName_, rootName_, true); // disk mapped
         } else {
            well_.createNew(rootName_, true); // memory mapped
         }
         if (posMode_ == PositionMode.TIME_LAPSE) {
            startAcquisition();
         } else {
            multiFieldThread_ = new MultiFieldThread();
            multiFieldThread_.start();
         }
      } else {
         startAcquisition();
      }
   }

   /**
    * Starts acquisition of a single well, based on the current protocol, using the supplied
    * acquisition data structure.
    * This command is specially designed for plate scanning and will automatically re-set
    * all appropriate parameters.
    * @return 
    * @throws MMAcqDataException 
    * @throws Exception
    */
   public boolean acquireWellScan(WellAcquisitionData wad) throws MMException, MMAcqDataException {
      boolean result = true;
      try {
         zStage_ = core_.getFocusDevice();
      } catch (Exception e1) {
         result = false;
         ReportingUtils.showError("Error in focus device");
         return result;
      }
      pause_ = false; // clear pause flag

      // force settings adequate for the well scanning
      useMultiplePositions_ = true;
      posMode_ = PositionMode.MULTI_FIELD;
      saveFiles_ = true;

      // check conditions for starting acq.
      if (isAcquisitionRunning()) {
         throw new MMException("Busy with the current acquisition.");
      }

      if (posList_ == null || posList_.getNumberOfPositions() < 1) {
         throw new MMException("Multiple position mode is selected but position list is not defined");
      }

      // check if the parent GUI is in the adequate state
      if (parentGUI_ != null) {
         oldLiveRunning_ = parentGUI_.getLiveMode();
         parentGUI_.stopAllActivity();
         if (!parentGUI_.okToAcquire()) {
            throw new MMException("Unable to start acquisition.\n"
                    + "Cancel 'Live' mode or other currently executing process in the main control panel.");
         }
      }

      oldChannelState_ = null;
      try {
         oldExposure_ = core_.getExposure();
         String channelConfig = core_.getCurrentConfig(core_.getChannelGroup());
         if (channelConfig.length() > 0) {
            oldChannelState_ = core_.getConfigGroupState(core_.getChannelGroup());
         }

         if (cameraConfig_.length() > 0) {
            core_.getConfigState(cameraGroup_, cameraConfig_);
            core_.setConfig(cameraGroup_, cameraConfig_);
         }

         // wait until all devices are ready
         core_.waitForSystem();
      } catch (Exception e) {
         ReportingUtils.logError(e);
         result = false;
         return result;
         //throw new MMException(e.getMessage());
      }

      if (autofocusEnabled_ && afMgr_.getDevice() == null) {
         throw new MMException("Auto-focus module was not loaded.\n"
                 + "Auto-focus option can not be used in this context.");
      }

      acquisitionLagging_ = false;
      posCount_ = 0;

      well_ = wad;
      multiFieldThread_ = new MultiFieldThread();
      multiFieldThread_.start();
      while (multiFieldThread_.isAlive()) {
         try {
            Thread.sleep(50);
         } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
         }
      }
      if (multiFieldThread_.isError()) {

         result = false;
         return result;
      }
      return result;
   }

   /**
    * Resets the engine.
    */
   public void clear() {
      channels_.clear();
      frameCount_ = 0;
      posCount_ = 0;
   }

   /**
    * Add new channel if the current state of the hardware permits.
    * 
    * @param config - configuration name
    * @param exp
    * @param doZOffst
    * @param zOffset
    * @param c8
    * @param c16
    * @param c
    * @return - true if successful
    */
   public boolean addChannel(String config, double exp, Boolean doZStack, double zOffset, ContrastSettings c8, ContrastSettings c16, int skip, Color c) {
      if (isConfigAvailable(config)) {
         ChannelSpec channel = new ChannelSpec();
         channel.config_ = config;
         channel.exposure_ = exp;
         channel.doZStack_ = doZStack;
         channel.zOffset_ = zOffset;
         channel.contrast8_ = c8;
         channel.contrast16_ = c16;
         channel.color_ = c;
         channel.skipFactorFrame_ = skip;
         requestedChannels_.add(channel);
         return true;
      } else {
         return false;
      }
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
    * @deprecated 
    */
   public boolean addChannel(String config, double exp, double zOffset, ContrastSettings c8, ContrastSettings c16, int skip, Color c) {
      return addChannel(config, exp, true, zOffset, c8, c16, skip, c);
   }

   public void setFrames(int numFrames, double deltaT) {
      requestedNumFrames_ = numFrames;
      frameIntervalMs_ = deltaT;
      numFrames_ = useFramesSetting_ ? requestedNumFrames_ : 1;
   }

   public int getCurrentFrameCount() {
      return frameCount_;
   }

   public void setSlices(double bottom, double top, double zStep, boolean absolute) {
      absoluteZ_ = absolute;
      bottomZPos_ = bottom;
      topZPos_ = top;
      zStep = Math.abs(zStep);
      deltaZ_ = zStep;

      int numSlices = 0;
      if (Math.abs(zStep) >= getMinZStepUm()) {
         numSlices = (int) (Math.abs(top - bottom) / zStep + 0.5) + 1;
      }
      sliceDeltaZ_ = new double[numSlices];
      for (int i = 0; i < sliceDeltaZ_.length; i++) {
         if (topZPos_ > bottomZPos_) {
            sliceDeltaZ_[i] = bottom + deltaZ_ * i;
         } else {
            sliceDeltaZ_[i] = bottom - deltaZ_ * i;
         }
      }
      if (numSlices == 0) {
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
    * Get first available config group
    */
   public String getFirstConfigGroup() {
      if (core_ == null) {
         return new String("");
      }

      String[] groups = getAvailableGroups();

      if (groups == null || groups.length < 1) {
         return new String("");
      }

      return getAvailableGroups()[0];
   }

   /**
    * Find out which channels are currently available for the selected channel group.
    * @return - list of channel (preset) names
    */
   public String[] getChannelConfigs() {
      if (core_ == null) {
         return new String[0];
      }
      return core_.getAvailableConfigs(core_.getChannelGroup()).toArray();
   }

   /**
    * Find out if the configuration is compatible with the current group.
    * This method should be used to verify if the acquisition protocol is consistent
    * with the current settings.
    */
   public boolean isConfigAvailable(String config) {
      StrVector vcfgs = core_.getAvailableConfigs(core_.getChannelGroup());
      for (int i = 0; i < vcfgs.size(); i++) {
         if (config.compareTo(vcfgs.get(i)) == 0) {
            return true;
         }
      }
      return false;
   }

   public String[] getCameraConfigs() {
      if (core_ == null) {
         return new String[0];
      }
      return core_.getAvailableConfigs(cameraGroup_).toArray();
   }

   public int getNumFrames() {
      return requestedNumFrames_;
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
      requestedChannels_.set(row, channel);
   }

   public void setUpdateLiveWindow(boolean update) {
   }

   public boolean isAcquisitionLagging() {
      return acquisitionLagging_;
   }

   public void setCameraConfig(String cfg) {
      cameraConfig_ = cfg;
   }

   /**
    * Starts the acquisition.
    * @throws MMException 
    */
   public void startAcquisition() throws MMException {
      previousPosIdx_ = -1; // initialize
      acqInterrupted_ = false;
      acqFinished_ = false;
      frameCount_ = 0;
      Runtime.getRuntime().gc();
      for (int i = 0; i < channels_.size(); i++) {
         channels_.get(i).min_ = 65535;
         channels_.get(i).max_ = 0;
      }

      if (!saveFiles_ || (saveFiles_ && !singleWindow_)) {
         // estimate if there is enough RAM for this acquisition
         long freeBytes = MemoryUtils.freeMemory();
         int numSlices = useSliceSetting_ ? sliceDeltaZ_.length : 1;
         int numPositions = useMultiplePositions_ ? posList_.getNumberOfPositions() : 1;
         int numFrames = singleFrame_ ? 1 : numFrames_;
         long requiredBytes = (long) numSlices * channels_.size() * numFrames * numPositions * core_.getImageWidth() * core_.getImageHeight() * core_.getBytesPerPixel() + 10000000;
         ReportingUtils.logMessage("Remaining memory " + freeBytes + " bytes. Required: " + requiredBytes);
         if (freeBytes < requiredBytes) {
            JOptionPane.showMessageDialog(null, "Not enough memory to complete this MD Acquisition.");
            return;
         }
      }

      try {
         if (isFocusStageAvailable()) {
            startZPosUm_ = core_.getPosition(zStage_);
         } else {
            startZPosUm_ = 0;
         }
      } catch (Exception e) {
         //i5dWin_.setTitle("Acquisition (error)");
         ReportingUtils.showError(e);
         throw new MMException(e.getMessage());
      }

      acqTimer_ = new Timer();
      acqTask_ = new AcqFrameTask();
      // a frame interval of 0 ms does not make sense to the timer.  set it to the smallest possible value
      if (frameIntervalMs_ < 1) {
         frameIntervalMs_ = 1;
      }
      if (numFrames_ > 0) {
         acqTimer_.schedule(acqTask_, 0, (long) frameIntervalMs_);
      }

   }

   /**
    * Acquires a single frame in the acquisition sequence.
    *
    */
   public void acquireOneFrame(int posIdx) {
      while(isPaused()) {
            try {
                Thread.sleep(5);
            } catch (InterruptedException ex) {
                ; // Do nothing.
            }
      }
            
      GregorianCalendar cldStart = new GregorianCalendar();
      int numSlices = useSliceSetting_ ? sliceDeltaZ_.length : 1;
      boolean abortWasRequested = false;

      int posIndexNormalized;
      if (!useMultiplePositions_ || posMode_ == PositionMode.TIME_LAPSE /* Positions first */) {
         posIndexNormalized = posIdx;
      } else {
         posIndexNormalized = 0; // Single position or time first
      }
      // move to the required position
      try {
         MultiStagePosition pos = null;
         if (useMultiplePositions_ /* && posMode_ == PositionMode.TIME_LAPSE */) {
            // time lapse logic
            if (posIdx != previousPosIdx_) {
               goToPosition(posIdx);
            } else {
               pos = posList_.getPosition(previousPosIdx_);
            }
         }

         performAutofocus(pos, posIdx);

         refreshZPosition();
         
         // do "before frame" notification
         core_.acqBeforeFrame();

         // flag that tells us whether the stage moves during channel/slice acquisition
         boolean zStageMoves;
         if (numSlices > 0) {
            zStageMoves = true;
         } else {
            zStageMoves = false;
         }
         originalAutoShutterSetting_ = core_.getAutoShutter();
         if (sliceMode_ == SliceMode.CHANNELS_FIRST) {
            if (originalAutoShutterSetting_ && keepShutterOpenForChannels_)
               core_.setAutoShutter(false);

            for (int j = 0; j < numSlices; j++) {
               double z = startZPosUm_;

               if (useSliceSetting_) {
                  if (absoluteZ_) {
                     z = sliceDeltaZ_[j];
                  } else {
                     z = startZPosUm_ + sliceDeltaZ_[j];
                  }
               }

               if (isFocusStageAvailable() && numSlices > 1) {
                  core_.setPosition(zStage_, z);
               }

               for (int k = 0; k < channels_.size(); k++) {
                  ChannelSpec cs = channels_.get(k);
                  if (abortRequest_){
                    break;
                  }
                  executeProtocolBody(cs, z, j, k, posIdx, numSlices, posIndexNormalized);

                  // signal that the z stage was moved
                  if (cs.zOffset_ != 0.0) {
                     zStageMoves = true;
                  }
               }
               if (abortRequest_){
                 abortRequest_ = false;
                 abortWasRequested = true;

                 break;
               }

            }
         } else if (sliceMode_ == SliceMode.SLICES_FIRST) {

            if (originalAutoShutterSetting_ && keepShutterOpenForStack_)
               core_.setAutoShutter(false);

            for (int k = 0; k < channels_.size(); k++) {
               ChannelSpec cs = channels_.get(k);


               for (int j = 0; j < numSlices; j++) {
                  double z = startZPosUm_;
                  if (abortRequest_){
                    break;
                  }



                  if (useSliceSetting_) {
                     if (absoluteZ_) {
                        z = sliceDeltaZ_[j];
                     } else {
                        z = startZPosUm_ + sliceDeltaZ_[j];
                     }
                  }

                  if (isFocusStageAvailable() && numSlices > 1) {
                     core_.setPosition(zStage_, z);
                  }


                  executeProtocolBody(cs, z, j, k, posIdx, numSlices, posIndexNormalized);

                  // signal that the z stage was moved
                  if (cs.zOffset_ != 0.0) {
                     zStageMoves = true;
                  }

               }
               if (abortRequest_){
                 abortRequest_ = false;
                 abortWasRequested = true;
                 break;
               }

            }
         } else {
            throw new MMException("Unrecognized slice mode: " + sliceMode_);
         }

         if (originalAutoShutterSetting_) {
             core_.setAutoShutter(true);
             core_.setShutterOpen(false);
         }

         // return to the starting position, but only if the hardware focus is OFF 
         if (isFocusStageAvailable() && afMgr_.getDevice() != null && !afMgr_.getDevice().isContinuousFocusEnabled()) {
            core_.setPosition(zStage_, startZPosUm_);
            core_.waitForDevice(zStage_);
         }

         if (isFocusStageAvailable() && zStageMoves) {
            // return to the Z starting position if slices were acquired or if z-offsets were applied
            core_.setPosition(zStage_, startZPosUm_);
            core_.waitForDevice(zStage_);
         }
         
         // do "after frame" notification
         core_.acqAfterFrame();

      } catch (MMException e) {
         terminate();
         ReportingUtils.showError(e.getMessage());
         return;
      } catch (OutOfMemoryError e) {
         terminate();
         ReportingUtils.showError("Out of memory - acquisition stopped.\n"
                 + "In the future you can try to increase the amount of \nmemory available to the Java VM (ImageJ).");
         return;
      } catch (IOException e) {
         terminate();
         ReportingUtils.showError(e, "IOException");
         return;
      } catch (JSONException e) {
         terminate();
         ReportingUtils.showError(e, "JSONException");
         return;
      } catch (Exception e) {
         e.printStackTrace();
         terminate();
         ReportingUtils.showError(e, "Exception");
         return;
      }

      // Processing for the first frame in the sequence
      if (frameCount_ == 0) {
         insertContrastSettingsMetadata(posIndexNormalized);
      }

      setupImage5DWindowCountdown(cldStart, posIndexNormalized);

      try {
         if (acqData_ != null && acqData_.length > 0) {
            acqData_[posIndexNormalized].setDimensions(frameCount_ + 1, channels_.size(), useSliceSetting_ ? sliceDeltaZ_.length : 1);
         }
      } catch (MMAcqDataException e) {
         ReportingUtils.logError(e, "MMAcqDataException");
      }

      // update frame counter
      if (useMultiplePositions_ && posMode_ == PositionMode.TIME_LAPSE) {
         if (posIdx == posList_.getNumberOfPositions() - 1) {
            frameCount_++;
         }
      } else {
         frameCount_++;
      }

      // check the termination criterion
      if (abortWasRequested || (frameCount_ >= numFrames_)) {
         terminate();
      }
   }

   public void terminate() {
      // acquisition finished

      // since terminate is called from several catch blocks, don't propagate throwables

      try {
         stop(false);

         if (singleWindow_) {
            String statusLine = "Acquisition Completed";
            parentGUI_.displayStatusLine(statusLine);
         } else {
            // TODO: since stop sets i5dWin_ to null, the following has no effect!
            // adjust the title
            //setImageNames(posIdx, " (completed) ");
         }

         // return to initial state
         restoreSystem();
         acqFinished_ = true;
         if (posMode_ != PositionMode.MULTI_FIELD) {
            //cleanup();
         }

      }catch(Throwable tt){
         ReportingUtils.showError(tt.getMessage());
      }

   }

   /**
    * @param posIndexNormalized
    */
   protected void insertContrastSettingsMetadata(int posIndexNormalized) {
      // insert contrast settings metadata
      try {
         // contrast settings
         if (img5d_.length > 0 && acqData_.length > 0) {
            for (int i = 0; i < channels_.size(); i++) {

               int index = getAvailablePosIndex(posIndexNormalized);

               ChannelDisplayProperties cdp = img5d_[index].getChannelDisplayProperties(i + 1);
               DisplaySettings ds = new DisplaySettings();
               ds.min = cdp.getMinValue();
               ds.max = cdp.getMaxValue();
               acqData_[posIndexNormalized].setChannelDisplaySetting(i, ds);

            }
         }
      } catch (MMAcqDataException e) {
         ReportingUtils.logError(e);
      }
   }

   /**
    * @param posIndexNormalized
    * @return
    */
   protected int getAvailablePosIndex(int posIndexNormalized) {
      int index = 0;
      if (null != img5d_) {
         if (0 < img5d_.length) {
            index = (null != img5d_[posIndexNormalized])
                    ? posIndexNormalized
                    : 0;
         }
      }
      return index;
   }

   /**
    * @param cldStart
    * @param posIndexNormalized
    */
   protected void setupImage5DWindowCountdown(GregorianCalendar cldStart,
           int posIndexNormalized) {
      if (i5dWin_ != null && i5dWin_.length > 0 && i5dWin_[posIndexNormalized] != null) {
         i5dWin_[posIndexNormalized].startCountdown((long) frameIntervalMs_ - (GregorianCalendar.getInstance().getTimeInMillis() - cldStart.getTimeInMillis()), numFrames_ - frameCount_);
      }
   }

   static public boolean saveImageFile(String fname, Object img, int width, int height) {
      ImageProcessor ip;
      if (img instanceof byte[]) {
         ip = new ByteProcessor(width, height);
         ip.setPixels((byte[]) img);
      } else if (img instanceof short[]) {
         ip = new ShortProcessor(width, height);
         ip.setPixels((short[]) img);
      }else if (img instanceof int[]){
         ip = new ColorProcessor(width, height);
         ip.setPixels((int[])img);

      } else {
         return false;
      }

      ImagePlus imp = new ImagePlus(fname, ip);
      FileSaver fs = new FileSaver(imp);
      return fs.saveAsTiff(fname);
   }

   public void restoreSystem() {
      try {
         core_.setExposure(oldExposure_);

         // The following might be superfluous, since the stage already returned to its original position
         // Not sure if it is always the case, so leave it in until that is assured.  This movement will 
         // kill the continuous focus, so test for its state first
         if (isFocusStageAvailable() && (afMgr_.getDevice() == null || !afMgr_.getDevice().isContinuousFocusEnabled())) {
            core_.setPosition(zStage_, startZPosUm_);
            core_.waitForDevice(zStage_);
         }

         if (oldChannelState_ != null) {
            core_.setSystemState(oldChannelState_);
            core_.waitForSystem();
            if (oldLiveRunning_) {
               parentGUI_.enableLiveMode(true);
            }
         }

         if (autofocusEnabled_) {
            afMgr_.getDevice().enableContinuousFocus(continuousFocusingWasEnabled_);
         }

         core_.waitForSystem();

      } catch (Exception e) {
         ReportingUtils.logError(e);
      }
   }

   public String getVerboseSummary() {
      int numFrames = requestedNumFrames_;
      if (!useFramesSetting_) {
         numFrames = 1;
      }
      int numSlices = useSliceSetting_ ? sliceDeltaZ_.length : 1;
      if (!useSliceSetting_) {
         numSlices = 1;
      }
      int numPositions = Math.max(1, posList_.getNumberOfPositions());
      if (!useMultiplePositions_) {
         numPositions = 1;
      }
      int numChannels = requestedChannels_.size();
      if (!useChannelsSetting_) {
         numChannels = 1;
      }


      int totalImages = numFrames * numSlices * numChannels * numPositions;
      double totalDurationSec = frameIntervalMs_ * numFrames / 1000.0;
      int hrs = (int) (totalDurationSec / 3600);
      double remainSec = totalDurationSec - hrs * 3600;
      int mins = (int) (remainSec / 60);
      remainSec = remainSec - mins * 60;

      Runtime rt = Runtime.getRuntime();
      rt.gc();

      String txt;
      txt =
              "Number of time points: " + numFrames
              + "\nNumber of positions: " + numPositions
              + "\nNumber of slices: " + numSlices
              + "\nNumber of channels: " + numChannels
              + "\nTotal images: " + totalImages
              + "\nDuration: " + hrs + "h " + mins + "m " + NumberUtils.doubleToDisplayString(remainSec) + "s";
      String order = "\nOrder: ";
      String ptSetting = null;
      if (useMultiplePositions_ && useFramesSetting_) {
         if (posMode_ == PositionMode.TIME_LAPSE) {
            ptSetting = "Position, Time";

         } else if (posMode_ == PositionMode.MULTI_FIELD) {
            ptSetting = "Time, Position";
         }
      } else if (useMultiplePositions_ && !useFramesSetting_) {
         ptSetting = "Position";
      } else if (!useMultiplePositions_ && useFramesSetting_) {
         ptSetting = "Time";
      }

      String csSetting = null;

      if (useSliceSetting_ && useChannelsSetting_) {
         if (sliceMode_ == SliceMode.CHANNELS_FIRST) {
            csSetting = "Channel, Slice";
         } else {
            csSetting = "Slice, Channel";
         }
      } else if (useSliceSetting_ && !useChannelsSetting_) {
         csSetting = "Slice";
      } else if (!useSliceSetting_ && useChannelsSetting_) {
         csSetting = "Channel";
      }

      if (ptSetting == null && csSetting == null) {
         order = "";
      } else if (ptSetting != null && csSetting == null) {
         order += ptSetting;
      } else if (ptSetting == null && csSetting != null) {
         order += csSetting;
      } else if (ptSetting != null && csSetting != null) {
         order += csSetting + ", " + ptSetting;
      }

      return txt + order;
   }

   public void stop(boolean interrupted) {

      core_.setAutoShutter(originalAutoShutterSetting_);
      try {
         if (core_.getAutoShutter()) {
            core_.setShutterOpen(false);
         }
      } catch (Throwable tt) {
      }



      acqInterrupted_ = interrupted;

      if (acqTask_ != null) {
         acqTask_.cancel();
         if (!acqInterrupted_) {
            acqTask_.setRunning(false);
         }
         // wait until task finishes
         waitForAcquisitionToStop();
      }

      if (i5dWin_ == null) {
         return;
      }

      if (acqData_ == null) {
         return;
      }

      for (int i = 0; i < i5dWin_.length; i++) {
         if (saveFiles_ && acqData_[i] != null) {
            try {
               acqData_[i].saveMetadata();
            } catch (MMAcqDataException e) {
               ReportingUtils.showError(e);
            }
         }

         if (i5dWin_ != null && i5dWin_.length > 0 && i5dWin_[i] != null) {
            i5dWin_[i].stopCountdown();
            Date enddate = GregorianCalendar.getInstance().getTime();
            if (useMultiplePositions_ && (well_ != null)) {
               i5dWin_[i].setTitle(well_.getLabel() + fileSeparator_ + posList_.getPosition(i).getLabel() + " (finished) " + enddate);
              // todo figure out exactly what inside ContrastAdjuster.setup and ContrastAdjuster.setupNewImage (presumably getSnapshotPixels) is needed here

              Object  ca =  IJ.runPlugIn("ij.plugin.frame.ContrastAdjuster", "");
               i5dWin_[i].getImagePlus().changes = true;
               if(ca instanceof PlugInFrame ) {
                  ((PlugInFrame)ca).close(); //
               }
            } else if (acqData_[i] != null) {
               i5dWin_[i].setTitle(acqData_[i].getName() + " (finished) " + enddate);
              Object  ca =  IJ.runPlugIn("ij.plugin.frame.ContrastAdjuster", "");
               i5dWin_[i].getImagePlus().changes = true;
               if(ca instanceof PlugInFrame ) {
                  ((PlugInFrame)ca).close();
               }
            }

            if (acqData_.length > i && acqData_[i] != null) {
               i5dWin_[i].setAcquisitionData(acqData_[i]);
            } else {
               ReportingUtils.logError("AcqData for position " + i + " were null");
            }
            i5dWin_[i].setActive(false);
            i5dWin_[i].setAcquitionEngine(null); // disengage from the acquisition
            i5dWin_[i].setPlaybackFrames(frameCount_);
         }
      }
      if ((posMode_ != PositionMode.MULTI_FIELD) || (posCount_ >= posList_.getNumberOfPositions())) {
         //cleanup();
      }
   }

   public boolean isAcquisitionRunning() {
      if (acqTask_ == null) {
         return false;
      }

      return acqTask_.isActive() || acqTask_.isRunning();
   }

   public boolean isMultiFieldRunning() {
      return multiFieldThread_.isAlive();
   }

   protected boolean isFocusStageAvailable() {
      if (zStage_ != null && zStage_.length() > 0) {
         return true;
      } else {
         return false;
      }
   }

   /**
    * Unconditional shutdown
    *
    */
   public void shutdown() {
      if (isAcquisitionRunning()) {
         stop(false);
      }
      if (multiFieldThread_ != null) {
         multiFieldThread_.interrupt();
      }
      // Wait for Acquisition engine to finish:
      acqFinished_ = true;
      //cleanup();
   }

   protected void cleanup() {
      waitForAcquisitionToStop();
      core_.logMessage("cleaning up acquisition engine");
      if (singleWindow_ && i5dWin_ != null && i5dWin_.length > 0 && i5dWin_[0] != null) {
         i5dWin_[0].close();
      }
      i5dWin_ = new Image5DWindow[0];
      img5d_ = new Image5D[0];
      acqData_ = new AcquisitionData[0];
      well_ = null;
      abortRequest_ = false;
   }

   private void waitForAcquisitionToStop() {
      while (isAcquisitionRunning()) {
         try {
            Thread.sleep(5);
         } catch (InterruptedException ex) {
            ReportingUtils.logError(ex);
         }
      }
   }

   public double getCurrentZPos() {
      if (isFocusStageAvailable()) {
         double z = 0.0;
         try {
            //core_.waitForDevice(zStage_);
            // NS: make sure we work with the current Focus device
            z = core_.getPosition(core_.getFocusDevice());
         } catch (Exception e) {
            // TODO Auto-generated catch block
            ReportingUtils.showError(e);
         }
         return z;
      }
      return 0;
   }

   public double getMinZStepUm() {
      // TODO: obtain this information from hardware
      // hard-coded to 0.01 um
      return 0.01;
   }

   public void setDirName(String dirName_) {
      this.acqName_ = dirName_;
   }

   public String getDirName() {
      return acqName_;
   }

   public void setRootName(String rootName_) {
      this.rootName_ = rootName_;
   }

   public String getRootName() {
      return rootName_;
   }

   public void setSaveFiles(boolean saveFiles) {
      saveFiles_ = saveFiles;
   }

   public boolean getSaveFiles() {
      return saveFiles_;
   }

   // depreciated
   public void setSingleFrame(boolean singleFrame) {
      singleFrame_ = singleFrame;
   }
   //
   // depreciated

   public void setSingleWindow(boolean singleWindow) {
      singleWindow_ = singleWindow;
   }

   public void setDisplayMode(int mode) {
      if (mode == 0) {
         singleFrame_ = false;
         singleWindow_ = false;
      } else if (mode == 1) {
         singleFrame_ = true;
         singleWindow_ = false;
      } else if (mode == 2) {
         singleFrame_ = false;
         singleWindow_ = true;
      }
   }

   public int getDisplayMode() {
      if (singleFrame_) {
         return 1;
      }
      if (singleWindow_) {
         return 2;
      }
      return 0;
   }

   public void setParameterPreferences(Preferences prefs) {
      prefs_ = prefs;
   }

   /**
    * Acquisition directory set-up.
    * @throws IOException 
    * @throws JSONException 
    * @throws MMAcqDataException 
    */
   protected void acquisitionDirectorySetup(int posIdx) throws IOException, MMAcqDataException {
      if (useMultiplePositions_) {
         if (posMode_ == PositionMode.TIME_LAPSE) {
            acqData_ = new AcquisitionData[posList_.getNumberOfPositions()];
            for (int i = 0; i < acqData_.length; i++) {
               if (saveFiles_) {
                  acqData_[i] = well_.createNewImagingSite(posList_.getPosition(i).getLabel(), false);
               } else {
                  acqData_[i] = well_.createNewImagingSite();
               }
            }
         } else {
            acqData_ = new AcquisitionData[1];
            if (saveFiles_) {
               acqData_[0] = well_.createNewImagingSite(posList_.getPosition(posIdx).getLabel(), false);
            } else {
               acqData_[0] = well_.createNewImagingSite();
            }
         }

      } else {
         acqData_ = new AcquisitionData[1];
         acqData_[0] = new AcquisitionData();
         if (saveFiles_) {
            acqData_[0].createNew(acqName_, rootName_, true); // disk mapped
         } else {
            acqData_[0].createNew(); // memory mapped
         }
      }

      // save a copy of the acquisition parameters
      if (saveFiles_) {
         for (int i = 0; i < acqData_.length; i++) {
            // TODO: do this elsewhere
            //i5dWin_[i].setAcqSavePath(acqData_[i].getBasePath());
            FileOutputStream os = new FileOutputStream(acqData_[i].getBasePath() + "/" + ImageKey.ACQUISITION_FILE_NAME);

            if (prefs_ != null) {
               try {
                  prefs_.exportNode(os);
               } catch (BackingStoreException e) {
                  // TODO Auto-generated catch block
                  ReportingUtils.showError(e);
               }
            }
         }
      }

      for (int i = 0; i < imgData_.length; i++) {
         int index = (null != imgData_[i]) ? i : 0;
         /*
         ImageProcessor ip = i5dWin_[index].getImagePlus().getProcessor();
         int imgDepth = 0;
         if (ip instanceof ByteProcessor)
         imgDepth = 1;
         else if (ip instanceof ShortProcessor)
         imgDepth = 2;
          */

         //acqData_[i].setImagePhysicalDimensions(ip.getWidth(), ip.getHeight(), imgDepth);
         acqData_[i].setImagePhysicalDimensions((int) imgData_[index].imgWidth_,
                 (int) imgData_[index].imgHeight_, (int) imgData_[index].imgDepth_);
         acqData_[i].setDimensions(0, channels_.size(), useSliceSetting_ ? sliceDeltaZ_.length : 1);
         acqData_[i].setComment(comment_);
         acqData_[i].setPixelSizeUm(pixelSize_um_);
         acqData_[i].setImageIntervalMs(frameIntervalMs_);
         acqData_[i].setSummaryValue(SummaryKeys.IMAGE_Z_STEP_UM, Double.toString(deltaZ_));
         acqData_[i].setSummaryValue(SummaryKeys.IMAGE_PIXEL_ASPECT, Double.toString(pixelAspect_));

         for (int j = 0; j < channels_.size(); j++) {
            Color c = channels_.get(j).color_;
            acqData_[i].setChannelColor(j, c.getRGB());
            acqData_[i].setChannelName(j, channels_.get(j).config_);
         }

         if (useMultiplePositions_) {
            MultiStagePosition mps = posList_.getPosition(posIdx);

            // insert position label
            acqData_[i].setSummaryValue(SummaryKeys.POSITION, mps.getLabel());

            // insert grid coordinates
            acqData_[i].setSummaryValue(SummaryKeys.GRID_ROW, Integer.toString(mps.getGridRow()));
            acqData_[i].setSummaryValue(SummaryKeys.GRID_COLUMN, Integer.toString(mps.getGridColumn()));

            // insert properties inherited from the position list
            String keys[] = mps.getPropertyNames();
            for (int k = 0; k < keys.length; k++) {
               // insert them in the position property space 
               acqData_[i].setPositionProperty(keys[k], mps.getProperty(keys[k]));

               // and also in the summary
               acqData_[i].setSummaryValue(keys[k], mps.getProperty(keys[k]));
            }
         }
      }
   }

   public void enableZSliceSetting(boolean b) {
      useSliceSetting_ = b;
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

   protected void wipeImage5D(Image5D image5D) {

      Object emptyPixels = image5D.createEmptyPixels();
      int nc = image5D.getNChannels();
      int ns = image5D.getNSlices();
      int nf = image5D.getNFrames();

      for (int c = 1; c <= nc; ++c) {
         for (int f = 1; f <= nf; ++f) {
            for (int s = 1; s <= ns; ++s) {
               image5D.setPixels(emptyPixels, c, s, f);
            }
         }
      }
   }

   private boolean groupIsEligibleChannel(String group) {
      StrVector cfgs = core_.getAvailableConfigs(group);
      if (cfgs.size() == 1) {
         Configuration presetData;
         try {
            presetData = core_.getConfigData(group, cfgs.get(0));
            if (presetData.size() == 1) {
               PropertySetting setting = presetData.getSetting(0);
               String devLabel = setting.getDeviceLabel();
               String propName = setting.getPropertyName();
               if (core_.hasPropertyLimits(devLabel, propName)) {
                  return false;
               }
            }
         } catch (Exception ex) {
            ReportingUtils.logError(ex);
            return false;
         }

      }
      return true;
   }

   public String[] getAvailableGroups() {
      StrVector groups;
      try {
         groups = core_.getAllowedPropertyValues("Core", "ChannelGroup");
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
         return new String[0];
      }
      ArrayList<String> strGroups = new ArrayList<String>();
      for (int i = 0; i < groups.size(); i++) {
         String group = groups.get(i);
         if (groupIsEligibleChannel(group)) {
            strGroups.add(group);
         }
      }

      return strGroups.toArray(new String[0]);
   }

   protected long getImageWidth() {
      return core_.getImageWidth();
   }

   protected long getImageHeight() {
      return core_.getImageHeight();
   }

   /**
    * Creates and configures the Image5d and associated window based
    * on the acquisition protocol.
    *  
    * @throws Exception
    */
   protected void setupImage5d(int posIndex) throws MMException {
      imgWidth_ = getImageWidth();
      imgHeight_ = getImageHeight();
      imgDepth_ = core_.getBytesPerPixel();
      pixelSize_um_ = core_.getPixelSizeUm();
      pixelAspect_ = 1.0; // TODO: obtain from core

      if (singleWindow_ && posIndex > 0) {
         return;
      }

      int type;
      if (imgDepth_ == 1) {
         type = ImagePlus.GRAY8;
      } else if (imgDepth_ == 2) {
         type = ImagePlus.GRAY16;
      } else if (4 == imgDepth_) {
         type = ImagePlus.COLOR_RGB;
      } else {
         throw new MMException("Unsupported pixel depth");
      }

      // create a new Image5D object
      int numSlices = 1;
      if (useSliceSetting_) {
         numSlices = sliceDeltaZ_.length;
      }

      if (useMultiplePositions_ && posMode_ == PositionMode.TIME_LAPSE) { // Positions first
         setupImage5DArray();
         imgData_ = new ImageData[posList_.getNumberOfPositions()];
      } else if (useMultiplePositions_ && posMode_ == PositionMode.MULTI_FIELD) { // Time first
         // multi-field mode special handling
         if (posIndex == 0) {
            if (img5d_ == null || img5d_.length != 1) {
               img5d_ = new Image5D[1];
               i5dWin_ = new Image5DWindow[1];
               imgData_ = new ImageData[1];
            }
         } else {
            // reset
         }
      } else {
         img5d_ = new Image5D[1];
         i5dWin_ = new Image5DWindow[1];
         imgData_ = new ImageData[1];
      }

      int n = 1;
      if (!singleWindow_) {
         n = img5d_.length;
      }
      for (int i = 0; i < n; i++) {
         int actualFrames = singleFrame_ ? 1 : numFrames_;
         boolean newWindow = false;
         if (!(useMultiplePositions_ && posMode_ == PositionMode.MULTI_FIELD /* Time first */) || posIndex == 0 || img5d_[i].getWidth() != getImageWidth() || img5d_[i].getHeight() != getImageHeight()) {
            if (posMode_ == PositionMode.MULTI_FIELD) {
               if (img5d_[i] == null || img5d_[i].getType() != type || img5d_[i].getWidth() != imgWidth_ || img5d_[i].getHeight() != imgHeight_
                       || img5d_[i].getNSlices() != numSlices || img5d_[i].getNFrames() != actualFrames || img5d_[i].getNChannels() != channels_.size()
                       || (i5dWin_[i] != null && i5dWin_[i].isClosed())) {
                  img5d_[i] = createImage5D(i, type, numSlices, actualFrames);
                  newWindow = true;
               }
            } else {
               img5d_[i] = createImage5D(i, type, numSlices, actualFrames);
               newWindow = true;
            }
         }

         if (useMultiplePositions_ && posMode_ == PositionMode.MULTI_FIELD) {
            wipeImage5D(img5d_[i]);
         }

         imgData_[i] = new ImageData();
         imgData_[i].imgWidth_ = getImageWidth();
         imgData_[i].imgHeight_ = getImageHeight();
         imgData_[i].imgDepth_ = core_.getBytesPerPixel();
         //imgData_[i].pixelSize_um_ = core_.getPixelSizeUm();
         //imgData_[i].pixelAspect_ = 1.0;

         // Set ImageJ calibration:
         Calibration cal = new Calibration();
         if (pixelSize_um_ != 0) {
            cal.setUnit("um");
            cal.pixelWidth = pixelSize_um_;
            cal.pixelHeight = pixelSize_um_ * pixelAspect_;
         }
         if (numSlices > 1) {
            cal.pixelDepth = sliceDeltaZ_.length;
            cal.setUnit("um");
         }
         if (numFrames_ > 1) {
            cal.frameInterval = frameIntervalMs_;
            cal.setTimeUnit("ms");
         }
         img5d_[i].setCalibration(cal);

         for (int j = 0; j < channels_.size(); j++) {
            ChannelCalibration chcal = new ChannelCalibration();
            chcal.setLabel(channels_.get(j).config_);
            img5d_[i].setChannelCalibration(j + 1, chcal);
            if( ImagePlus.COLOR_RGB == type)
               //todo image channel could have a different type!!!
               img5d_[i].setChannelColorModel(j + 1, new DirectColorModel(32, 0xFF0000, 0xFF00, 0xFF));
            else
            // set color
               img5d_[i].setChannelColorModel(j + 1, ChannelDisplayProperties.createModelFromColor(channels_.get(j).color_));
         }

         // pop-up 5d image window
         if (singleWindow_ && i5dWin_[i] == null) {
            i5dWin_[i] = new Image5DWindow(img5d_[i], false);
         } else if (newWindow || i5dWin_[i] == null) {
            i5dWin_[i] = new Image5DWindow(img5d_[i]);
         }


         //WindowManager.addWindow(i5dWin_);
         ChannelSpec[] cs = new ChannelSpec[channels_.size()];
         for (int j = 0; j < channels_.size(); j++) {
            cs[j] = channels_.get(j);
         }
         i5dWin_[i].setMMChannelData(cs);
         if (!i5dWin_[i].isVisible()) {
            i5dWin_[i].setLocation(xWindowPos + i * 30, yWindowPos + i * 30);
         }

         if (i == 0 && !singleWindow_) {
            // add listener to the IJ window to detect when it closes
            // (use only the first window in the multi-pos case)
            WindowListener wndCloser = new WindowAdapter() {

               public void windowClosing(WindowEvent e) {
                  // TODO: this does not work anymore since i5dWin_ will be null at this point
                  if (i5dWin_ != null && i5dWin_.length > 0) {
                     Rectangle r = i5dWin_[0].getBounds();
                     // record the position of the IJ window
                     xWindowPos = r.x;
                     yWindowPos = r.y;
                  }
               }
            };
            if (i5dWin_.length > 0)
               i5dWin_[0].addWindowListener(wndCloser);
         }

         // Set the desired display mode.  This needs to be called after opening the Window
         // There is a threading issue, setting this too early will lead to exceptions in Java 
         // Note that OVERLAY mode is much slower than others, so show a single channel in a fast mode
         if (channels_.size() == 1) {

            if(  ImagePlus.COLOR_RGB == type){
               img5d_[i].setDisplayMode(ChannelControl.RGB);
            }else{
            img5d_[i].setDisplayMode(ChannelControl.ONE_CHANNEL_COLOR);
            }
         } else {
            img5d_[i].setDisplayMode(ChannelControl.OVERLAY);
         }

         // hook up with the acquisition engine
         i5dWin_[i].setAcquitionEngine(this);

         i5dWin_[i].setActive(true);
      }
   }

   protected Image5D createImage5D(int posIdex, int type, int numSlices, int actualFrames) {
      return new Image5D(acqName_, type, (int) imgWidth_, (int) imgHeight_, channels_.size(), numSlices, actualFrames, false);
   }

   protected void setupImage5DArray() {
      img5d_ = new Image5D[posList_.getNumberOfPositions()];
      i5dWin_ = new Image5DWindow[posList_.getNumberOfPositions()];
   }

   private void setImageNames(int posIndex, String comment) {
      if (singleWindow_) {
         return;
      }

      GregorianCalendar cld = new GregorianCalendar();
      if (i5dWin_ != null && i5dWin_.length > 0 && acqData_ != null) {
         for (int i = 0; i < i5dWin_.length; i++) {
            if (useMultiplePositions_ && well_ != null) {
               if (posMode_ == PositionMode.TIME_LAPSE) // time-lapse
               {
                  i5dWin_[i].setTitle(well_.getLabel() + fileSeparator_ + posList_.getPosition(i).getLabel() + comment + cld.getTime());
               } else // multi-field
               {
                  i5dWin_[i].setTitle(well_.getLabel() + fileSeparator_ + posList_.getPosition(posIndex).getLabel() + comment + cld.getTime());
               }
            } else // single position
            {
               i5dWin_[i].setTitle(acqData_[i].getName() + comment + cld.getTime());
            }
         }
      }
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

   public void keepShutterOpenForStack(boolean open) {
      keepShutterOpenForStack_ = open;
   }

   public boolean isShutterOpenForStack() {
      return keepShutterOpenForStack_;
   }

   public void keepShutterOpenForChannels(boolean open) {
      keepShutterOpenForChannels_ = open;
   }

   public boolean isShutterOpenForChannels() {
      return keepShutterOpenForChannels_;
   }

   public void enableAutoFocus(boolean enable) {
      autofocusEnabled_ = enable;
   }

   public boolean isAutoFocusEnabled() {
      return autofocusEnabled_;
   }

   public int getAfSkipInterval() {
      return afSkipInterval_;
   }

   public void setAfSkipInterval(int interval) {
      if (interval < 0) {
         interval = 0;
      }
      if (interval > numFrames_ - 1) {
         interval = numFrames_ - 1;
      }
      afSkipInterval_ = interval;
   }

   /**
    * Acquires a single frame during acquisition
    *
    * Displays in an Image5D (if requested) or in Live Window
    * Attach metadata
    * @param cs - channel specification
    * @param z - Current Z position 
    * @param sliceIdx - Number of (z) slice
    * @param channelIdx - Number of channel
    * @param posIdx - Number of position (when using multi-field)
    * @param numSlices - Total number of (z) slices
    * @param posIndexNormalized - ???
    */
   protected void executeProtocolBody(ChannelSpec cs, double z, int sliceIdx,
           int channelIdx, int posIdx, int numSlices, int posIndexNormalized) throws MMException, IOException, JSONException, MMAcqDataException {
      shutterIsOpen_ = false;
      try {
         shutterIsOpen_ = core_.getShutterOpen();
      } catch (Exception ex) {
         ReportingUtils.showError(ex);
      }
      int actualFrameCount = singleFrame_ ? 0 : frameCount_;

      // check if we need to skip this frame
      if (frameCount_ > 0 && frameCount_ % (Math.abs(cs.skipFactorFrame_) + 1) != 0) {
         fillInSkippedFrame(cs, sliceIdx, channelIdx, posIndexNormalized, actualFrameCount);
         return;
      }

      // check if we need to skip this slice
      if (!cs.doZStack_ && sliceIdx > 0) {
         fillInSkippedSlice(sliceIdx, channelIdx, posIndexNormalized, actualFrameCount);
         return;
      }

      // apply z-offsets
      // GregorianCalendar cldAcq = new GregorianCalendar();
      double exposureMs = cs.exposure_;
      Object img = null;
      double zAbsolutePos = z;

      try {
         if (isFocusStageAvailable() && cs.zOffset_ != 0.0 && cs.config_.length() > 0) {
            core_.waitForDevice(zStage_);
            zAbsolutePos = z + cs.zOffset_;
            core_.setPosition(zStage_, zAbsolutePos);
            core_.waitForDevice(zStage_);
         }

         if (cs.config_.length() > 0) {
            core_.setConfig(core_.getChannelGroup(), cs.config_);
            core_.waitForConfig(core_.getChannelGroup(), cs.config_);
            core_.setExposure(cs.exposure_);
         }

         if (originalAutoShutterSetting_ == true && core_.getAutoShutter() == false && shutterIsOpen_ == false) {
             if (sliceMode_ == SliceMode.SLICES_FIRST && keepShutterOpenForStack_ && sliceIdx == 0) {
                setShutterOpen(true);
             }
             if (sliceMode_ == SliceMode.CHANNELS_FIRST && keepShutterOpenForChannels_ && channelIdx == 0) {
                setShutterOpen(true);
             }
         }

         try{
             img = snapAndRetrieve();
         }
         catch( Exception e1)
         {
            try    {
             ReportingUtils.logError(e1);
             ReportingUtils.displayNonBlockingMessage("acquisition snapAndRetrieve failed: " + e1.getMessage());
            }  catch( Throwable t ){
               System.out.println("error attempting to display error: " + e1.getLocalizedMessage() );
            }

         }
         try {
         if (originalAutoShutterSetting_ == true && core_.getAutoShutter() == false && shutterIsOpen_ == true) {
             if (sliceMode_ == SliceMode.SLICES_FIRST && !keepShutterOpenForChannels_ && sliceIdx == (numSlices-1)) {
                 setShutterOpen(false);
             }
             if (sliceMode_ == SliceMode.CHANNELS_FIRST && !keepShutterOpenForStack_ && channelIdx == channels_.size()-1) {
                 setShutterOpen(false);
             }
         }
         } catch (Exception e) {
            ReportingUtils.logError(e);
         }

         if (img != null && singleWindow_) {
            String statusLine = String.format("Frame %d Channel %s Slice %d Pos %d ",
                    frameCount_, cs.config_, sliceIdx, posIndexNormalized);
            parentGUI_.displayImageWithStatusLine(img, statusLine);
         }

      } catch (Exception e) {
         ReportingUtils.logError(e);
         throw new MMException(e.getMessage());
      }

      long width = getImageWidth();
      long height = getImageHeight();
      long depth = core_.getBytesPerPixel();

      // processing for the first image in the entire sequence
      if (sliceIdx == 0 && channelIdx == 0 && frameCount_ == 0) {
         if (!useMultiplePositions_ || posMode_ == PositionMode.TIME_LAPSE) {
            if (posIdx == 0) {
               fullSetup(posIdx);
            }
         } else {
            fullSetup(posIdx);
         }
      }

      // processing for the first image in a frame -- do we have enough memory?
      if (sliceIdx == 0 && channelIdx == 0) {
         haveEnoughMemoryForFrame(numSlices, width, height, depth);
      }

      // we won't try to adjust type mismatch
      if (imgDepth_ != depth) {
         throw new MMException("Byte depth does not match between channels or slices");
      }

      // if the image is the wrong size, rescale it.
      if (imgWidth_ != width || imgHeight_ != height) {
         core_.logMessage("Scaling from: " + width + "," + height);
         ImageProcessor imp;
         if (imgDepth_ == 1) {
            imp = new ByteProcessor((int) width, (int) height);
         } else {
            imp = new ShortProcessor((int) width, (int) height);
         }
         imp.setPixels(img);
         ImageProcessor ip2 = imp.resize((int) imgWidth_, (int) imgHeight_);
         img = ip2.getPixels();
      }

      // set Image5D with the new image
      insertPixelsIntoImage5D(sliceIdx, channelIdx, actualFrameCount, posIndexNormalized, img);

      // generate meta-data
      generateMetadata(zAbsolutePos, sliceIdx, channelIdx, posIdx, posIndexNormalized,
              exposureMs, img);
   }

   /**
    * @param zCur
    * @param sliceIdx
    * @param channelIdx
    * @param posIdx
    * @param posIndexNormalized
    * @param exposureMs
    * @param img
    * @throws MMAcqDataException
    */
   public void generateMetadata(double zCur, int sliceIdx, int channelIdx,
           int posIdx, int posIndexNormalized, double exposureMs, Object img)
           throws MMAcqDataException {
      if (acqData_ == null || acqData_.length == 0 || acqData_[posIndexNormalized] == null) {
         return;
      }

      JSONObject state = Annotator.generateJSONMetadata(core_.getSystemStateCache());
      acqData_[posIndexNormalized].insertImageMetadata(frameCount_, channelIdx, sliceIdx);
      acqData_[posIndexNormalized].setImageValue(frameCount_, channelIdx, sliceIdx, ImagePropertyKeys.EXPOSURE_MS, exposureMs);
      acqData_[posIndexNormalized].setImageValue(frameCount_, channelIdx, sliceIdx, ImagePropertyKeys.Z_UM, zCur);
      if (useMultiplePositions_) {
         acqData_[posIndexNormalized].setImageValue(frameCount_, channelIdx, sliceIdx, ImagePropertyKeys.X_UM, posList_.getPosition(posIdx).getX());
         acqData_[posIndexNormalized].setImageValue(frameCount_, channelIdx, sliceIdx, ImagePropertyKeys.Y_UM, posList_.getPosition(posIdx).getY());
      }
      acqData_[posIndexNormalized].setSystemState(frameCount_, channelIdx, sliceIdx, state);

      if (saveFiles_) {
         acqData_[posIndexNormalized].attachImage(img, frameCount_, channelIdx, sliceIdx);
         lastImageFilePath_ = acqData_[posIndexNormalized].getLastImageFilePath();
      }
   }

   /**
    * When a frame is skipped, fill in missing data in display only from previous frame
    *
    * @param cs
    * @param sliceIdx
    * @param channelIdx
    * @param posIndexNormalized
    * @param actualFrameCount
    */
   private void fillInSkippedFrame(ChannelSpec cs, int sliceIdx, int channelIdx,
           int posIndexNormalized, int actualFrameCount) {

      // attempt to fill in the gap by using the most recent channel data

      if (!singleFrame_) {
         int offset = frameCount_ % (Math.abs(cs.skipFactorFrame_) + 1);

         int index = 0;
         if (img5d_[posIndexNormalized] != null) {
            index = posIndexNormalized;
         }

         Object previousImg = img5d_[index].getPixels(channelIdx + 1, sliceIdx + 1, actualFrameCount + 1 - offset);
         if (previousImg != null) {
            img5d_[0].setPixels(previousImg, channelIdx + 1, sliceIdx + 1, actualFrameCount + 1);
         }
      }
   }

   /**
    * When a channel does not do a Z-stack, fill in the missing slice from the previous slice (for display only)
    *
    * @param sliceIdx
    * @param channelIdx
    * @param posIndexNormalized
    * @param actualFrameCount
    */
   private void fillInSkippedSlice(int sliceIdx, int channelIdx,
           int posIndexNormalized, int actualFrameCount) {

      // attempt to fill in the gap by using the most recent channel data

      if (!singleFrame_) {
         int index = 0;
         if (img5d_[posIndexNormalized] != null) {
            index = posIndexNormalized;
         }

         if (sliceIdx > 0) {
            Object previousImg = img5d_[index].getPixels(channelIdx + 1, sliceIdx, actualFrameCount + 1);
            if (previousImg != null) {
               img5d_[0].setPixels(previousImg, channelIdx + 1, sliceIdx + 1, actualFrameCount + 1);
            }
         }
      }
   }

   /**

   /**
    * @param posIdx
    * @throws MMException
    * @throws IOException
    * @throws MMAcqDataException
    */
   protected void fullSetup(int posIdx) throws MMException, IOException, MMAcqDataException {
      setupImage5d(posIdx);
      acquisitionDirectorySetup(posIdx);
      setImageNames(posIdx, " (started) ");
   }

   /**
    * @param numSlices
    * @param width
    * @param height
    * @param depth
    * @throws OutOfMemoryError
    */
   private void haveEnoughMemoryForFrame(int numSlices, long width, long height,
           long depth) throws OutOfMemoryError {
      // check if we have enough memory to acquire the entire frame
      long freeBytes = MemoryUtils.freeMemory();
      long requiredBytes = ((long) numSlices * channels_.size() + 10) * (width * height * depth);
      core_.logMessage("Remaining memory " + freeBytes + " bytes. Required: " + requiredBytes);
      int tries = 0;
      while (freeBytes < requiredBytes && tries < 5) {
         System.gc();
         freeBytes = MemoryUtils.freeMemory();
         tries++;
      }
      if (freeBytes < requiredBytes) {
         throw new OutOfMemoryError("Remaining memory " + FMT2.format(freeBytes / 1048576.0)
                 + " MB. Required for the next step: " + FMT2.format(requiredBytes / 1048576.0) + " MB");
      }
   }

   /**
    * @param sliceIdx
    * @param channelIdx
    * @param actualFrameCount
    * @param posIndexNormalized
    * @param img
    */
   protected void insertPixelsIntoImage5D(int sliceIdx, int channelIdx, int actualFrameCount,
           int posIndexNormalized, Object img) {
      if (i5dWin_ == null || i5dWin_.length == 0) {
         return;
      }

      // set Image5D
      Image5DWindow i5dWin = i5dWin_[posIndexNormalized];

      if (!singleWindow_ && i5dWin != null) {
         img5d_[posIndexNormalized].setPixels(img, channelIdx + 1, sliceIdx + 1, actualFrameCount + 1);
         if (!i5dWin.isPlaybackRunning()) {
            if (frameCount_ == 0) {
               img5d_[posIndexNormalized].setCurrentPosition(0, 0, channelIdx, sliceIdx, actualFrameCount);
            } else {
               int chan = img5d_[posIndexNormalized].getCurrentChannel() - 1;
               img5d_[posIndexNormalized].setCurrentPosition(0, 0, chan, sliceIdx, actualFrameCount);
            }
         }
         // auto-scale channels based on the first slice of the first frame
         if (frameCount_ == 0) {
            if (channelIdx < 0 || channelIdx >= channels_.size()) {
               ReportingUtils.logMessage("Encountered non-existing channel " + channelIdx + " in function insertPixelsIntoImage5D");
               return;
            }

            ChannelSpec sp = channels_.get(channelIdx);
            ImageStatistics stats = img5d_[posIndexNormalized].getStatistics(); // get uncalibrated stats
            if( 4==img5d_[posIndexNormalized].getBytesPerPixel()){
               // For RGB, need to set min to 0 so image will display correctly in Playback Panel
               sp.min_ = 0.;
            }else{
            if (stats.min < sp.min_) {
               sp.min_ = stats.min;
            }
            }
            if (stats.max > sp.max_) {
               sp.max_ = stats.max;
            }

            img5d_[posIndexNormalized].setChannelMinMax(channelIdx + 1, sp.min_, sp.max_);
         }

         RefreshI5d refresh = new RefreshI5d(i5dWin);
         SwingUtilities.invokeLater(refresh);
      }
   }

   protected Object snapAndRetrieve() throws Exception {
      Object img;
      // snap and retrieve pixels
      core_.snapImage();
      img = core_.getImage();
      return img;
   }

   public void goToPosition(int posIndex) throws Exception {
      MultiStagePosition pos = posList_.getPosition(posIndex);
      try {
         MultiStagePosition.goToPosition(pos, core_);
         core_.waitForSystem();
      } catch (Exception e1) {
         throw e1;
      }
   }

   private void refreshZPosition() {
      // refresh the current z position
      try {
         if (isFocusStageAvailable()) {
            startZPosUm_ = core_.getPosition(zStage_);
         } else {
            startZPosUm_ = 0;
         }
      } catch (Exception e) {
         ReportingUtils.showError(e, "Error during Acquisition");
      }
   }

   private void performAutofocus(MultiStagePosition pos, int posIdx) throws Exception {
      // perform auto focusing if the module is available
      if (afMgr_.getDevice() != null && autofocusEnabled_ && (frameCount_ % (afSkipInterval_ + 1) == 0)) {
         // check for any autofocusing instructions
         if (pos != null && pos.hasProperty(PositionList.AF_KEY)) {
            // check if recognize any tags and see if we can do anything about it
            if (pos.getProperty(PositionList.AF_KEY).equals(PositionList.AF_VALUE_INCREMENTAL)) {
               afMgr_.getDevice().incrementalFocus();
            } else if (pos.getProperty(PositionList.AF_KEY).equals(PositionList.AF_VALUE_FULL)) {
               attemptFullFocus();
            } else if (pos.getProperty(PositionList.AF_KEY).equals(PositionList.AF_VALUE_NONE)) {
               ; // do nothing
            } else {
               // unrecognized tag
               throw new MMException("Unrecognized Auto-focus property in position list");
            }
         } else {
            // no instructions, so do full focus on each position
            attemptFullFocus();
         }
         // update the Z-position based on the autofocus
         if (pos != null) {
            double zFocus = core_.getPosition(zStage_);
            StagePosition sp = pos.get(zStage_);
            if (sp != null) {
               sp.x = zFocus; // assuming this is a single-axis stage set the first axis to the z value
            }
         }
         previousPosIdx_ = posIdx;
      }
   }

   public synchronized void setPause(boolean state) {
      pause_ = state;
      // Reflect pausing state in image title
      String stateInTitle = " (paused) ";
      if (!pause_)
         stateInTitle = " (started) ";
      Date enddate = GregorianCalendar.getInstance().getTime();
      for (int i = 0; i < i5dWin_.length; i++) {
         if (useMultiplePositions_ && (well_ != null)) {
            i5dWin_[i].setTitle(well_.getLabel() + fileSeparator_ + posList_.getPosition(i).getLabel() + stateInTitle + enddate);
         } else if (acqData_[i] != null) {
            i5dWin_[i].setTitle(acqData_[i].getName() + stateInTitle + enddate);
         }
      }
   }

   public synchronized boolean isPaused() {
      return pause_;
   }

   public void setFinished() {
      acqFinished_ = true;
   }

   public String installAutofocusPlugin(String className) {
      return "Call to installAutofocusPlugin in MMAcquisitionEngine received. This method has been deprecated. Use MMStudioMainFrame.installAutofocusPlugin() instead.";
   }

   private void attemptFullFocus() {
      try {
         afMgr_.getDevice().fullFocus();
      } catch (final MMException e) {
         if (!autofocusHasFailed_) {
            autofocusHasFailed_ = true;
            new Thread() {
               public void run() {
                  ReportingUtils.showError(e, "Autofocus has failed during this acquisition.");
               }
            }.start();

         }
      }
   }

   public boolean isFramesSettingEnabled() {
      return useFramesSetting_;
   }

   public void enableFramesSetting(boolean enable) {
      useFramesSetting_ = enable;
   }

   public boolean isChannelsSettingEnabled() {
      return useChannelsSetting_;
   }

   public void enableChannelsSetting(boolean enable) {
      useChannelsSetting_ = enable;
   }

   private void setShutterOpen(boolean b) {
      try {
         core_.setShutterOpen(b);
         core_.waitForDevice(core_.getShutterDevice());
      } catch (Exception ex) {
         ReportingUtils.showError(ex);
      }
      shutterIsOpen_ = b;

   }

    public String getLastImageFilePath() {
        return lastImageFilePath_;
    }

    public void abortRequest(){
      abortRequest_= true;
    }
}
