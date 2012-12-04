/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.acquisition;

import java.awt.Color;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.prefs.Preferences;
import javax.swing.JOptionPane;
import mmcorej.CMMCore;
import mmcorej.Configuration;
import mmcorej.PropertySetting;
import mmcorej.StrVector;
import mmcorej.TaggedImage;
import org.json.JSONObject;
import org.micromanager.api.AcquisitionEngine;
import org.micromanager.api.DataProcessor;
import org.micromanager.api.ImageCache;
import org.micromanager.api.IAcquisitionEngine2010;
import org.micromanager.api.ScriptInterface;
import org.micromanager.internalinterfaces.AcqSettingsListener;
import org.micromanager.navigation.PositionList;
import org.micromanager.utils.AcqOrderMode;
import org.micromanager.utils.AutofocusManager;
import org.micromanager.utils.ChannelSpec;
import org.micromanager.utils.ContrastSettings;
import org.micromanager.utils.MMException;
import org.micromanager.utils.NumberUtils;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author arthur
 */
public class AcquisitionWrapperEngine implements AcquisitionEngine {

   private CMMCore core_;
   protected ScriptInterface gui_;
   private PositionList posList_;
   private String zstage_;
   private double sliceZStepUm_;
   private double sliceZBottomUm_;
   private double sliceZTopUm_;
   private boolean useSlices_;
   private boolean useFrames_;
   private boolean useChannels_;
   private boolean useMultiPosition_;
   private boolean keepShutterOpenForStack_;
   private boolean keepShutterOpenForChannels_;
   private ArrayList<ChannelSpec> channels_ = new ArrayList<ChannelSpec>();
   private String rootName_;
   private String dirName_;
   private int numFrames_;
   private double interval_;
   private double minZStepUm_;
   private String comment_;
   private boolean saveFiles_;
   private int acqOrderMode_;
   private boolean useAutoFocus_;
   private int afSkipInterval_;
   protected List<DataProcessor<TaggedImage>> taggedImageProcessors_;
   private List<Class> imageRequestProcessors_;
   private boolean absoluteZ_;
   private IAcquisitionEngine2010 acquisitionEngine2010;
   private ArrayList<Double> customTimeIntervalsMs_;
   private boolean useCustomIntervals_;
   protected JSONObject summaryMetadata_;
   protected ImageCache imageCache_;
   private ArrayList<AcqSettingsListener> settingsListeners_;

   public AcquisitionWrapperEngine() {
      imageRequestProcessors_ = new ArrayList<Class>();
      taggedImageProcessors_ = new ArrayList<DataProcessor<TaggedImage>>();
      useCustomIntervals_ = false;
      settingsListeners_ = new ArrayList<AcqSettingsListener>();
   }

   public String acquire() throws MMException {
      return runAcquisition(getSequenceSettings());
   }

   public void addSettingsListener(AcqSettingsListener listener) {
       settingsListeners_.add(listener);
   }
   
   public void removeSettingsListener(AcqSettingsListener listener) {
       settingsListeners_.remove(listener);
   }
   
   public void settingsChanged() {
       for (AcqSettingsListener listener:settingsListeners_) {
           listener.settingsChanged();
       }
   }
   
   protected IAcquisitionEngine2010 getAcquisitionEngine2010() {
      if (acquisitionEngine2010 == null) {
         acquisitionEngine2010 = gui_.getAcquisitionEngine2010();
      }
      return acquisitionEngine2010;
   }

   protected String runAcquisition(SequenceSettings acquisitionSettings) {
      if (!saveFiles_ || this.enoughDiskSpace()) {
         try {
            DefaultTaggedImagePipeline taggedImagePipeline = new DefaultTaggedImagePipeline(
                    getAcquisitionEngine2010(),
                    acquisitionSettings,
                    taggedImageProcessors_,
                    gui_,
                    acquisitionSettings.save);
            summaryMetadata_ = taggedImagePipeline.summaryMetadata_;
            imageCache_ = taggedImagePipeline.imageCache_;
            return taggedImagePipeline.acqName_;
         } catch (Throwable ex) {
            ReportingUtils.showError(ex);
            return null;
         }
      } else {
         ReportingUtils.showError("Not enough space on disk to save the requested image set; acquisition canceled.");
         return null;
      }

   }

   private int getNumChannels() {
      int numChannels = 0;
      if (useChannels_) {
         for (ChannelSpec channel : channels_) {
            if (channel.useChannel_) {
               ++numChannels;
            }
         }
      } else {
         numChannels = 1;
      }
      return numChannels;
   }

