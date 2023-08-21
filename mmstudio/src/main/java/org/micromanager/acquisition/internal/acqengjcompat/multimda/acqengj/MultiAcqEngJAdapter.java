///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Henry Pinkard, Nico Stuurman
//
// COPYRIGHT:    Photomics Inc, 2022, Altos Labs, 2023
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


package org.micromanager.acquisition.internal.acqengjcompat.multimda.acqengj;

import com.google.common.eventbus.Subscribe;
import java.awt.Component;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import javax.swing.JOptionPane;
import mmcorej.CMMCore;
import mmcorej.Configuration;
import mmcorej.PropertySetting;
import mmcorej.StrVector;
import mmcorej.org.json.JSONObject;
import org.micromanager.AutofocusPlugin;
import org.micromanager.PositionList;
import org.micromanager.Studio;
import org.micromanager.acqj.api.AcquisitionAPI;
import org.micromanager.acqj.api.AcquisitionHook;
import org.micromanager.acqj.internal.Engine;
import org.micromanager.acqj.main.Acquisition;
import org.micromanager.acqj.main.AcquisitionEvent;
import org.micromanager.acqj.util.AcquisitionEventIterator;
import org.micromanager.acquisition.AcquisitionEndedEvent;
import org.micromanager.acquisition.ChannelSpec;
import org.micromanager.acquisition.SequenceSettings;
import org.micromanager.acquisition.internal.DefaultAcquisitionEndedEvent;
import org.micromanager.acquisition.internal.DefaultAcquisitionStartedEvent;
import org.micromanager.acquisition.internal.MMAcquisition;
import org.micromanager.acquisition.internal.acqengjcompat.AcqEngJAdapter;
import org.micromanager.acquisition.internal.acqengjcompat.MDAAcqEventModules;
import org.micromanager.data.DataProvider;
import org.micromanager.data.Datastore;
import org.micromanager.data.Pipeline;
import org.micromanager.data.SummaryMetadata;
import org.micromanager.data.internal.DefaultDatastore;
import org.micromanager.data.internal.DefaultSummaryMetadata;
import org.micromanager.display.DisplayWindow;
import org.micromanager.events.NewPositionListEvent;
import org.micromanager.events.internal.InternalShutdownCommencingEvent;
import org.micromanager.internal.interfaces.AcqSettingsListener;
import org.micromanager.internal.propertymap.NonPropertyMapJSONFormats;
import org.micromanager.internal.utils.AcqOrderMode;
import org.micromanager.internal.utils.NumberUtils;
import org.micromanager.internal.utils.ReportingUtils;

/**
 * This class translates the acquisition specified in the UI into acquisition events
 * for the acquisition engine.
 *
 * <p>AcquisitionEngine implements a subset of the functionality of AcqEngJ,
 * and this class enforces those specific assumptions. These include:
 * - The axes of the acquisition are limited to channel, slice, frame, and position
 * - The number of images and other parameters are all known at the start of acquisition
 */
public class MultiAcqEngJAdapter extends AcqEngJAdapter {

   private Acquisition currentMultiMDA_;

   private CMMCore core_;
   protected Studio studio_;
   private PositionList posList_;
   private String zStage_;

   private ArrayList<AcqSettingsListener> settingsListeners_;
   private List<Datastore> stores_;
   private List<Pipeline> pipelines_;
   private SequenceSettings timeLapseSettings_ = null;

   private double zStart_;
   private AutofocusPlugin autofocusMethod_;
   private boolean autofocusOn_;

   private long nextWakeTime_ = -1;

   private ArrayList<RunnablePlusIndices> runnables_ = new ArrayList<>();

   private class RunnablePlusIndices {
      int channel_;
      int slice_;
      int frame_;
      int position_;
      Runnable runnable_;

      public RunnablePlusIndices(Runnable r, int channel, int slice, int frame, int position) {
         runnable_ = r;
         channel_ = channel;
         slice_ = slice;
         frame_ = frame;
         position_ = position;
      }
   }

   /**
    * Constructor.
    *
    * @param studio The always present studio object that gives us everything.
    */
   public MultiAcqEngJAdapter(Studio studio) {
      super(studio);
      // Create AcqEngJ
      studio_ = studio;
      core_ = studio_.core();
      new Engine(core_);
      settingsListeners_ = new ArrayList<>();
   }

