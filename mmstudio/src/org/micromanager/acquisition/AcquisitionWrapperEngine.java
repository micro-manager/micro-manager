/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.acquisition;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.micromanager.acquisition.engine.BurstMaker;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.prefs.Preferences;
import javax.swing.JOptionPane;
import mmcorej.CMMCore;
import mmcorej.Configuration;
import mmcorej.PropertySetting;
import mmcorej.StrVector;
import mmcorej.TaggedImage;
import org.micromanager.MMStudioMainFrame;
import org.micromanager.acquisition.engine.Engine;
import org.micromanager.acquisition.engine.ProcessorStack;
import org.micromanager.acquisition.engine.SequenceGenerator;
import org.micromanager.acquisition.engine.SequenceSettings;
import org.micromanager.api.AcquisitionEngine;
import org.micromanager.api.DeviceControlGUI;
import org.micromanager.api.EngineTask;
import org.micromanager.api.Pipeline;
import org.micromanager.navigation.PositionList;
import org.micromanager.utils.AutofocusManager;
import org.micromanager.utils.ChannelSpec;
import org.micromanager.utils.ContrastSettings;
import org.micromanager.utils.MMException;
import org.micromanager.utils.NumberUtils;
import org.micromanager.utils.PositionMode;
import org.micromanager.utils.ReportingUtils;
import org.micromanager.utils.SliceMode;

/**
 *
 * @author arthur
 */
public class AcquisitionWrapperEngine implements AcquisitionEngine {
   private CMMCore core_;
   private MMStudioMainFrame gui_;
   private PositionList posList_ = new PositionList();
   private String zstage_;
   private double sliceZStepUm_;
   private double sliceZBottomUm_;
   private double sliceZTopUm_;
   private boolean useSlices_;
   private boolean useFrames_;
   private boolean useChannels_;
   private boolean keepShutterOpenForStack_;
   private boolean keepShutterOpenForChannels_;
   private boolean useMultiPosition_;
   private ArrayList<ChannelSpec> channels_ = new ArrayList<ChannelSpec>();
   private String rootName_;
   private String dirName_;
   private int numFrames_;
   private double interval_;
   private LiveAcqDisplay display_;
   private double minZStepUm_;
   private String comment_;
   private boolean saveFiles_;
   private int sliceMode_;
   private int positionMode_;
   private boolean useAutoFocus_;
   private int afSkipInterval_;
   private Engine eng_ = null;
   private List<Class> taggedImageProcessors_;
   private List<Class> imageRequestProcessors_;
   private boolean absoluteZ_;
   private Pipeline pipeline_;
   private Thread createPipelineThread_;

   public AcquisitionWrapperEngine() {
      Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());
      createPipelineThread_ = new Thread() {
         public void run() {
            try {
               pipeline_ = (Pipeline) Class.forName("org.micromanager.AcqEngine").newInstance();
            } catch (Exception ex) {
               ReportingUtils.logError(ex);
            }
         }
      };
      createPipelineThread_.start();