   public int getNumFrames() {
      int numFrames = numFrames_;
      if (!useFrames_) {
         numFrames = 1;
      }
      return numFrames;
   }

   private int getNumPositions() {
      int numPositions = Math.max(1, posList_.getNumberOfPositions());
      if (!useMultiPosition_) {
         numPositions = 1;
      }
      return numPositions;
   }

   private int getNumSlices() {
      int numSlices = useSlices_ ? (int) (1 + Math.abs(sliceZTopUm_ - sliceZBottomUm_) / sliceZStepUm_) : 1;
      if (!useSlices_) {
         numSlices = 1;
      }
      return numSlices;
   }

   private int getTotalImages() {
      int totalImages = getNumFrames() * getNumSlices() * getNumChannels() * getNumPositions();
      return totalImages;
   }

   private long getTotalMB() {
      CMMCore core = gui_.getMMCore();
      long totalMB = core.getImageWidth() * core.getImageHeight() * core.getBytesPerPixel() * ((long) getTotalImages()) / 1048576L;
      return totalMB;
   }

   private void updateChannelCameras() {
      for (ChannelSpec channel : channels_) {
         channel.camera_ = getSource(channel);
      }
   }

   /*
    * Attach a runnable to the acquisition engine. Each index (f, p, c, s) can
    * be specified. Passing a value of -1 results in the runnable being attached
    * at all values of that index. For example, if the first argument is -1,
    * then the runnable will execute at every frame.
    */
   public void attachRunnable(int frame, int position, int channel, int slice, Runnable runnable) {
      getAcquisitionEngine2010().attachRunnable(frame, position, channel, slice, runnable);
   }
   /*
    * Clear all attached runnables from the acquisition engine.
    */