   /**
    * This is where the work happens.
    *
    * @param basicSettings Defines the time lapse and autofocus
    * @param sequenceSettings Acquisitions that should be executed consecutively.
    * @param positionLists PositionLists for each sequenceSetting (>=1).  The size of this list
    *                      must be equal to the size of sequenceSettings.
    * @return Datastores corresponding to sequenceSettings
    */
   public List<Datastore> runAcquisition(SequenceSettings basicSettings,
                                         List<SequenceSettings> sequenceSettings,
                                         List<PositionList> positionLists) {
      if (sequenceSettings.size() < 1 || positionLists.size() != sequenceSettings.size()) {
         studio_.logs().logError("Please use Position Lists for each acquisition");
         return null;
      }
      timeLapseSettings_ = basicSettings;
      stores_ = new ArrayList<>(sequenceSettings.size());
      pipelines_ = new ArrayList<>(sequenceSettings.size());
      for (int i = 0; i < sequenceSettings.size(); i++) {
         if (sequenceSettings.get(i).useCustomIntervals()) {
            studio_.logs().showError("Custom time intervals are not supported.");
            return null;
         }
         if (sequenceSettings.get(i).acqOrderMode() == AcqOrderMode.POS_TIME_CHANNEL_SLICE
               || sequenceSettings.get(i).acqOrderMode() == AcqOrderMode.POS_TIME_SLICE_CHANNEL) {
            // we only handle time first acquisitions (at least for now)
            studio_.logs().showError("PLease select Time first for all acquisitions");
            return null;
         }
         // Make sure computer can write to selected location and there is enough space to do so
         if (sequenceSettings.get(i).save()) {
            File root = new File(sequenceSettings.get(i).root());
            if (!root.canWrite()) {
               int result = JOptionPane.showConfirmDialog(null,
                     "The specified root directory\n" + root.getAbsolutePath()
                           + "\ndoes not exist. Create it?", "Directory not found.",
                     JOptionPane.YES_NO_OPTION);
               if (result == JOptionPane.YES_OPTION) {
                  if (!root.mkdirs() || !root.canWrite()) {
                     ReportingUtils.showError(
                           "Unable to save data to selected location: check that "
                                 + "location exists.\nAcquisition canceled.");
                     return null;
                  }
               } else {
                  ReportingUtils.showMessage("Acquisition canceled.");
                  return null;
               }
            } else if (!this.enoughDiskSpace(root)) {
               ReportingUtils.showError(
                     "Not enough space on disk to save the requested image set; "
                           + "acquisition canceled.");
               return null;
            }

            DefaultDatastore.setPreferredSaveMode(studio_, sequenceSettings.get(i).saveMode());
         }
      }

      try {
         // Start up the acquisition engine
         MultiAcqEngJMDADataSink sink = new MultiAcqEngJMDADataSink(studio_.events());
         currentMultiMDA_ = new Acquisition(sink);
         currentMultiMDA_.setDebugMode(core_.debugLogEnabled());

         loadRunnables(sequenceSettings);

         // This TaggedImageProcessor is used to divert images away from the optional
         // processing and saving of AcqEngJ, and into the system used by the studio API
         // (which has its own system for processing and saving)

         // MMAcquisition
         for (int i = 0; i < sequenceSettings.size(); i++) {
            JSONObject summaryMetadataJSON = currentMultiMDA_.getSummaryMetadata();
            addMMSummaryMetadata(summaryMetadataJSON, sequenceSettings.get(i),
                  positionLists.get(i), studio_);
            SummaryMetadata summaryMetadata =  DefaultSummaryMetadata.fromPropertyMap(
                     NonPropertyMapJSONFormats.summaryMetadata().fromJSON(
                              summaryMetadataJSON.toString()));
            MMAcquisition acq = new MMAcquisition(studio_, summaryMetadata, this,
                  sequenceSettings.get(i));
            Datastore store = acq.getDatastore();
            stores_.add(store);
            Pipeline pipeline = acq.getPipeline();
            pipelines_.add(pipeline);
            sink.add(store, pipeline);
         }

         zStage_ = core_.getFocusDevice();
         zStart_ = core_.getPosition(zStage_);
         autofocusMethod_ = studio_.getAutofocusManager().getAutofocusMethod();
         autofocusOn_ = false;
         if (autofocusMethod_ != null) {
            autofocusOn_ = autofocusMethod_.isContinuousFocusEnabled();
         }
         studio_.events().registerForEvents(this);

         for (int i = 0; i < sequenceSettings.size(); i++) {
            studio_.events().post(new DefaultAcquisitionStartedEvent(stores_.get(i), this,
                  sequenceSettings.get(i)));
         }

         // These hooks implement Autofocus
         if (basicSettings.useAutofocus()) {
            currentMultiMDA_.addHook(autofocusHookBefore(basicSettings.skipAutofocusCount()),
                  currentMultiMDA_.BEFORE_HARDWARE_HOOK);
         }

         for (int i = 0; i < sequenceSettings.size(); i++) {
            // Hook to move back the ZStage to its original position after a Z stack
            if (sequenceSettings.get(i).useSlices()) {
               currentMultiMDA_.addHook(zPositionHook(sequenceSettings.get(i),
                           Acquisition.BEFORE_HARDWARE_HOOK, i),
                     AcquisitionAPI.BEFORE_HARDWARE_HOOK);
               currentMultiMDA_.addHook(zPositionHook(sequenceSettings.get(i),
                           Acquisition.AFTER_EXPOSURE_HOOK, i),
                     AcquisitionAPI.AFTER_EXPOSURE_HOOK);
            }
         }

         // Read for events
         currentMultiMDA_.start();

         // Start the events and signal to finish when complete
         int nrFrames = 1;
         if (timeLapseSettings_.useFrames()) {
            nrFrames = timeLapseSettings_.numFrames();
         }
         for (int t = 0; t < nrFrames; t++) {
            for (int i = 0; i < sequenceSettings.size(); i++) {
               currentMultiMDA_.submitEventIterator(createAcqEventIterator(
                     sequenceSettings.get(i),
                     positionLists.get(i),
                     i,
                     t,
                     (long) (t * timeLapseSettings_.intervalMs())));
            }
         }
         currentMultiMDA_.finish();

         return stores_;

      } catch (Throwable ex) {
         ReportingUtils.showError(ex);
         if (currentMultiMDA_ != null && currentMultiMDA_.areEventsFinished()) {
            for (int i = 0; i < sequenceSettings.size(); i++) {
               studio_.events().post(new DefaultAcquisitionEndedEvent(stores_.get(i), this));
            }
         }
         return null;
      }
   }