      imageRequestProcessors_ = new ArrayList<Class>();
      imageRequestProcessors_.add(BurstMaker.class);
      taggedImageProcessors_ = new ArrayList<Class>();
   }

   public void acquire() throws MMException {
      try {
         createPipelineThread_.join();
      } catch (InterruptedException ex) {
         ReportingUtils.logError(ex);
      }
      runPipeline2(gatherSequenceSettings());
   }

   public void runPipeline2(SequenceSettings acquisitionSettings) {
      try {
         pipeline_.run(acquisitionSettings, this);
      } catch (Throwable ex) {
         ReportingUtils.showError(ex);
      }
   }

   public void runPipeline(SequenceSettings acquisitionSettings) {
      try {
         // SET UP THE PIPELINE...
         // ...Sequence generator...
         SequenceGenerator generator = new SequenceGenerator(acquisitionSettings, core_.getExposure());
         BlockingQueue<EngineTask> generatorOutput = generator.begin();

         // ...TaskProcessorStack...
         ProcessorStack<EngineTask> taskProcessorStack =
                 new ProcessorStack<EngineTask>(generatorOutput,
                 imageRequestProcessors_);
         BlockingQueue<EngineTask> requestProcessorStackOutput = taskProcessorStack.begin();

         // ...Engine...
         eng_ = new Engine(core_, gui_.getAutofocusManager(),
                 requestProcessorStackOutput, acquisitionSettings);
         BlockingQueue<TaggedImage> engineOutput = eng_.begin();

         // ...ImageProcessorStack...
         ProcessorStack<TaggedImage> imageProcessorStack =
                 new ProcessorStack<TaggedImage>(engineOutput,
                 taggedImageProcessors_);
         BlockingQueue<TaggedImage> imageProcessorStackOutput = imageProcessorStack.begin();

         // ...Display and Save...
         display_ = new LiveAcqDisplay(core_, imageProcessorStackOutput,
                 acquisitionSettings, acquisitionSettings.channels, saveFiles_, this);
         display_.start();

      } catch (Exception ex) {
         ReportingUtils.logError(ex);
      }
   }

   private void updateChannelCameras() {
      for(ChannelSpec channel:channels_) {
         channel.camera_ = getSource(channel);
      }
   }

   private String getSource(ChannelSpec channel) {
      PropertySetting setting;
      try {
         Configuration state = core_.getConfigGroupState(channel.config_);
         if (state.isPropertyIncluded("Core", "Camera")) {
            return state.getSetting("Core", "Camera").getPropertyValue();
         } else {
            return "";
         }
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
         return "";
      }
   }


   public void addImageProcessor(Class taggedImageProcessorClass) {
      taggedImageProcessors_.add(taggedImageProcessorClass);
   }

   public void removeImageProcessor(Class taggedImageProcessor) {
      taggedImageProcessors_.remove(taggedImageProcessor);
   }

   private SequenceSettings gatherSequenceSettings() {
      SequenceSettings acquisitionSettings = new SequenceSettings();

      updateChannelCameras();

      // Frames
      if (useFrames_) {
         acquisitionSettings.numFrames = numFrames_;
         acquisitionSettings.intervalMs = interval_;
      } else {
         acquisitionSettings.numFrames = 0;
      }

      // Slices
      if (useSlices_) {
         if (sliceZTopUm_ > sliceZBottomUm_) {
            for (double z = sliceZBottomUm_; z <= sliceZTopUm_; z += sliceZStepUm_) {
               acquisitionSettings.slices.add(z);
            }
         } else {
            for (double z = sliceZBottomUm_; z >= sliceZTopUm_; z -= sliceZStepUm_) {
               acquisitionSettings.slices.add(z);
            }
         }
      }

      acquisitionSettings.relativeZSlice = !this.absoluteZ_;
      try {
         acquisitionSettings.zReference = core_.getPosition(core_.getFocusDevice());
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
      }
      // Channels

      if (this.useChannels_) {
         for (ChannelSpec channel:channels_)
            if (channel.useChannel_)
               acquisitionSettings.channels.add(channel);
      }

      // Positions
      if (this.useMultiPosition_)
         acquisitionSettings.positions.addAll(Arrays.asList(posList_.getPositions()));

      // Other

      acquisitionSettings.timeFirst = !(positionMode_ == PositionMode.TIME_LAPSE);
      acquisitionSettings.slicesFirst = !(sliceMode_ == SliceMode.CHANNELS_FIRST);

      acquisitionSettings.useAutofocus = useAutoFocus_;
      acquisitionSettings.skipAutofocusCount = afSkipInterval_;

      acquisitionSettings.keepShutterOpenChannels = keepShutterOpenForChannels_;
      acquisitionSettings.keepShutterOpenSlices = keepShutterOpenForStack_;

      acquisitionSettings.save = saveFiles_;
      acquisitionSettings.root = rootName_;
      acquisitionSettings.prefix = dirName_;
      acquisitionSettings.comment = comment_;
      return acquisitionSettings;
   }


