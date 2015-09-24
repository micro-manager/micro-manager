
package org.micromanager.acquisition;

import java.awt.Color;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.prefs.Preferences;
import java.util.Set;

import javax.swing.JOptionPane;

import mmcorej.CMMCore;
import mmcorej.Configuration;
import mmcorej.PropertySetting;
import mmcorej.StrVector;
import mmcorej.TaggedImage;

import org.json.JSONObject;
import org.micromanager.api.DataProcessor;
import org.micromanager.api.ImageCache;
import org.micromanager.api.IAcquisitionEngine2010;
import org.micromanager.api.PositionList;
import org.micromanager.api.ScriptInterface;
import org.micromanager.api.SequenceSettings;
import org.micromanager.events.EventManager;
import org.micromanager.events.PipelineEvent;
import org.micromanager.events.ProcessorEvent;
import org.micromanager.events.SummaryMetadataEvent;
import org.micromanager.internalinterfaces.AcqSettingsListener;
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
   protected ScriptInterface studio_;
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
   protected HashMap<String, Class<? extends DataProcessor<TaggedImage>>> nameToProcessorClass_;
   protected List<DataProcessor<TaggedImage>> taggedImageProcessors_;
   private boolean absoluteZ_;
   private IAcquisitionEngine2010 acquisitionEngine2010;
   private ArrayList<Double> customTimeIntervalsMs_;
   private boolean useCustomIntervals_;
   protected JSONObject summaryMetadata_;
   protected ImageCache imageCache_;
   private ArrayList<AcqSettingsListener> settingsListeners_;
   private AcquisitionManager acqManager_;
   private int cameraTimeout_;

   public AcquisitionWrapperEngine(AcquisitionManager mgr) {
      nameToProcessorClass_ = new HashMap<String, Class<? extends DataProcessor<TaggedImage>>>();
      taggedImageProcessors_ = new ArrayList<DataProcessor<TaggedImage>>();
      useCustomIntervals_ = false;
      settingsListeners_ = new ArrayList<AcqSettingsListener>();
      acqManager_ = mgr;
   }

   @Override
   public String acquire() throws MMException {
      return runAcquisition(getSequenceSettings(), acqManager_);
   }

   @Override
   public void addSettingsListener(AcqSettingsListener listener) {
       settingsListeners_.add(listener);
   }
   
   @Override
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
         acquisitionEngine2010 = studio_.getAcquisitionEngine2010();
      }
      return acquisitionEngine2010;
   }

   protected String runAcquisition(SequenceSettings acquisitionSettings, 
           AcquisitionManager acqManager) {
      //Make sure computer can write to selected location and that there is enough space to do so
      if (saveFiles_) {
         File root = new File(rootName_);
         if (!root.canWrite()) {
            int result = JOptionPane.showConfirmDialog(null, 
                    "The specified root directory\n" + root.getAbsolutePath() +
                    "\ndoes not exist. Create it?", "Directory not found.", 
                    JOptionPane.YES_NO_OPTION);
            if (result == JOptionPane.YES_OPTION) {
               root.mkdirs();
               if (!root.canWrite()) {
                  ReportingUtils.showError(
                          "Unable to save data to selected location: check that location exists.\nAcquisition canceled.");
                  return null;
               }
            } else {
               ReportingUtils.showMessage("Acquisition canceled.");
               return null;
            }
         } else if (!this.enoughDiskSpace()) {
            ReportingUtils.showError(
                    "Not enough space on disk to save the requested image set; acquisition canceled.");
            return null;
         }
      }

      // If we don't stop Live here, we run into trouble if any processors are
      // active, because the processor pipeline would get rewired before it
      // finishes processing live images.
      studio_.enableLiveMode(false);

      try {
         // Start up the acquisition engine
         BlockingQueue<TaggedImage> engineOutputQueue = getAcquisitionEngine2010().run(
                 acquisitionSettings, true,
                 studio_.getPositionList(),
                 studio_.getAutofocusManager().getDevice());
         summaryMetadata_ = getAcquisitionEngine2010().getSummaryMetadata();
         org.micromanager.events.EventManager.post(
               new SummaryMetadataEvent(summaryMetadata_));

         // Run the Acquisition Engine output through a pipeline of ImageProcessors
         BlockingQueue<TaggedImage> procStackOutputQueue = ProcessorStack.run(
                 engineOutputQueue, taggedImageProcessors_);

         // Create an MMAcquisition object, which will result in an ImageCache
         // and VirtualImageDisplay if desired
         String acqName = acqManager.createAcquisition(
                 summaryMetadata_, acquisitionSettings.save, this,
                 studio_.getHideMDADisplayOption());
         MMAcquisition acq = acqManager.getAcquisition(acqName);
         imageCache_ = acq.getImageCache();

         // Start pumping processed images into the ImageCache
         DefaultTaggedImageSink sink = new DefaultTaggedImageSink(
                 procStackOutputQueue, imageCache_);
         sink.start(new Runnable() {
            @Override
            public void run() {
               getAcquisitionEngine2010().stop();
            }
         });
        
         return acqName;

      } catch (Throwable ex) {
         ReportingUtils.showError(ex);
         return null;
      }
   }

   private int getNumChannels() {
      int numChannels = 0;
      if (useChannels_) {
         for (ChannelSpec channel : channels_) {
            if (channel.useChannel) {
               ++numChannels;
            }
         }
      } else {
         numChannels = 1;
      }
      return numChannels;
   }

   @Override
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
      if (!useSlices_) {
         return 1;
      }
      if (sliceZStepUm_ == 0) {
         // XXX How should this be handled?
         return Integer.MAX_VALUE;
      }
      return 1 + (int)Math.abs((sliceZTopUm_ - sliceZBottomUm_) / sliceZStepUm_);
   }

   private int getTotalImages() {
      int totalImages = getNumFrames() * getNumSlices() * getNumChannels() * getNumPositions();
      return totalImages;
   }

   private long getTotalMB() {
      CMMCore core = studio_.getMMCore();
      long totalMB = core.getImageWidth() * core.getImageHeight() * core.getBytesPerPixel() * ((long) getTotalImages()) / 1048576L;
      return totalMB;
   }

   private void updateChannelCameras() {
      for (ChannelSpec channel : channels_) {
         channel.camera = getSource(channel);
      }
   }

   /*
    * Attach a runnable to the acquisition engine. Each index (f, p, c, s) can
    * be specified. Passing a value of -1 results in the runnable being attached
    * at all values of that index. For example, if the first argument is -1,
    * then the runnable will execute at every frame.
    */
   @Override
   public void attachRunnable(int frame, int position, int channel, int slice, Runnable runnable) {
      getAcquisitionEngine2010().attachRunnable(frame, position, channel, slice, runnable);
   }
   /*
    * Clear all attached runnables from the acquisition engine.
    */

   @Override
   public void clearRunnables() {
      getAcquisitionEngine2010().clearRunnables();
   }

   private String getSource(ChannelSpec channel) {
      try {
         Configuration state = core_.getConfigState(core_.getChannelGroup(), channel.config);
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

   @Override
   public void addImageProcessor(DataProcessor<TaggedImage> taggedImageProcessor) {
      if (!taggedImageProcessors_.contains(taggedImageProcessor)) {
         taggedImageProcessors_.add(taggedImageProcessor);
         EventManager.post(new PipelineEvent(taggedImageProcessors_));
      }
   }

   @Override
   public void removeImageProcessor(DataProcessor<TaggedImage> taggedImageProcessor) {
      taggedImageProcessors_.remove(taggedImageProcessor);
      taggedImageProcessor.dispose();
      EventManager.post(new PipelineEvent(taggedImageProcessors_));
   }

   @Override
   public void setImageProcessorPipeline(List<DataProcessor<TaggedImage>> pipeline) {
      taggedImageProcessors_.clear();
      taggedImageProcessors_.addAll(pipeline);
      EventManager.post(new PipelineEvent(taggedImageProcessors_));
   }

   @Override
   public ArrayList<DataProcessor<TaggedImage>> getImageProcessorPipeline() {
      return new ArrayList<DataProcessor<TaggedImage>>(taggedImageProcessors_);
   }

   @Override
   public void registerProcessorClass(Class<? extends DataProcessor<TaggedImage>> processorClass, String name) {
      if (nameToProcessorClass_.get(name) != null) {
         ReportingUtils.logError("Tried to register an additional DataProcessor under the name \"" + name + "\"; ignoring it.");
      }
      else {
         nameToProcessorClass_.put(name, processorClass);
         // Post an event informing listeners that there's a newly-registered
         // DataProcessor class.
         EventManager.post(new ProcessorEvent(name, processorClass));
      }
   }

   @Override
   public List<String> getSortedDataProcessorNames() {
      Set<String> keys = nameToProcessorClass_.keySet();
      ArrayList<String> sortedKeys = new ArrayList<String>();
      sortedKeys.addAll(keys);
      Collections.sort(sortedKeys);
      return sortedKeys;
   }

   @Override
   public DataProcessor<TaggedImage> makeProcessor(String name, ScriptInterface gui) {
      Class<? extends DataProcessor<TaggedImage>> processorClass = nameToProcessorClass_.get(name);
      DataProcessor<TaggedImage> newProcessor;
      try {
         newProcessor = processorClass.newInstance();
         newProcessor.setApp(gui);
         addImageProcessor(newProcessor);
      }
      catch (Exception ex) {
         ReportingUtils.logError("Failed to create processor " + name + " mapped to class " + processorClass + ": " + ex);
         newProcessor = null;
      }
      return newProcessor;
   }

   @Override
   public DataProcessor<TaggedImage> getProcessorRegisteredAs(String name) {
      Class<? extends DataProcessor<TaggedImage>> processorClass = nameToProcessorClass_.get(name);
      for (DataProcessor<TaggedImage> processor : taggedImageProcessors_) {
         if (processor.getClass() == processorClass) {
            return processor;
         }
      }
      return null;
   }

   @Override
   public String getNameForProcessorClass(Class<? extends DataProcessor<TaggedImage>> processorClass) {
      for (String name : nameToProcessorClass_.keySet()) {
         if (nameToProcessorClass_.get(name) == processorClass) {
            return name;
         }
      }
      return null;
   }

   @Override
   public void disposeProcessors() {
      for (DataProcessor<TaggedImage> processor : taggedImageProcessors_) {
         processor.dispose();
      }
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
         double start = sliceZBottomUm_;
         double stop = sliceZTopUm_;
         double step = Math.abs(sliceZStepUm_);
         if (step == 0.0) {
            throw new UnsupportedOperationException("zero Z step size");
         }
         int count = getNumSlices();
         if (start > stop) {
            step = -step;
         }
         for (int i = 0; i < count; i++) {
            acquisitionSettings.slices.add(start + i * step);
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
            if (channel.useChannel) {
               acquisitionSettings.channels.add(channel);
            }
         }
         acquisitionSettings.channelGroup = core_.getChannelGroup();
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
      acquisitionSettings.usePositionList = this.useMultiPosition_;
      acquisitionSettings.cameraTimeout = cameraTimeout_;
      return acquisitionSettings;
   }

   public void setSequenceSettings(SequenceSettings ss) {
      
      updateChannelCameras();

      // Frames
      useFrames_ = true;
      if (ss.customIntervalsMs != null && ss.customIntervalsMs.size() > 0) {
         useCustomIntervals_ = true;
         customTimeIntervalsMs_ = ss.customIntervalsMs;
         numFrames_ = ss.customIntervalsMs.size();
      } else {
         useCustomIntervals_ = false;
         numFrames_ = ss.numFrames;
         interval_ = ss.intervalMs;
      }

      // Slices
      useSlices_ = true;
      if (ss.slices.size() == 0)
         useSlices_ = false;
      else if (ss.slices.size() == 1) {
         sliceZBottomUm_ = ss.slices.get(0);
         sliceZTopUm_ = sliceZBottomUm_;
         sliceZStepUm_ = 0.0;
      } else {
         sliceZBottomUm_ = ss.slices.get(0);
         sliceZTopUm_ = ss.slices.get(ss.slices.size()-1);
         sliceZStepUm_ = ss.slices.get(1) - ss.slices.get(0);
         if (sliceZBottomUm_ > sliceZBottomUm_)
            sliceZStepUm_ = -sliceZStepUm_;
      }

      absoluteZ_ = !ss.relativeZSlice;
      // NOTE: there is no adequate setting for ss.zReference
      
      // Channels
      if (ss.channels.size() > 0)
         useChannels_ = true;
      else
         useChannels_ = false;
         
      channels_ = ss.channels;
       // no channel group

      //timeFirst = true means that time points are collected at each position      
      if (ss.timeFirst && ss.slicesFirst) {
         acqOrderMode_ = AcqOrderMode.POS_TIME_CHANNEL_SLICE;
      }
      
      if (ss.timeFirst && !ss.slicesFirst) {
         acqOrderMode_ = AcqOrderMode.POS_TIME_SLICE_CHANNEL;
      }
      
      if (!ss.timeFirst && ss.slicesFirst) {
         acqOrderMode_ = AcqOrderMode.TIME_POS_CHANNEL_SLICE;
      }

      if (!ss.timeFirst && !ss.slicesFirst) {
         acqOrderMode_ = AcqOrderMode.TIME_POS_SLICE_CHANNEL;
      }

      useAutoFocus_ = ss.useAutofocus;
      afSkipInterval_ = ss.skipAutofocusCount;

      keepShutterOpenForChannels_ = ss.keepShutterOpenChannels;
      keepShutterOpenForStack_ = ss.keepShutterOpenSlices;

      saveFiles_ = ss.save;
      rootName_ = ss.root;
      dirName_ = ss.prefix;
      comment_ = ss.comment;
      
      useMultiPosition_ = ss.usePositionList;
      cameraTimeout_ = ss.cameraTimeout;
   }

//////////////////// Actions ///////////////////////////////////////////
   @Override
   public void stop(boolean interrupted) {
      try {
         if (acquisitionEngine2010 != null) {
            acquisitionEngine2010.stop();
         }
      } catch (Exception ex) {
         ReportingUtils.showError(ex, "Acquisition engine stop request failed");
      }
   }

   @Override
   public boolean abortRequest() {
      if (isAcquisitionRunning()) {
         String[] options = { "Abort", "Cancel" };
         int result = JOptionPane.showOptionDialog(null,
                 "Abort current acquisition task?",
                 "Micro-Manager",
                 JOptionPane.DEFAULT_OPTION,
                 JOptionPane.QUESTION_MESSAGE, null,
                 options, options[1]);
         if (result == 0) {
            stop(true);
            return true;
         }
      }
      return false;
   }

   @Override
   public boolean abortRequested() {
      return acquisitionEngine2010.stopHasBeenRequested();
   }

   @Override
   public void shutdown() {
      stop(true);
   }

   @Override
   public void setPause(boolean state) {
      if (state) {
         acquisitionEngine2010.pause();
      } else {
         acquisitionEngine2010.resume();
      }
   }

//// State Queries /////////////////////////////////////////////////////
   @Override
   public boolean isAcquisitionRunning() {
      if (acquisitionEngine2010 != null) {
         return acquisitionEngine2010.isRunning();
      } else {
         return false;
      }
   }

   @Override
   public boolean isFinished() {
      if (acquisitionEngine2010 != null) {
         return acquisitionEngine2010.isFinished();
      } else {
         return false;
      }
   }

   @Override
   public boolean isMultiFieldRunning() {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   @Override
   public long getNextWakeTime() {
      return acquisitionEngine2010.nextWakeTime();
   }


//////////////////// Setters and Getters ///////////////////////////////
   @Override
   public void setCore(CMMCore core_, AutofocusManager afMgr) {
      this.core_ = core_;
   }

   @Override
   public void setPositionList(PositionList posList) {
      posList_ = posList;
   }

   @Override
   public void setParentGUI(ScriptInterface parent) {
      studio_ = parent;
   }

   @Override
   public void setZStageDevice(String stageLabel_) {
      zstage_ = stageLabel_;
   }

   @Override
   public void setUpdateLiveWindow(boolean b) {
      // do nothing
   }

   @Override
   public void setFinished() {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   @Override
   public int getCurrentFrameCount() {
      return 0;
   }

   @Override
   public double getFrameIntervalMs() {
      return interval_;
   }

   @Override
   public double getSliceZStepUm() {
      return sliceZStepUm_;
   }

   @Override
   public double getSliceZBottomUm() {
      return sliceZBottomUm_;
   }

   @Override
   public void setChannel(int row, ChannelSpec channel) {
      channels_.set(row, channel);
   }

   /**
    * Get first available config group
    */
   @Override
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
   @Override
   public String[] getChannelConfigs() {
      if (core_ == null) {
         return new String[0];
      }
      return core_.getAvailableConfigs(core_.getChannelGroup()).toArray();
   }

   @Override
   public String getChannelGroup() {
      return core_.getChannelGroup();
   }

   @Override
   public boolean setChannelGroup(String group) {
      if (groupIsEligibleChannel(group)) {
         try {
            core_.setChannelGroup(group);
         } catch (Exception e) {
            try {
               core_.setChannelGroup("");
            } catch (Exception ex) {
                ReportingUtils.showError(ex);
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
   @Override
   public void clear() {
      if (channels_ != null) {
         channels_.clear();
      }
      numFrames_ = 0;
   }

   @Override
   public void setFrames(int numFrames, double interval) {
      numFrames_ = numFrames;
      interval_ = interval;
   }

   @Override
   public double getMinZStepUm() {
      return minZStepUm_;
   }

   @Override
   public void setSlices(double bottom, double top, double step, boolean absolute) {
      sliceZBottomUm_ = bottom;
      sliceZTopUm_ = top;
      sliceZStepUm_ = step;
      absoluteZ_ = absolute;
      this.settingsChanged();
   }

   @Override
   public boolean getZAbsoluteMode() {
       return absoluteZ_;
   }
   
   @Override
   public boolean isFramesSettingEnabled() {
      return useFrames_;
   }

   @Override
   public void enableFramesSetting(boolean enable) {
      useFrames_ = enable;
   }

   @Override
   public boolean isChannelsSettingEnabled() {
      return useChannels_;
   }

   @Override
   public void enableChannelsSetting(boolean enable) {
      useChannels_ = enable;
   }

   @Override
   public boolean isZSliceSettingEnabled() {
      return useSlices_;
   }

   @Override
   public double getZTopUm() {
      return sliceZTopUm_;
   }

   @Override
   public void keepShutterOpenForStack(boolean open) {
      keepShutterOpenForStack_ = open;
   }

   @Override
   public boolean isShutterOpenForStack() {
      return keepShutterOpenForStack_;
   }

   @Override
   public void keepShutterOpenForChannels(boolean open) {
      keepShutterOpenForChannels_ = open;
   }

   @Override
   public boolean isShutterOpenForChannels() {
      return keepShutterOpenForChannels_;
   }

   @Override
   public void enableZSliceSetting(boolean boolean1) {
      useSlices_ = boolean1;
   }

   @Override
   public void enableMultiPosition(boolean selected) {
      useMultiPosition_ = selected;
   }

   @Override
   public boolean isMultiPositionEnabled() {
      return useMultiPosition_;
   }

   @Override
   public ArrayList<ChannelSpec> getChannels() {
      return channels_;
   }

   @Override
   public void setChannels(ArrayList<ChannelSpec> channels) {
      channels_ = channels;
   }

   @Override
   public String getRootName() {
      return rootName_;
   }

   @Override
   public void setRootName(String absolutePath) {
      rootName_ = absolutePath;
   }

   @Override
   public void setCameraConfig(String cfg) {
      // do nothing
   }

   @Override
   public void setDirName(String text) {
      dirName_ = text;
   }

   @Override
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
   @Override
   public boolean addChannel(String config, double exp, Boolean doZStack, double zOffset, ContrastSettings con, int skip, Color c, boolean use) {
      if (isConfigAvailable(config)) {
         ChannelSpec channel = new ChannelSpec();
         channel.config = config;
         channel.useChannel = use;
         channel.exposure = exp;
         channel.doZStack = doZStack;
         channel.zOffset = zOffset;
         channel.contrast = con;
         channel.color = c;
         channel.skipFactorFrame = skip;
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
    * @Deprecated
    */
   @Override
   public boolean addChannel(String config, double exp, double zOffset, ContrastSettings c8, ContrastSettings c16, int skip, Color c) {
      return addChannel(config, exp, true, zOffset, c16, skip, c, true);
   }

   @Override
   public void setSaveFiles(boolean selected) {
      saveFiles_ = selected;
   }

   @Override
   public boolean getSaveFiles() {
      return saveFiles_;
   }

   @Override
   public void setDisplayMode(int mode) {
      //Ignore
   }

   @Override
   public int getAcqOrderMode() {
      return acqOrderMode_;
   }

   @Override
   public int getDisplayMode() {
      return 0;
   }

   @Override
   public void setAcqOrderMode(int mode) {
      acqOrderMode_ = mode;
   }

   @Override
   public void enableAutoFocus(boolean enabled) {
      useAutoFocus_ = enabled;
   }

   @Override
   public boolean isAutoFocusEnabled() {
      return useAutoFocus_;
   }

   @Override
   public int getAfSkipInterval() {
      return afSkipInterval_;
   }

   @Override
   public void setAfSkipInterval(int interval) {
      afSkipInterval_ = interval;
   }

   public void setParameterPreferences(Preferences prefs) {
      // do nothing
   }

   @Override
   public void setSingleFrame(boolean selected) {
      //Ignore
   }

   @Override
   public void setSingleWindow(boolean selected) {
      //Ignore
   }

   @Override
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

   @Override
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
   @Override
   public boolean isConfigAvailable(String config) {
      StrVector vcfgs = core_.getAvailableConfigs(core_.getChannelGroup());
      for (int i = 0; i < vcfgs.size(); i++) {
         if (config.compareTo(vcfgs.get(i)) == 0) {
            return true;
         }
      }
      return false;
   }

   @Override
   public String[] getCameraConfigs() {
      if (core_ == null) {
         return new String[0];
      }
      return core_.getAvailableConfigs(cameraGroup_).toArray();
   }

   @Override
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

   @Override
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

   @Override
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

   @Override
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

   @Override
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

   @Override
   public void enableCustomTimeIntervals(boolean enable) {
      useCustomIntervals_ = enable;
   }

   @Override
   public boolean customTimeIntervalsEnabled() {
      return useCustomIntervals_;
   }

   /*
    * Returns the summary metadata associated with the most recent acquisition.
    */
   @Override
   public JSONObject getSummaryMetadata() {
      return summaryMetadata_;
   }

   /*
    * Returns the image cache associated with the most recent acquisition.
    */
   @Override
   public ImageCache getImageCache() {
      return imageCache_;
   }

    @Override
    public String getComment() {
        return this.comment_;
    }

}