   /**
    * Attach Runnables as acquisition hooks.
    *
    * @param acquisitionSettingList List with object with settings for the acquisition
    */
   private void loadRunnables(List<SequenceSettings> acquisitionSettingList) {
      for (RunnablePlusIndices r : runnables_) {
         currentMultiMDA_.addHook(new AcquisitionHook() {
            @Override
            public AcquisitionEvent run(AcquisitionEvent event) {
               int acqIndex = 0;
               if (event.getTags().containsKey(ACQ_IDENTIFIER)) {
                  acqIndex = Integer.parseInt(event.getTags().get(ACQ_IDENTIFIER));
               }
               if (acqIndex >= acquisitionSettingList.size()) {
                  studio_.logs().logError("Event's Acquisition index is higher than "
                        + "Acquisitions we know about.");
                  return event;
               }
               SequenceSettings acquisitionSettings = acquisitionSettingList.get(acqIndex);
               boolean zMatch = event.getZIndex() == null || event.getZIndex() == r.slice_;
               boolean tMatch = event.getTIndex() == null || event.getTIndex() == r.frame_;
               boolean cMatch = event.getConfigPreset() == null
                     || acquisitionSettings.channels().get(r.channel_).config()
                           .equals(event.getConfigPreset());
               boolean pMatch = event.getAxisPosition(
                     MDAAcqEventModules.POSITION_AXIS) == null
                     || ((Integer) event.getAxisPosition(MDAAcqEventModules.POSITION_AXIS))
                       == r.position_;
               if (pMatch && zMatch && tMatch && cMatch) {
                  r.runnable_.run();
               }
               return event;
            }

            @Override
            public void close() {
               // Runnable interface doesn't provide anything for close...
            }
         }, Acquisition.AFTER_HARDWARE_HOOK);
         // TODO: does current API expect this to be before or after hardware? after camera?
         //  during event generation?
      }
   }