//////////////////// Actions ///////////////////////////////////////////

   public void stop(boolean interrupted) {
      try {
         pipeline_.stop();
      } catch (Exception ex) {
         ReportingUtils.showError("Acquisition engine stop request failed");
      }
   }

   public boolean abortRequest() {
      if (isAcquisitionRunning()) {
         int result = JOptionPane.showConfirmDialog(null,
                 "Abort current acquisition task ?",
                 "Micro-Manager", JOptionPane.YES_NO_OPTION,
                 JOptionPane.INFORMATION_MESSAGE);

         if (result == JOptionPane.YES_OPTION) {
            stop(true);
            return true;
         }
      }
      return false;
   }
   
   public boolean abortRequested() {
      return pipeline_.stopHasBeenRequested();
   }

   public void shutdown() {
      stop(true);
   }

   public void setPause(boolean state) {
      if (state) {
         pipeline_.pause();
      } else {
         pipeline_.resume();
      }
   }


//// State Queries /////////////////////////////////////////////////////

   public boolean isAcquisitionRunning() {
      if (pipeline_ != null)
         return pipeline_.isRunning();
      else
         return false;
   }

   public boolean isMultiFieldRunning() {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public long getNextWakeTime() {
      return pipeline_.nextWakeTime();
   }

//////////////////// Setters and Getters ///////////////////////////////

   public void setCore(CMMCore core_, AutofocusManager afMgr) {
      this.core_ = core_;
   }

   public void setPositionList(PositionList posList) {
      posList_ = posList;
   }

   public void setParentGUI(DeviceControlGUI parent) {
      gui_ = (MMStudioMainFrame) parent;
   }

   public void setZStageDevice(String stageLabel_) {
      zstage_ = stageLabel_;
   }

   public void setUpdateLiveWindow(boolean b) {
      // do nothing
   }

   public void setFinished() {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public int getCurrentFrameCount() {
      return 0;
   }

   public double getFrameIntervalMs() {
      return interval_;
   }

   public double getSliceZStepUm() {
      return sliceZStepUm_;
   }

   public double getSliceZBottomUm() {
      return sliceZBottomUm_;
   }

   public void setChannel(int row, ChannelSpec channel) {
      channels_.set(row, channel);
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
   public int getNumFrames() {
      return numFrames_;
   }

   public String getChannelGroup() {
      return core_.getChannelGroup();
   }

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

   /**
    * Resets the engine.
    */
   public void clear() {
      if (channels_ != null)
         channels_.clear();
      numFrames_ = 0;
   }

   public void setFrames(int numFrames, double interval) {
      numFrames_ = numFrames;
      interval_ = interval;
   }

   public double getMinZStepUm() {
      return minZStepUm_;
   }

   public void setSlices(double bottom, double top, double step, boolean b) {
      sliceZBottomUm_ = bottom;
      sliceZTopUm_ = top;
      sliceZStepUm_ = step;
      absoluteZ_ = b;
   }

   public boolean isFramesSettingEnabled() {
      return useFrames_;
   }

   public void enableFramesSetting(boolean enable) {
      useFrames_ = enable;
   }

   public boolean isChannelsSettingEnabled() {
      return useChannels_;
   }

   public void enableChannelsSetting(boolean enable) {
      useChannels_ = enable;
   }

   public boolean isZSliceSettingEnabled() {
      return useSlices_;
   }

   public double getZTopUm() {
      return sliceZTopUm_;
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

   public void enableZSliceSetting(boolean boolean1) {
      useSlices_ = boolean1;
   }

   public void enableMultiPosition(boolean selected) {
      useMultiPosition_ = selected;
   }

   public boolean isMultiPositionEnabled() {
      return useMultiPosition_;
   }

   public ArrayList<ChannelSpec> getChannels() {
      return channels_;
   }

   public void setChannels(ArrayList<ChannelSpec> channels) {
      channels_ = channels;
   }

   public String getRootName() {
      return rootName_;
   }

   public void setRootName(String absolutePath) {
      rootName_ = absolutePath;
   }

   public void setCameraConfig(String cfg) {
      // do nothing
   }

   public void setDirName(String text) {
      dirName_ = text;
   }

   public void setComment(String text) {
      comment_ = text;
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
   public boolean addChannel(String config, double exp, Boolean doZStack, double zOffset, ContrastSettings c8, ContrastSettings c16, int skip, Color c, boolean use) {
      if (isConfigAvailable(config)) {
         ChannelSpec channel = new ChannelSpec();
         channel.config_ = config;
         channel.useChannel_ = use;
         channel.exposure_ = exp;
         channel.doZStack_ = doZStack;
         channel.zOffset_ = zOffset;
         channel.contrast8_ = c8;
         channel.contrast16_ = c16;
         channel.color_ = c;
         channel.skipFactorFrame_ = skip;
         channels_.add(channel);
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
      return addChannel(config, exp, true, zOffset, c8, c16, skip, c, true);
   }

   public void setSaveFiles(boolean selected) {
      saveFiles_ = selected;
   }

   public boolean getSaveFiles() {
      return saveFiles_;
   }

   public void setDisplayMode(int mode) {
      //Ignore
   }

   public int getSliceMode() {
      return sliceMode_;
   }

   public int getDisplayMode() {
      return 0;
   }

   public void setSliceMode(int mode) {
      sliceMode_ = mode;
   }

   public int getPositionMode() {
      return positionMode_;
   }

   public void setPositionMode(int mode) {
      positionMode_ = mode;
   }

   public void enableAutoFocus(boolean enabled) {
      useAutoFocus_ = enabled;
   }

   public boolean isAutoFocusEnabled() {
      return useAutoFocus_;
   }

   public int getAfSkipInterval() {
      return afSkipInterval_;
   }

   public void setAfSkipInterval(int interval) {
      afSkipInterval_ = interval;
   }

   public void setParameterPreferences(Preferences prefs) {
      // do nothing
   }

   public void setSingleFrame(boolean selected) {
      //Ignore
   }

   public void setSingleWindow(boolean selected) {
      //Ignore
   }

   public String installAutofocusPlugin(String className) {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public String getVerboseSummary() {
      int numFrames = numFrames_;
      if (!useFrames_) {
         numFrames = 1;
      }
      int numSlices = useSlices_ ? (int) (1 + Math.abs(sliceZTopUm_-sliceZBottomUm_)/sliceZStepUm_) : 1;
      if (!useSlices_) {
         numSlices = 1;
      }
      int numPositions = Math.max(1, posList_.getNumberOfPositions());
      if (!useMultiPosition_) {
         numPositions = 1;
      }


      int numChannels = 0;
      if (useChannels_) {
         for (ChannelSpec channel:channels_)
            if (channel.useChannel_)
               ++numChannels;
         }
      else {
         numChannels = 1;
      }

      int totalImages = numFrames * numSlices * numChannels * numPositions;
      double totalDurationSec = interval_ * numFrames / 1000.0;
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
      if (useMultiPosition_ && useFrames_) {
         if (positionMode_ == PositionMode.TIME_LAPSE) {
            ptSetting = "Position, Time";

         } else if (positionMode_ == PositionMode.MULTI_FIELD) {
            ptSetting = "Time, Position";
         }
      } else if (useMultiPosition_ && !useFrames_) {
         ptSetting = "Position";
      } else if (!useMultiPosition_ && useFrames_) {
         ptSetting = "Time";
      }

      String csSetting = null;

      if (useSlices_ && useChannels_) {
         if (sliceMode_ == SliceMode.CHANNELS_FIRST) {
            csSetting = "Channel, Slice";
         } else {
            csSetting = "Slice, Channel";
         }
      } else if (useSlices_ && !useChannels_) {
         csSetting = "Slice";
      } else if (!useSlices_ && useChannels_) {
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

   public String[] getAvailableGroups() {
      StrVector groups;
      try {
         groups = core_.getAllowedPropertyValues("Core", "ChannelGroup");
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
         return new String[0];
      }
      ArrayList<String> strGroups = new ArrayList<String>();
      for (String group:groups) {
         if (groupIsEligibleChannel(group)) {
            strGroups.add(group);
         }
      }

      return strGroups.toArray(new String[0]);
   }

   public double getCurrentZPos() {
      if (isFocusStageAvailable()) {
         double z = 0.0;
         try {
            //core_.waitForDevice(zstage_);
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

   public boolean isPaused() {
      return pipeline_.isPaused();
   }

   public void restoreSystem() {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   protected boolean isFocusStageAvailable() {
      if (zstage_ != null && zstage_.length() > 0) {
         return true;
      } else {
         return false;
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
}