   public void clearRunnables() {
      getAcquisitionEngine2010().clearRunnables();
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

   /**
    * @deprecated
    */
   public void addImageProcessor(Class taggedImageProcessorClass) {
      try {
         taggedImageProcessors_.add(
                 (DataProcessor<TaggedImage>) taggedImageProcessorClass.newInstance());
      } catch (InstantiationException ex) {
         ReportingUtils.logError(ex);
      } catch (IllegalAccessException ex) {
         ReportingUtils.logError(ex);
      }
   }

   /**
    * @deprecated
    */
   public void removeImageProcessor(Class taggedImageProcessorClass) {
      for (DataProcessor<TaggedImage> proc:taggedImageProcessors_) {
         if (proc.getClass() == taggedImageProcessorClass) {
            taggedImageProcessors_.remove(proc);
         }
      }
   }

   public void addImageProcessor(DataProcessor<TaggedImage> taggedImageProcessor) {
      taggedImageProcessors_.add(taggedImageProcessor);
   }

   public void removeImageProcessor(DataProcessor<TaggedImage> taggedImageProcessor) {
      taggedImageProcessors_.remove(taggedImageProcessor);
   }

   public SequenceSettings getSequenceSettings() {
      SequenceSettings acquisitionSettings = new SequenceSettings();

      updateChannelCameras();

      // Frames
      if (useFrames_) {
         if (useCustomIntervals_) {
            acquisitionSettings.customIntervalsMs = customTimeIntervalsMs_;
            acquisitionSettings.numFrames = acquisitionSettings.customIntervalsMs.size();
         } else {
            acquisitionSettings.numFrames = numFrames_;
            acquisitionSettings.intervalMs = interval_;
         }
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
         String zdrive = core_.getFocusDevice();
         acquisitionSettings.zReference = (zdrive.length() > 0)
                 ? core_.getPosition(core_.getFocusDevice()) : 0.0;
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
      }
      // Channels

      if (this.useChannels_) {
         for (ChannelSpec channel : channels_) {
            if (channel.useChannel_) {
               acquisitionSettings.channels.add(channel);
            }
         }
      }

      // Positions
      if (this.useMultiPosition_) {
         acquisitionSettings.positions.addAll(Arrays.asList(posList_.getPositions()));
      }


      //timeFirst = true means that time points are collected at each position
      acquisitionSettings.timeFirst = (acqOrderMode_ == AcqOrderMode.POS_TIME_CHANNEL_SLICE
              || acqOrderMode_ == AcqOrderMode.POS_TIME_SLICE_CHANNEL);
      acquisitionSettings.slicesFirst = (acqOrderMode_ == AcqOrderMode.POS_TIME_CHANNEL_SLICE
              || acqOrderMode_ == AcqOrderMode.TIME_POS_CHANNEL_SLICE);

      acquisitionSettings.useAutofocus = useAutoFocus_;
      acquisitionSettings.skipAutofocusCount = afSkipInterval_;

      acquisitionSettings.keepShutterOpenChannels = keepShutterOpenForChannels_;
      acquisitionSettings.keepShutterOpenSlices = keepShutterOpenForStack_;

      acquisitionSettings.save = saveFiles_;
      if (saveFiles_) {
         acquisitionSettings.root = rootName_;
         acquisitionSettings.prefix = dirName_;
      }
      acquisitionSettings.comment = comment_;
      return acquisitionSettings;
   }

//////////////////// Actions ///////////////////////////////////////////
   public void stop(boolean interrupted) {
      try {
         if (acquisitionEngine2010 != null) {
            acquisitionEngine2010.stop();
         }
      } catch (Exception ex) {
         ReportingUtils.showError(ex, "Acquisition engine stop request failed");
      }
   }

   public boolean abortRequest() {
      if (isAcquisitionRunning()) {
         int result = JOptionPane.showConfirmDialog(null,
                 "Abort current acquisition task?",
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
      return acquisitionEngine2010.stopHasBeenRequested();
   }

   public void shutdown() {
      stop(true);
   }

   public void setPause(boolean state) {
      if (state) {
         acquisitionEngine2010.pause();
      } else {
         acquisitionEngine2010.resume();
      }
   }

//// State Queries /////////////////////////////////////////////////////
   public boolean isAcquisitionRunning() {
      if (acquisitionEngine2010 != null) {
         return acquisitionEngine2010.isRunning();
      } else {
         return false;
      }
   }

   public boolean isFinished() {
      if (acquisitionEngine2010 != null) {
         return acquisitionEngine2010.isFinished();
      } else {
         return false;
      }
   }

   public boolean isMultiFieldRunning() {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public long getNextWakeTime() {
      return acquisitionEngine2010.nextWakeTime();
   }


//////////////////// Setters and Getters ///////////////////////////////
   public void setCore(CMMCore core_, AutofocusManager afMgr) {
      this.core_ = core_;
   }

   public void setPositionList(PositionList posList) {
      posList_ = posList;
   }

   public void setParentGUI(ScriptInterface parent) {
      gui_ = parent;
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
         return "";
      }

      String[] groups = getAvailableGroups();

      if (groups == null || groups.length < 1) {
         return "";
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
      if (channels_ != null) {
         channels_.clear();
      }
      numFrames_ = 0;
   }

   public void setFrames(int numFrames, double interval) {
      numFrames_ = numFrames;
      interval_ = interval;
   }

   public double getMinZStepUm() {
      return minZStepUm_;
   }

   public void setSlices(double bottom, double top, double step, boolean absolute) {
      sliceZBottomUm_ = bottom;
      sliceZTopUm_ = top;
      sliceZStepUm_ = step;
      absoluteZ_ = absolute;
      this.settingsChanged();
   }

   public boolean getZAbsoluteMode() {
       return absoluteZ_;
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
      settingsChanged();
   }

   /*
    * @deprecated
    */
    @Override
    public boolean addChannel(String config, double exp, Boolean doZStack, double zOffset, ContrastSettings con8, ContrastSettings con16, int skip, Color c, boolean use) {
        return addChannel(config, exp, doZStack, zOffset, con8, skip, c, use);
    }
    
   /**
    * Add new channel if the current state of the hardware permits.
    *
    * @param config - configuration name
    * @param exp
    * @param doZStack
    * @param zOffset
    * @param c
    * @return - true if successful
    */
   public boolean addChannel(String config, double exp, Boolean doZStack, double zOffset, ContrastSettings con, int skip, Color c, boolean use) {
      if (isConfigAvailable(config)) {
         ChannelSpec channel = new ChannelSpec();
         channel.config_ = config;
         channel.useChannel_ = use;
         channel.exposure_ = exp;
         channel.doZStack_ = doZStack;
         channel.zOffset_ = zOffset;
         channel.contrast_ = con;
         channel.color_ = c;
         channel.skipFactorFrame_ = skip;
         channels_.add(channel);
         return true;
      } else {
         ReportingUtils.logError("\"" + config + "\" is not found in the current Channel group.");
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
      return addChannel(config, exp, true, zOffset, c16, skip, c, true);
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

   public int getAcqOrderMode() {
      return acqOrderMode_;
   }

   public int getDisplayMode() {
      return 0;
   }

   public void setAcqOrderMode(int mode) {
      acqOrderMode_ = mode;
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

   protected boolean enoughDiskSpace() {
      File root = new File(rootName_);
      //Need to find a file that exists to check space
      while (!root.exists()) {
         root = root.getParentFile();
         if (root == null) {
            return false;
         }
      }
      long usableMB = root.getUsableSpace() / (1024 * 1024);
      return (1.25 * getTotalMB()) < usableMB;
   }

   public String getVerboseSummary() {
      int numFrames = getNumFrames();
      int numSlices = getNumSlices();
      int numPositions = getNumPositions();
      int numChannels = getNumChannels();

      int totalImages = getTotalImages();
      long totalMB = getTotalMB();

      double totalDurationSec = 0;
      if (!useCustomIntervals_) {
         totalDurationSec = interval_ * numFrames / 1000.0;
      } else {
         for (Double d : customTimeIntervalsMs_) {
            totalDurationSec += d / 1000.0;
         }
      }
      int hrs = (int) (totalDurationSec / 3600);
      double remainSec = totalDurationSec - hrs * 3600;
      int mins = (int) (remainSec / 60);
      remainSec = remainSec - mins * 60;

      Runtime rt = Runtime.getRuntime();
      rt.gc();

      String txt;
      txt =
              "Number of time points: " + (!useCustomIntervals_
              ? numFrames : customTimeIntervalsMs_.size())
              + "\nNumber of positions: " + numPositions
              + "\nNumber of slices: " + numSlices
              + "\nNumber of channels: " + numChannels
              + "\nTotal images: " + totalImages
              + "\nTotal memory: " + (totalMB <= 1024 ? totalMB + " MB" : NumberUtils.doubleToDisplayString(totalMB/1024.0) + " GB")
              + "\nDuration: " + hrs + "h " + mins + "m " + NumberUtils.doubleToDisplayString(remainSec) + "s";

      if (useFrames_ || useMultiPosition_ || useChannels_ || useSlices_) {
         StringBuffer order = new StringBuffer("\nOrder: ");
         if (useFrames_ && useMultiPosition_) {
            if (acqOrderMode_ == AcqOrderMode.TIME_POS_CHANNEL_SLICE
                    || acqOrderMode_ == AcqOrderMode.TIME_POS_SLICE_CHANNEL) {
               order.append("Time, Position");
            } else {
               order.append("Position, Time");
            }
         } else if (useFrames_) {
            order.append("Time");
         } else if (useMultiPosition_) {
            order.append("Position");
         }

         if ((useFrames_ || useMultiPosition_) && (useChannels_ || useSlices_)) {
            order.append(", ");
         }

         if (useChannels_ && useSlices_) {
            if (acqOrderMode_ == AcqOrderMode.TIME_POS_CHANNEL_SLICE
                    || acqOrderMode_ == AcqOrderMode.POS_TIME_CHANNEL_SLICE) {
               order.append("Channel, Slice");
            } else {
               order.append("Slice, Channel");
            }
         } else if (useChannels_) {
            order.append("Channel");
         } else if (useSlices_) {
            order.append("Slice");
         }

         return txt + order.toString();
      } else {
         return txt;
      }
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
      for (String group : groups) {
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
            ReportingUtils.showError(e);
         }
         return z;
      }
      return 0;
   }

   public boolean isPaused() {
      return acquisitionEngine2010.isPaused();
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

   /**
    * @return the taggedImageProcessors_
    */
    @Override
   public List<DataProcessor<TaggedImage>> getImageProcessors() {
      return taggedImageProcessors_;
   }

   public void setCustomTimeIntervals(double[] customTimeIntervals) {
      if (customTimeIntervals == null || customTimeIntervals.length == 0) {
         customTimeIntervalsMs_ = null;
         enableCustomTimeIntervals(false);
      } else {
         enableCustomTimeIntervals(true);
         customTimeIntervalsMs_ = new ArrayList<Double>();
         for (double d : customTimeIntervals) {
            customTimeIntervalsMs_.add(d);
         }
      }
   }

   public double[] getCustomTimeIntervals() {
      if (customTimeIntervalsMs_ == null) {
         return null;
      }
      double[] intervals = new double[customTimeIntervalsMs_.size()];
      for (int i = 0; i < customTimeIntervalsMs_.size(); i++) {
         intervals[i] = customTimeIntervalsMs_.get(i);
      }
      return intervals;

   }

   public void enableCustomTimeIntervals(boolean enable) {
      useCustomIntervals_ = enable;
   }

   public boolean customTimeIntervalsEnabled() {
      return useCustomIntervals_;
   }

   /*
    * Returns the summary metadata associated with the most recent acquisition.
    */
   public JSONObject getSummaryMetadata() {
      return summaryMetadata_;
   }

   /*
    * Returns the image cache associated with the most recent acquisition.
    */
   public ImageCache getImageCache() {
      return imageCache_;
   }

    @Override
    public String getComment() {
        return this.comment_;
    }

}