   /**
    * This function converts acquisitionSettings to a lazy sequence (i.e. an iterator) of
    * AcquisitionEvents.
    */
   private Iterator<AcquisitionEvent> createAcqEventIterator(
         SequenceSettings acquisitionSettings, PositionList positionList, int acqIndex,
         int timeIndex, long minimumStartTime)
         throws Exception {
      Function<AcquisitionEvent, Iterator<AcquisitionEvent>> channels = null;
      Function<AcquisitionEvent, Iterator<AcquisitionEvent>> zStack = null;
      Function<AcquisitionEvent, Iterator<AcquisitionEvent>> positions = null;

      // Select channels that we are actually using
      List<ChannelSpec> chSpecs = new ArrayList<>();
      for (ChannelSpec chSpec : acquisitionSettings.channels()) {
         if (chSpec.useChannel()) {
            chSpecs.add(chSpec);
         }
      }

      HashMap<String, String> tag = new HashMap<>(1);
      tag.put(ACQ_IDENTIFIER, String.valueOf(acqIndex));

      ArrayList<Function<AcquisitionEvent, Iterator<AcquisitionEvent>>> acqFunctions =
            new ArrayList<>();

      if (acquisitionSettings.useSlices()) {
         double origin = acquisitionSettings.slices().get(0);
         if (acquisitionSettings.relativeZSlice()) {
            origin = studio_.core().getPosition() + acquisitionSettings.slices().get(0);
         }
         zStack = MDAAcqEventModules.zStack(0,
               acquisitionSettings.slices().size() - 1,
               acquisitionSettings.sliceZStepUm(),
               origin,
               chSpecs,
               tag);
      }

      if (acquisitionSettings.useChannels()) {
         Integer middleSliceIndex = (acquisitionSettings.slices().size() - 1) / 2;
         channels = MDAAcqEventModules.channels(chSpecs, middleSliceIndex, tag);
         //TODO: keep shutter open
         //TODO: skip frames
         //TODO: z stack off for channel
      }

      if (acquisitionSettings.usePositionList()) {
         positions = MDAAcqEventModules.positions(positionList, tag);
      }

      if (acquisitionSettings.acqOrderMode() == AcqOrderMode.TIME_POS_CHANNEL_SLICE) {
         if (acquisitionSettings.usePositionList()) {
            acqFunctions.add(positions);
         }
         if (acquisitionSettings.useChannels()) {
            acqFunctions.add(channels);
         }
         if (acquisitionSettings.useSlices()) {
            acqFunctions.add(zStack);
         }
      } else if (acquisitionSettings.acqOrderMode() == AcqOrderMode
            .TIME_POS_SLICE_CHANNEL) {
         if (acquisitionSettings.usePositionList()) {
            acqFunctions.add(positions);
         }
         if (acquisitionSettings.useSlices()) {
            acqFunctions.add(zStack);
         }
         if (acquisitionSettings.useChannels()) {
            acqFunctions.add(channels);
         }
      } else {
         throw new RuntimeException("Unknown acquisition order");
      }

      AcquisitionEvent baseEvent = new AcquisitionEvent(currentMultiMDA_);
      // set time index and minimum startTime in baseEvent
      baseEvent.setTimeIndex(timeIndex);
      baseEvent.setMinimumStartTime(minimumStartTime);

      return new AcquisitionEventIterator(baseEvent, acqFunctions,
            acqEventMonitor(acquisitionSettings));
   }


   private SequenceSettings calculateSlices(SequenceSettings sequenceSettings) {
      // Slices
      if (sequenceSettings.useSlices()) {
         double start = sequenceSettings.sliceZBottomUm();
         double stop = sequenceSettings.sliceZTopUm();
         double step = Math.abs(sequenceSettings.sliceZStepUm());
         if (step == 0.0) {
            throw new UnsupportedOperationException("zero Z step size");
         }
         int count = getNumSlices(sequenceSettings);
         if (start > stop) {
            step = -step;
         }
         ArrayList<Double> slices = new ArrayList<>();
         for (int i = 0; i < count; i++) {
            slices.add(start + i * step);
         }
         return sequenceSettings.copyBuilder().slices(slices).build();
      }
      return sequenceSettings;
   }

   private boolean enoughDiskSpace(File root) {
      // Need to find a file that exists to check space
      while (!root.exists()) {
         root = root.getParentFile();
         if (root == null) {
            return false;
         }
      }
      long usableMB = root.getUsableSpace();
      return (1.25 * getTotalMemory()) < usableMB;
   }

   private String getSource(ChannelSpec channel) {
      try {
         Configuration state = core_.getConfigState(core_.getChannelGroup(), channel.config());
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
    * Will notify registered AcqSettingsListeners that the settings have changed.
    */
   private void settingsChanged() {
      for (AcqSettingsListener listener : settingsListeners_) {
         listener.settingsChanged();
      }
   }


   private int getNumChannels(SequenceSettings sequenceSettings) {
      if (!sequenceSettings.useChannels()) {
         return 1;
      }
      if (sequenceSettings.channels() == null || sequenceSettings.channels().size() == 0) {
         return 1;
      }
      int numChannels = 0;
      for (ChannelSpec channel : sequenceSettings.channels()) {
         if (channel != null && channel.useChannel()) {
            ++numChannels;
         }
      }
      return numChannels;
   }

   /**
    * Returns the number of frames (time points) in this acquisition.
    *
    * @return Number of Frames (time points) in this acquisition.
    */
   private int getNumFrames(SequenceSettings sequenceSettings) {
      int numFrames = sequenceSettings.numFrames();
      if (!sequenceSettings.useFrames()) {
         numFrames = 1;
      }
      return numFrames;
   }

   private int getNumPositions(SequenceSettings sequenceSettings) {
      if (posList_ == null) {
         return 1;
      }
      int numPositions = Math.max(1, posList_.getNumberOfPositions());
      if (!sequenceSettings.usePositionList()) {
         numPositions = 1;
      }
      return numPositions;
   }

   public static int getNumSlices(SequenceSettings sequenceSettings) {
      if (!sequenceSettings.useSlices()) {
         return 1;
      }
      if (sequenceSettings.sliceZStepUm() == 0) {
         // This TODOitem inherited from corresponding fuction in clojure engine
         // TODO How should zero z step be handled?
         return Integer.MAX_VALUE;
      }
      return 1
            + (int) Math.abs((sequenceSettings.sliceZTopUm()
            - sequenceSettings.sliceZBottomUm())
            / sequenceSettings.sliceZStepUm());
   }


   private boolean isFocusStageAvailable() {
      return zStage_ != null && zStage_.length() > 0;
   }

   /**
    * The name of this function is a bit misleading
    * Every channel group name provided as an argument will return
    * true, unless the group exists and only contains a single property with
    * propertylimits (i.e. a slider in the UI)
    *
    * @param group channel group name to be tested
    * @return false if the group exists and only has a single property that has
    *     propertylimits, true otherwise
    */
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


   private int getTotalImages(SequenceSettings sequenceSettings) {
      if (!sequenceSettings.useChannels() || sequenceSettings.channels().size() == 0) {
         return getNumFrames(sequenceSettings) * getNumSlices(sequenceSettings)
               * getNumChannels(sequenceSettings) * getNumPositions(sequenceSettings);
      }

      int nrImages = 0;
      for (ChannelSpec channel : sequenceSettings.channels()) {
         if (channel.useChannel()) {
            for (int t = 0; t < getNumFrames(sequenceSettings); t++) {
               boolean doTimePoint = true;
               if (channel.skipFactorFrame() > 0) {
                  if (t % (channel.skipFactorFrame() + 1) != 0) {
                     doTimePoint = false;
                  }
               }
               if (doTimePoint) {
                  if (channel.doZStack()) {
                     nrImages += getNumSlices(sequenceSettings);
                  } else {
                     nrImages++;
                  }
               }
            }
         }
      }
      return nrImages * getNumPositions(sequenceSettings);
   }

   /**
    * Returns estimate of memory that will be used by this acquisition.
    *
    * @return Estimate of memory used by the acquii
    */
   public long getTotalMemory(SequenceSettings sequenceSettings) {
      CMMCore core = studio_.core();
      return core.getImageWidth()
            * core.getImageHeight()
            * core.getBytesPerPixel()
            * ((long) getTotalImages(sequenceSettings));
   }

   /*
    * Attach a runnable to the acquisition engine. Each index (f, p, c, s) can
    * be specified. Passing a value of -1 results in the runnable being attached
    * at all values of that index. For example, if the first argument is -1,
    * then the runnable will execute at every frame.
    */
   public void attachRunnable(int frame, int position, int channel, int slice, Runnable runnable) {
      runnables_.add(new RunnablePlusIndices(runnable, channel, slice, frame, position));
   }
   /*
    * Clear all attached runnables from the acquisition engine.
    */

   public void clearRunnables() {
      runnables_.clear();
   }

   //////////////////// Actions ///////////////////////////////////////////
   @Override
   public void stop(boolean interrupted) {
      try {
         if (currentMultiMDA_ != null) {
            currentMultiMDA_.abort();
         }
      } catch (Exception ex) {
         ReportingUtils.showError(ex, "Acquisition engine stop request failed");
      }
   }

   @Override
   public boolean abortRequest() {
      if (isAcquisitionRunning()) {
         boolean abortCancelled = false;
         String[] options = {"Abort", "Cancel"};
         for (Datastore store : stores_) {
            List<DisplayWindow> displays = studio_.displays().getDisplays((DataProvider) store);
            Component parentComponent = null;
            if (displays != null && !displays.isEmpty()) {
               parentComponent = displays.get(0).getWindow();
            }
            int result = JOptionPane.showOptionDialog(parentComponent,
                  "Abort current acquisition task?",
                  "Micro-Manager",
                  JOptionPane.DEFAULT_OPTION,
                  JOptionPane.QUESTION_MESSAGE, null,
                  options, options[1]);
            if (result != 0) {
               abortCancelled = true;
            }
         }
         if (!abortCancelled) {
            stop(true);
            return true;
         } else {
            return false;
         }
      }
      return true;
   }

   public boolean abortRequested() {
      if (currentMultiMDA_ != null) {
         return currentMultiMDA_.isAbortRequested();
      }
      return false;
   }

   public void shutdown() {
      stop(true);
   }

   public void setPause(boolean state) {
      if (currentMultiMDA_ != null) {
         currentMultiMDA_.setPaused(state);
      }
   }

   //// State Queries /////////////////////////////////////////////////////
   public boolean isAcquisitionRunning() {
      // Even after the acquisition finishes, if the pipeline is still "live",
      // we should consider the acquisition to be running.
      if (currentMultiMDA_ != null) {
         boolean pipeLinesFinished = true;
         if (pipelines_ != null) {
            for (Pipeline pipeline : pipelines_) {
               if (!pipeline.isHalted()) {
                  pipeLinesFinished = false;
               }
            }
         }
         return (!currentMultiMDA_.areEventsFinished() || !pipeLinesFinished);
      } else {
         return false;
      }
   }

   public boolean isFinished() {
      if (currentMultiMDA_ != null) {
         return currentMultiMDA_.areEventsFinished();
      } else {
         return false;
      }
   }

   public boolean isMultiFieldRunning() {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public long getNextWakeTime() {
      // TODO What to do if next wake time undefined?
      return nextWakeTime_;
   }


   //////////////////// Setters and Getters ///////////////////////////////

   public void setPositionList(PositionList posList) {
      posList_ = posList;
   }

   public void setParentGUI(Studio parent) {
      studio_ = parent;
      core_ = studio_.core();
      studio_.events().registerForEvents(this);
   }

   /**
    * This is ignored by the Clojure engine, and also does not have any function
    * in this engine.  Deprecate?  Do not rely on this to do anything.
    *
    * @param stageLabel Name of the focus drive to use.  Ignored.
    */
   public void setZStageDevice(String stageLabel) {
      zStage_ = stageLabel;
   }

   public void setUpdateLiveWindow(boolean b) {
      // do nothing
   }

   public void setFinished() {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   @Override
   public double getFrameIntervalMs() {
      if (timeLapseSettings_ != null) {
         return timeLapseSettings_.intervalMs();
      }
      return 0.0; // TODO: see where this is actually used...
   }

   public boolean isZSliceSettingEnabled(SequenceSettings sequenceSettings) {
      return sequenceSettings.useSlices();
   }

   /**
    * Get first available config group.
    */
   public String getFirstConfigGroup() {
      if (core_ == null) {
         return "";
      }

      String[] groups = getAvailableGroups();

      if (groups.length < 1) {
         return "";
      }

      return getAvailableGroups()[0];
   }

   /**
    * Find out which channels are currently available for the selected channel group.
    *
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

   /**
    * Sets the channel group in the core.
    * Replies on callbacks to update the UI as well as sequenceSettings
    * (SequenceSettings are updated in the callback function in AcqControlDlg)
    *
    * @param group name of group to set as the new Channel Group
    * @return true when successful, false if no change is needed or when the change fails
    */
   public boolean setChannelGroup(String group) {
      String curGroup = core_.getChannelGroup();
      if (!(group != null
            && (curGroup == null || !curGroup.contentEquals(group)))) {
         // Don't make redundant changes.
         return false;
      }
      if (groupIsEligibleChannel(group)) {
         try {
            core_.setChannelGroup(group);
         } catch (Exception e) {
            try {
               core_.setChannelGroup("");
            } catch (Exception ex) {
               ReportingUtils.logError(ex);
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
    *
    * @deprecated unclear what this should be doing
    */
   @Deprecated
   public void clear() {
      // unclear what the purpose is  Delete?
   }


   public String getVerboseSummary(SequenceSettings sequenceSettings) {
      final int numFrames = getNumFrames(sequenceSettings);
      final int numSlices = getNumSlices(sequenceSettings);
      final int numPositions = getNumPositions(sequenceSettings);
      final int numChannels = getNumChannels(sequenceSettings);

      double exposurePerTimePointMs = 0.0;
      if (sequenceSettings.useChannels()) {
         for (ChannelSpec channel : sequenceSettings.channels()) {
            if (channel.useChannel()) {
               double channelExposure = channel.exposure();
               if (channel.doZStack()) {
                  channelExposure *= getNumSlices(sequenceSettings);
               }
               channelExposure *= getNumPositions(sequenceSettings);
               exposurePerTimePointMs += channelExposure;
            }
         }
      } else { // use the current settings for acquisition
         try {
            exposurePerTimePointMs = core_.getExposure() * getNumSlices(sequenceSettings)
                  * getNumPositions(sequenceSettings);
         } catch (Exception ex) {
            studio_.logs().logError(ex, "Failed to get exposure time");
         }
      }

      final int totalImages = getTotalImages(sequenceSettings);
      final long totalMB = getTotalMemory() / (1024 * 1024);

      double totalDurationSec = 0;
      double interval = Math.max(sequenceSettings.intervalMs(), exposurePerTimePointMs);
      if (!sequenceSettings.useCustomIntervals()) {
         totalDurationSec = interval * (numFrames - 1) / 1000.0;
      } else {
         for (Double d : sequenceSettings.customIntervalsMs()) {
            totalDurationSec += d / 1000.0;
         }
      }
      totalDurationSec += exposurePerTimePointMs / 1000;
      int hrs = (int) (totalDurationSec / 3600);
      double remainSec = totalDurationSec - hrs * 3600;
      int mins = (int) (remainSec / 60);
      remainSec = remainSec - mins * 60;

      String durationString = "\nMinimum duration: ";
      if (hrs > 0) {
         durationString += hrs + "h ";
      }
      if (mins > 0 || hrs > 0) {
         durationString += mins + "m ";
      }
      durationString += NumberUtils.doubleToDisplayString(remainSec) + "s";

      String txt =
            "Number of time points: " + (!sequenceSettings.useCustomIntervals()
                  ? numFrames : sequenceSettings.customIntervalsMs().size())
                  + "\nNumber of positions: " + numPositions
                  + "\nNumber of slices: " + numSlices
                  + "\nNumber of channels: " + numChannels
                  + "\nTotal images: " + totalImages
                  + "\nTotal memory: " + (totalMB <= 1024 ? totalMB + " MB" :
                  NumberUtils.doubleToDisplayString(totalMB / 1024.0) + " GB")
                  + durationString;

      if (sequenceSettings.useFrames()
            || sequenceSettings.usePositionList()
            || sequenceSettings.useChannels()
            || sequenceSettings.useSlices()) {
         StringBuilder order = new StringBuilder("\nOrder: ");
         if (sequenceSettings.useFrames() && sequenceSettings.usePositionList()) {
            if (sequenceSettings.acqOrderMode() == AcqOrderMode.TIME_POS_CHANNEL_SLICE
                  || sequenceSettings.acqOrderMode() == AcqOrderMode.TIME_POS_SLICE_CHANNEL) {
               order.append("Time, Position");
            } else {
               order.append("Position, Time");
            }
         } else if (sequenceSettings.useFrames()) {
            order.append("Time");
         } else if (sequenceSettings.usePositionList()) {
            order.append("Position");
         }

         if ((sequenceSettings.useFrames() || sequenceSettings.usePositionList())
               && (sequenceSettings.useChannels() || sequenceSettings.useSlices())) {
            order.append(", ");
         }

         if (sequenceSettings.useChannels() && sequenceSettings.useSlices()) {
            if (sequenceSettings.acqOrderMode() == AcqOrderMode.TIME_POS_CHANNEL_SLICE
                  || sequenceSettings.acqOrderMode() == AcqOrderMode.POS_TIME_CHANNEL_SLICE) {
               order.append("Channel, Slice");
            } else {
               order.append("Slice, Channel");
            }
         } else if (sequenceSettings.useChannels()) {
            order.append("Channel");
         } else if (sequenceSettings.useSlices()) {
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
    *
    * @param config Configuration to be tested
    * @return True if the parameter is in the current group
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

   public String[] getAvailableGroups() {
      StrVector groups;
      try {
         groups = core_.getAllowedPropertyValues("Core", "ChannelGroup");
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
         return new String[0];
      }
      ArrayList<String> strGroups = new ArrayList<>();
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
            // core_.waitForDevice(zstage_);
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
      if (currentMultiMDA_ != null) {
         return currentMultiMDA_.isPaused();
      }
      return false;
   }

   /*
    * Returns the summary metadata associated with the most recent acquisition.
    */
   public JSONObject getSummaryMetadata() {
      return currentMultiMDA_.getSummaryMetadata();
   }

   public String getComment(SequenceSettings sequenceSettings) {
      return sequenceSettings.comment();
   }


   ////////////////////////////////////////////
   ////////// Event handlers
   /////////////////////////////////////////////

   /**
    * Event handler for the AcquisitionEndedEvent.
    *
    * @param event Event signalling that the acquisition ended.
    */
   @Subscribe
   public void onAcquisitionEnded(AcquisitionEndedEvent event) {
      if (event.getStore().equals(stores_)) {
         // Restore original Z position and autofocus if applicable.
         if (isFocusStageAvailable()) {
            try {
               core_.setPosition(zStage_, zStart_);
               if (autofocusMethod_ != null) {
                  autofocusMethod_.enableContinuousFocus(autofocusOn_);
               }
            } catch (Exception e) {
               studio_.logs().logError(e);
            }
         }
         stores_ = null;
         pipelines_ = null;
         currentMultiMDA_ = null;
         studio_.events().unregisterForEvents(this);
      }
   }

   @Subscribe
   public void onNewPositionListEvent(NewPositionListEvent newPositionListEvent) {
      posList_ = newPositionListEvent.getPositionList();
   }

   /**
    * Event handler for the event signalling that the application is shutting down.
    *
    * @param event Event signalling that the application started to shut down.
    */
   @Subscribe
   public void onShutdownCommencing(InternalShutdownCommencingEvent event) {
      if (!event.isCanceled() && isAcquisitionRunning()) {
         int result = JOptionPane.showConfirmDialog(null,
               "Acquisition in progress. Are you sure you want to exit and discard all data?",
               "Micro-Manager", JOptionPane.YES_NO_OPTION,
               JOptionPane.INFORMATION_MESSAGE);

         if (result == JOptionPane.YES_OPTION) {
            if (currentMultiMDA_ != null) {
               currentMultiMDA_.abort();
            }
         } else {
            event.cancelShutdown();
         }
      }
   }
}