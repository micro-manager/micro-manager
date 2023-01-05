///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Henry Pinkard
//
// COPYRIGHT:    Photomics Inc, 2022
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


package org.micromanager.acquisition.internal.acqengjcompat;

import com.google.common.eventbus.Subscribe;
import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import javax.swing.JOptionPane;
import mmcorej.CMMCore;
import mmcorej.Configuration;
import mmcorej.PropertySetting;
import mmcorej.StrVector;
import mmcorej.TaggedImage;
import mmcorej.org.json.JSONArray;
import mmcorej.org.json.JSONException;
import mmcorej.org.json.JSONObject;
import org.micromanager.AutofocusPlugin;
import org.micromanager.PositionList;
import org.micromanager.Studio;
import org.micromanager.acqj.api.AcquisitionHook;
import org.micromanager.acqj.internal.Engine;
import org.micromanager.acqj.main.AcqEngMetadata;
import org.micromanager.acqj.main.Acquisition;
import org.micromanager.acqj.main.AcquisitionEvent;
import org.micromanager.acqj.util.AcquisitionEventIterator;
import org.micromanager.acquisition.AcquisitionEndedEvent;
import org.micromanager.acquisition.ChannelSpec;
import org.micromanager.acquisition.SequenceSettings;
import org.micromanager.acquisition.internal.AcquisitionEngine;
import org.micromanager.acquisition.internal.DefaultAcquisitionEndedEvent;
import org.micromanager.acquisition.internal.DefaultAcquisitionStartedEvent;
import org.micromanager.acquisition.internal.MMAcquisition;
import org.micromanager.data.Datastore;
import org.micromanager.data.Pipeline;
import org.micromanager.data.internal.DefaultDatastore;
import org.micromanager.data.internal.DefaultSummaryMetadata;
import org.micromanager.data.internal.PropertyKey;
import org.micromanager.events.NewPositionListEvent;
import org.micromanager.events.internal.InternalShutdownCommencingEvent;
import org.micromanager.internal.MMStudio;
import org.micromanager.internal.interfaces.AcqSettingsListener;
import org.micromanager.internal.utils.AcqOrderMode;
import org.micromanager.internal.utils.MMException;
import org.micromanager.internal.utils.NumberUtils;
import org.micromanager.internal.utils.ReportingUtils;

/**
 * This class provides a compatibility layer between AcqEngJ and the
 * AcquisitionEngine interface. It is analagous to the AcquisitionWrapperEngine.java,
 * which does the same thing for the clojure acquisition engine
 *
 * <p>AcquisitionEngine implements a subset of the functionality of AcqEngJ,
 * and this class enforces those specific assumptions. These include:
 * - One and only one acquisition can be run at any given time
 * - The axes of the acquisition are limited to channel, slice, frame, and position
 * - The number of images and other parameters are all known at the start of acquisition
 */
public class AcqEngJAdapter implements AcquisitionEngine {

   private Acquisition currentAcquisition_;

   private CMMCore core_;
   protected Studio studio_;
   private PositionList posList_;
   private String zStage_;
   private SequenceSettings sequenceSettings_;

   protected JSONObject summaryMetadata_;
   private ArrayList<AcqSettingsListener> settingsListeners_;
   private Datastore curStore_;
   private Pipeline curPipeline_;

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

   public AcqEngJAdapter(Studio studio) {
      // Create AcqEngJ
      studio_ = studio;
      core_ = studio_.core();
      new Engine(core_);
      studio_ = MMStudio.getInstance();
      settingsListeners_ = new ArrayList<>();
      sequenceSettings_ = (new SequenceSettings.Builder()).build();
   }

   // this is where the work happens
   private Datastore runAcquisition(SequenceSettings sequenceSettings) {
      SequenceSettings.Builder sb = sequenceSettings.copyBuilder();
      //Make sure computer can write to selected location and there is enough space to do so
      if (sequenceSettings.save()) {
         File root = new File(sequenceSettings.root());
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
         } else if (!this.enoughDiskSpace()) {
            ReportingUtils.showError(
                  "Not enough space on disk to save the requested image set; "
                        + "acquisition canceled.");
            return null;
         }

         DefaultDatastore.setPreferredSaveMode(studio_, sequenceSettings.saveMode());
      }

      // manipulate positionlist
      PositionList posListToUse = posList_;
      if (posList_ == null && sequenceSettings_.usePositionList()) {
         posListToUse = studio_.positions().getPositionList();
      }
      posList_ = posListToUse;

      // The clojure acquisition engine always uses numFrames, and customIntervals
      // unless they are null.
      if (sequenceSettings.useCustomIntervals()) {
         sb.numFrames(sequenceSettings.customIntervalsMs().size());
      } else {
         sb.customIntervalsMs(null);
      }

      // Several "translations" have to be made to accommodate the Clojure engine:
      if (!sequenceSettings.useFrames()) {
         sb.numFrames(0);
      }
      if (!sequenceSettings.useChannels()) {
         sb.channels(null);
      }
      switch (sequenceSettings.acqOrderMode()) {
         case AcqOrderMode.TIME_POS_SLICE_CHANNEL:
            sb.timeFirst(false);
            sb.slicesFirst(false);
            break;
         case AcqOrderMode.TIME_POS_CHANNEL_SLICE:
            sb.timeFirst(false);
            sb.slicesFirst(true);
            break;
         case AcqOrderMode.POS_TIME_SLICE_CHANNEL:
            sb.timeFirst(true);
            sb.slicesFirst(false);
            break;
         case AcqOrderMode.POS_TIME_CHANNEL_SLICE:
            sb.timeFirst(true);
            sb.slicesFirst(true);
            break;
         default:
            break;
      }

      try {
         // Start up the acquisition engine
         SequenceSettings acquisitionSettings = sb.build();
         currentAcquisition_ = new Acquisition(null);
         loadRunnables(acquisitionSettings);
         // This TaggedImageProcessor is used to divert images away from the optional
         // processing and saving of AcqEngJ, and into the system used by the studio API
         // (which has its own system for processing and saving)
         TaggedImageDiverter diverter = new TaggedImageDiverter();
         currentAcquisition_.addImageProcessor(diverter);
         final BlockingQueue<TaggedImage> engineOutputQueue = diverter.getQueue();

         summaryMetadata_ = currentAcquisition_.getSummaryMetadata();
         addMMSummaryMetadata(summaryMetadata_, sequenceSettings);

         // MMAcquisition
         MMAcquisition acq = new MMAcquisition(studio_,
               acquisitionSettings.save() ? acquisitionSettings.root() : null,
               acquisitionSettings.prefix(),
               summaryMetadata_,
               this,
               acquisitionSettings.shouldDisplayImages());
         curStore_ = acq.getDatastore();
         curPipeline_ = acq.getPipeline();

         zStage_ = core_.getFocusDevice();
         zStart_ = core_.getPosition(zStage_);
         autofocusMethod_ = studio_.getAutofocusManager().getAutofocusMethod();
         autofocusOn_ = false;
         if (autofocusMethod_ != null) {
            autofocusOn_ = autofocusMethod_.isContinuousFocusEnabled();
         }
         studio_.events().registerForEvents(this);
         studio_.events().post(new DefaultAcquisitionStartedEvent(curStore_, this,
               acquisitionSettings));

         // Start pumping images through the pipeline and into the datastore.
         AcqEngJDataSink sink = new AcqEngJDataSink(
               engineOutputQueue, curPipeline_, curStore_, this, studio_.events());
         sink.start(() -> currentAcquisition_.abort());

         // Read for events
         currentAcquisition_.start();

         // Start the events and signal to finish when complete
         currentAcquisition_.submitEventIterator(createAcqEventIterator(acquisitionSettings));
         currentAcquisition_.finish();

         return curStore_;

      } catch (Throwable ex) {
         ReportingUtils.showError(ex);
         studio_.events().post(new DefaultAcquisitionEndedEvent(
               curStore_, this));
         return null;
      }
   }

   /**
    * Higher level stuff in MM may depend in many hidden, poorly documented
    * ways on summary metadata generated by the acquisition engine.
    * This function adds in its fields in order to achieve compatibility.
    */
   private void addMMSummaryMetadata(JSONObject summaryMetadata, SequenceSettings acqSettings)
         throws JSONException {
      // These are the ones from the clojure engine that may yet need to be translated
      //        "Channels" -> {Long@25854} 2

      summaryMetadata_.put(PropertyKey.CHANNEL_GROUP.key(), acqSettings.channelGroup());
      JSONArray chNames = new JSONArray();
      JSONArray chColors = new JSONArray();
      if (acqSettings.useChannels() && acqSettings.channels().size() > 0) {
         for (ChannelSpec c : acqSettings.channels()) {
            chNames.put(c.config());
            chColors.put(c.color().getRGB());
         }
      } else {
         chNames.put("Default");
      }
      summaryMetadata_.put(PropertyKey.CHANNEL_NAMES.key(), chNames);
      summaryMetadata_.put(PropertyKey.CHANNEL_COLORS.key(), chColors);

      // MM MDA acquisitions have a defined number of
      // frames/slices/channels/positions at the outset
      summaryMetadata_.put(PropertyKey.FRAMES.key(), getNumFrames());
      summaryMetadata_.put(PropertyKey.SLICES.key(), getNumSlices());
      summaryMetadata_.put(PropertyKey.CHANNELS.key(), getNumChannels());
      summaryMetadata_.put(PropertyKey.POSITIONS.key(), getNumPositions());

      // MM MDA acquisitions have a defined order
      summaryMetadata_.put(PropertyKey.SLICES_FIRST.key(),
            acqSettings.acqOrderMode() == AcqOrderMode.TIME_POS_SLICE_CHANNEL
                  || acqSettings.acqOrderMode() == AcqOrderMode.POS_TIME_CHANNEL_SLICE);
      summaryMetadata_.put(PropertyKey.TIME_FIRST.key(),
            acqSettings.acqOrderMode() == AcqOrderMode.TIME_POS_SLICE_CHANNEL
                  || acqSettings.acqOrderMode() == AcqOrderMode.TIME_POS_CHANNEL_SLICE);

      DefaultSummaryMetadata dsmd = new DefaultSummaryMetadata.Builder().build();
      summaryMetadata.put(PropertyKey.MICRO_MANAGER_VERSION.key(),
            dsmd.getMicroManagerVersion());
   }

   /**
    * Higher level code in MMStudio expects certain metadata tags that
    * are added by the Clojure engine. For compatibility, we must translate
    * AcqEnJ's metadata to include this here
    */
   public static void addMMImageMetadata(JSONObject imageMD) {
      // These might be required...

      //    getUUID();
      //    getCamera();
      //    getBinning();
      //    getROI();
      //    getBitDepth();
      //    getExposureMs();
      //    getElapsedTimeMs(0.0);
      //    getImageNumber();
      //    getReceivedTime();
      //    getPixelSizeUm();
      //    getPixelAspect();
      //    getPositionName("");
      //    getXPositionUm();
      //    getYPositionUm();
      //    getZPositionUm();


      try {
         if (AcqEngMetadata.hasAxis(imageMD, AcqEngMetadata.TIME_AXIS)) {
            imageMD.put(PropertyKey.FRAME_INDEX.key(),
                  AcqEngMetadata.getAxisPosition(imageMD, AcqEngMetadata.TIME_AXIS));
         }
         if (AcqEngMetadata.hasAxis(imageMD, AcqEngMetadata.Z_AXIS)) {
            imageMD.put(PropertyKey.SLICE_INDEX.key(),
                  AcqEngMetadata.getAxisPosition(imageMD, AcqEngMetadata.Z_AXIS));
         }
         if (AcqEngMetadata.hasAxis(imageMD, AcqEngMetadata.CHANNEL_AXIS)) {
            imageMD.put(PropertyKey.CHANNEL_INDEX.key(),
                  AcqEngMetadata.getAxisPosition(imageMD, AcqEngMetadata.CHANNEL_AXIS));

            // TODO change to reading channel name from the axes
            //  imageMD.put(PropertyKey.CHANNEL_NAME.key(), AcqEngMetadata.getChannelName(imageMD));
            // TODO acqEngJ doesnt currently report this in metadata...
            // imageMD.put(PropertyKey.CAMERA_CHANNEL_INDEX.key(), AcqEngMetadata.(imageMD));
            // Maybe core channel group is needed?
         }
         if (AcqEngMetadata.hasAxis(imageMD, "position")) {
            imageMD.put(PropertyKey.POSITION_INDEX.key(),
                  AcqEngMetadata.getAxisPosition(imageMD, "position"));
         }
      } catch (JSONException e) {
         throw new RuntimeException("Couldn't convert metadata");
      }
   }


   /**
    * Attach Runnables as acquisition hooks.
    *
    * @param acquisitionSettings Object with settings for the acquisition
    */
   private void loadRunnables(SequenceSettings acquisitionSettings) {
      for (RunnablePlusIndices r : runnables_) {
         currentAcquisition_.addHook(new AcquisitionHook() {
            @Override
            public AcquisitionEvent run(AcquisitionEvent event) {
               boolean zMatch = event.getZIndex() == null || event.getZIndex() == r.slice_;
               boolean tMatch = event.getTIndex() == null || event.getTIndex() == r.frame_;
               boolean cMatch = event.getConfigPreset() == null
                     || acquisitionSettings.channels().get(r.channel_).config()
                           .equals(event.getConfigPreset());
               boolean pMatch = event.getAxisPosition(MDAAcqEventModules.POSITION_AXIS) == null
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
      ;
   }

   /**
    * This function converts acquisitionSettings to a lazy sequence (i.e. an iterator) of
    * AcquisitionEvents.
    */
   private Iterator<AcquisitionEvent> createAcqEventIterator(SequenceSettings acquisitionSettings)
         throws Exception {
      Function<AcquisitionEvent, Iterator<AcquisitionEvent>> channels = null;
      Function<AcquisitionEvent, Iterator<AcquisitionEvent>> zStack = null;
      Function<AcquisitionEvent, Iterator<AcquisitionEvent>> positions = null;
      Function<AcquisitionEvent, Iterator<AcquisitionEvent>> timelapse = null;

      ArrayList<Function<AcquisitionEvent, Iterator<AcquisitionEvent>>> acqFunctions
            = new ArrayList<>();

      if (acquisitionSettings.useSlices()) {
         double origin = acquisitionSettings.slices().get(0);
         if (acquisitionSettings.relativeZSlice()) {
            origin = studio_.core().getPosition() + acquisitionSettings.slices().get(0);
         }
         zStack = MDAAcqEventModules.zStack(0,
               acquisitionSettings.slices().size() - 1,
               acquisitionSettings.sliceZStepUm(),
               origin);
      }

      if (acquisitionSettings.useChannels()) {
         channels = MDAAcqEventModules.channels(acquisitionSettings.channels());
         //TODO: keep shutter open
         //TODO: skip frames
         //TODO: z stack off for channel
      }

      if (acquisitionSettings.usePositionList()) {
         positions = MDAAcqEventModules.positions(posList_);
         // TODO: is acq engine supposed to move multiple stages?
         // Yes: when moving to a new position, all stages in the MultiStagePosition instance
         // should be moved to the desired location
         // TODO: What about Z positions in position list
         // Yes: First move all stages in the MSP to their desired location, then do
         // whatever is asked to do.
      }

      if (acquisitionSettings.useFrames()) {
         timelapse = MDAAcqEventModules.timelapse(acquisitionSettings.numFrames(),
               acquisitionSettings.intervalMs());
         //TODO custom time intervals
      }

      //TODO autofocus

      if (acquisitionSettings.acqOrderMode() == AcqOrderMode.POS_TIME_CHANNEL_SLICE) {
         if (acquisitionSettings.usePositionList()) {
            acqFunctions.add(positions);
         }
         if (acquisitionSettings.useFrames()) {
            acqFunctions.add(timelapse);
         }
         if (acquisitionSettings.useChannels()) {
            acqFunctions.add(channels);
         }
         if (acquisitionSettings.useSlices()) {
            acqFunctions.add(zStack);
         }
      } else if (acquisitionSettings.acqOrderMode() == AcqOrderMode.POS_TIME_SLICE_CHANNEL) {
         if (acquisitionSettings.usePositionList()) {
            acqFunctions.add(positions);
         }
         if (acquisitionSettings.useFrames()) {
            acqFunctions.add(timelapse);
         }
         if (acquisitionSettings.useSlices()) {
            acqFunctions.add(zStack);
         }
         if (acquisitionSettings.useChannels()) {
            acqFunctions.add(channels);
         }
      } else if (acquisitionSettings.acqOrderMode() == AcqOrderMode.TIME_POS_CHANNEL_SLICE) {
         if (acquisitionSettings.useFrames()) {
            acqFunctions.add(timelapse);
         }
         if (acquisitionSettings.usePositionList()) {
            acqFunctions.add(positions);
         }
         if (acquisitionSettings.useChannels()) {
            acqFunctions.add(channels);
         }
         if (acquisitionSettings.useSlices()) {
            acqFunctions.add(zStack);
         }
      } else if (acquisitionSettings.acqOrderMode() == AcqOrderMode.TIME_POS_SLICE_CHANNEL) {
         if (acquisitionSettings.useFrames()) {
            acqFunctions.add(timelapse);
         }
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

      AcquisitionEvent baseEvent = new AcquisitionEvent(currentAcquisition_);
      return new AcquisitionEventIterator(baseEvent, acqFunctions,
            acqEventMonitor(acquisitionSettings));

   }

   /**
    * This function monitors acquisition events as they are dynamically created.
    *
    * @return
    */
   private Function<AcquisitionEvent, AcquisitionEvent> acqEventMonitor(SequenceSettings settings) {
      return (AcquisitionEvent event) -> {
         if (event.getMinimumStartTimeAbsolute() != null) {
            nextWakeTime_ = event.getMinimumStartTimeAbsolute();
         }
         return event;
      };
   }

   private void calculateSlices() {
      // Slices
      if (sequenceSettings_.useSlices()) {
         double start = sequenceSettings_.sliceZBottomUm();
         double stop = sequenceSettings_.sliceZTopUm();
         double step = Math.abs(sequenceSettings_.sliceZStepUm());
         if (step == 0.0) {
            throw new UnsupportedOperationException("zero Z step size");
         }
         int count = getNumSlices();
         if (start > stop) {
            step = -step;
         }
         ArrayList<Double> slices = new ArrayList<>();
         for (int i = 0; i < count; i++) {
            slices.add(start + i * step);
         }
         sequenceSettings_ = sequenceSettings_.copyBuilder().slices(slices).build();
      }
   }

   private boolean enoughDiskSpace() {
      File root = new File(sequenceSettings_.root());
      //Need to find a file that exists to check space
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


   private int getNumChannels() {
      if (!sequenceSettings_.useChannels()) {
         return 1;
      }
      if (sequenceSettings_.channels() == null || sequenceSettings_.channels().size() == 0) {
         return 1;
      }
      int numChannels = 0;
      for (ChannelSpec channel : sequenceSettings_.channels()) {
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
   private int getNumFrames() {
      int numFrames = sequenceSettings_.numFrames();
      if (!sequenceSettings_.useFrames()) {
         numFrames = 1;
      }
      return numFrames;
   }

   private int getNumPositions() {
      if (posList_ == null) {
         return 1;
      }
      int numPositions = Math.max(1, posList_.getNumberOfPositions());
      if (!sequenceSettings_.usePositionList()) {
         numPositions = 1;
      }
      return numPositions;
   }

   private int getNumSlices() {
      if (!sequenceSettings_.useSlices()) {
         return 1;
      }
      if (sequenceSettings_.sliceZStepUm() == 0) {
         // This TODOitem inherited from corresponding fuction in clojure engine
         // TODO How should zero z step be handled?
         return Integer.MAX_VALUE;
      }
      return 1
            + (int) Math.abs((sequenceSettings_.sliceZTopUm()
            - sequenceSettings_.sliceZBottomUm())
            / sequenceSettings_.sliceZStepUm());
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


   private int getTotalImages() {
      if (!sequenceSettings_.useChannels() || sequenceSettings_.channels().size() == 0) {
         return getNumFrames() * getNumSlices() * getNumChannels() * getNumPositions();
      }

      int nrImages = 0;
      for (ChannelSpec channel : sequenceSettings_.channels()) {
         if (channel.useChannel()) {
            for (int t = 0; t < getNumFrames(); t++) {
               boolean doTimePoint = true;
               if (channel.skipFactorFrame() > 0) {
                  if (t % (channel.skipFactorFrame() + 1) != 0) {
                     doTimePoint = false;
                  }
               }
               if (doTimePoint) {
                  if (channel.doZStack()) {
                     nrImages += getNumSlices();
                  } else {
                     nrImages++;
                  }
               }
            }
         }
      }
      return nrImages * getNumPositions();
   }

   //////////////////////////////////////////
   ///////// AcquisitionEngine API //////////
   /////////////////////////////////////////

   @Override
   public SequenceSettings getSequenceSettings() {
      return sequenceSettings_;
   }

   /**
    * Sets the settings to be used in the next acquisition.
    *
    * @param sequenceSettings Settings for the next acquisition.
    */
   @Override
   public void setSequenceSettings(SequenceSettings sequenceSettings) {
      sequenceSettings_ = sequenceSettings;
      calculateSlices();
      settingsChanged();
   }

   @Override
   public Datastore acquire() throws MMException {
      calculateSlices();
      return runAcquisition(sequenceSettings_);
   }

   @Override
   public Datastore getAcquisitionDatastore() {
      return curStore_;
   }

   @Override
   public void addSettingsListener(AcqSettingsListener listener) {
      settingsListeners_.add(listener);
   }

   @Override
   public void removeSettingsListener(AcqSettingsListener listener) {
      settingsListeners_.remove(listener);
   }

   @Override
   public long getTotalMemory() {
      CMMCore core = studio_.core();
      return core.getImageWidth()
            * core.getImageHeight()
            * core.getBytesPerPixel()
            * ((long) getTotalImages());
   }

   /*
    * Attach a runnable to the acquisition engine. Each index (f, p, c, s) can
    * be specified. Passing a value of -1 results in the runnable being attached
    * at all values of that index. For example, if the first argument is -1,
    * then the runnable will execute at every frame.
    */
   @Override
   public void attachRunnable(int frame, int position, int channel, int slice, Runnable runnable) {
      runnables_.add(new RunnablePlusIndices(runnable, channel, slice, frame, position));
   }
   /*
    * Clear all attached runnables from the acquisition engine.
    */

   @Override
   public void clearRunnables() {
      runnables_.clear();
   }

   //////////////////// Actions ///////////////////////////////////////////
   @Override
   public void stop(boolean interrupted) {
      try {
         if (currentAcquisition_ != null) {
            currentAcquisition_.abort();
         }
      } catch (Exception ex) {
         ReportingUtils.showError(ex, "Acquisition engine stop request failed");
      }
   }

   @Override
   public boolean abortRequest() {
      if (isAcquisitionRunning()) {
         String[] options = {"Abort", "Cancel"};
         int result = JOptionPane.showOptionDialog(null,
               "Abort current acquisition task?",
               "Micro-Manager",
               JOptionPane.DEFAULT_OPTION,
               JOptionPane.QUESTION_MESSAGE, null,
               options, options[1]);
         if (result == 0) {
            stop(true);
            return true;
         } else {
            return false;
         }
      }
      return true;
   }

   @Override
   public boolean abortRequested() {
      if (currentAcquisition_ != null) {
         return currentAcquisition_.isAbortRequested();
      }
      return false;
   }

   @Override
   public void shutdown() {
      stop(true);
   }

   @Override
   public void setPause(boolean state) {
      if (currentAcquisition_ != null) {
         currentAcquisition_.setPaused(state);
      }
   }

   //// State Queries /////////////////////////////////////////////////////
   @Override
   public boolean isAcquisitionRunning() {
      // Even after the acquisition finishes, if the pipeline is still "live",
      // we should consider the acquisition to be running.
      if (currentAcquisition_ != null) {
         return (!currentAcquisition_.areEventsFinished()
               || (curPipeline_ != null && !curPipeline_.isHalted()));
      } else {
         return false;
      }
   }

   @Override
   public boolean isFinished() {
      if (currentAcquisition_ != null) {
         return currentAcquisition_.areEventsFinished();
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
      // TODO What to do if next wake time undefined?
      return nextWakeTime_;
   }


   //////////////////// Setters and Getters ///////////////////////////////

   @Override
   public void setPositionList(PositionList posList) {
      posList_ = posList;
   }

   @Override
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
   @Override
   public void setZStageDevice(String stageLabel) {
      zStage_ = stageLabel;
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
   public double getFrameIntervalMs() {
      return sequenceSettings_.intervalMs();
   }

   @Override
   public boolean isZSliceSettingEnabled() {
      return sequenceSettings_.useSlices();
   }

   @Override
   public void setChannel(int row, ChannelSpec sp) {
      ArrayList<ChannelSpec> channels = sequenceSettings_.channels();
      channels.add(row, sp);
      sequenceSettings_ = (new SequenceSettings.Builder(sequenceSettings_))
            .channels(channels).build();
   }

   @Override
   public void setChannels(ArrayList<ChannelSpec> channels) {
      sequenceSettings_ = (new SequenceSettings.Builder(sequenceSettings_))
            .channels(channels).build();
   }

   /**
    * Get first available config group.
    */
   @Override
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

   /**
    * Sets the channel group in the core.
    * Replies on callbacks to update the UI as well as sequenceSettings
    * (SequenceSettings are updated in the callback function in AcqControlDlg)
    *
    * @param group name of group to set as the new Channel Group
    * @return true when successful, false if no change is needed or when the change fails
    */
   @Override
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
   @Override
   @Deprecated
   public void clear() {
      // unclear what the purpose is  Delete?
   }


   @Override
   public void setShouldDisplayImages(boolean shouldDisplay) {
      sequenceSettings_ =
            sequenceSettings_.copyBuilder().shouldDisplayImages(shouldDisplay).build();
   }

   @Override
   public String getVerboseSummary() {
      final int numFrames = getNumFrames();
      final int numSlices = getNumSlices();
      final int numPositions = getNumPositions();
      final int numChannels = getNumChannels();

      double exposurePerTimePointMs = 0.0;
      if (sequenceSettings_.useChannels()) {
         for (ChannelSpec channel : sequenceSettings_.channels()) {
            if (channel.useChannel()) {
               double channelExposure = channel.exposure();
               if (channel.doZStack()) {
                  channelExposure *= getNumSlices();
               }
               channelExposure *= getNumPositions();
               exposurePerTimePointMs += channelExposure;
            }
         }
      } else { // use the current settings for acquisition
         try {
            exposurePerTimePointMs = core_.getExposure() * getNumSlices() * getNumPositions();
         } catch (Exception ex) {
            studio_.logs().logError(ex, "Failed to get exposure time");
         }
      }

      final int totalImages = getTotalImages();
      final long totalMB = getTotalMemory() / (1024 * 1024);

      double totalDurationSec = 0;
      double interval = Math.max(sequenceSettings_.intervalMs(), exposurePerTimePointMs);
      if (!sequenceSettings_.useCustomIntervals()) {
         totalDurationSec = interval * (numFrames - 1) / 1000.0;
      } else {
         for (Double d : sequenceSettings_.customIntervalsMs()) {
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
            "Number of time points: " + (!sequenceSettings_.useCustomIntervals()
                  ? numFrames : sequenceSettings_.customIntervalsMs().size())
                  + "\nNumber of positions: " + numPositions
                  + "\nNumber of slices: " + numSlices
                  + "\nNumber of channels: " + numChannels
                  + "\nTotal images: " + totalImages
                  + "\nTotal memory: " + (totalMB <= 1024 ? totalMB + " MB" :
                  NumberUtils.doubleToDisplayString(totalMB / 1024.0) + " GB")
                  + durationString;

      if (sequenceSettings_.useFrames()
            || sequenceSettings_.usePositionList()
            || sequenceSettings_.useChannels()
            || sequenceSettings_.useSlices()) {
         StringBuilder order = new StringBuilder("\nOrder: ");
         if (sequenceSettings_.useFrames() && sequenceSettings_.usePositionList()) {
            if (sequenceSettings_.acqOrderMode() == AcqOrderMode.TIME_POS_CHANNEL_SLICE
                  || sequenceSettings_.acqOrderMode() == AcqOrderMode.TIME_POS_SLICE_CHANNEL) {
               order.append("Time, Position");
            } else {
               order.append("Position, Time");
            }
         } else if (sequenceSettings_.useFrames()) {
            order.append("Time");
         } else if (sequenceSettings_.usePositionList()) {
            order.append("Position");
         }

         if ((sequenceSettings_.useFrames() || sequenceSettings_.usePositionList())
               && (sequenceSettings_.useChannels() || sequenceSettings_.useSlices())) {
            order.append(", ");
         }

         if (sequenceSettings_.useChannels() && sequenceSettings_.useSlices()) {
            if (sequenceSettings_.acqOrderMode() == AcqOrderMode.TIME_POS_CHANNEL_SLICE
                  || sequenceSettings_.acqOrderMode() == AcqOrderMode.POS_TIME_CHANNEL_SLICE) {
               order.append("Channel, Slice");
            } else {
               order.append("Slice, Channel");
            }
         } else if (sequenceSettings_.useChannels()) {
            order.append("Channel");
         } else if (sequenceSettings_.useSlices()) {
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

   @Override
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

   @Override
   public boolean isPaused() {
      if (currentAcquisition_ != null) {
         return currentAcquisition_.isPaused();
      }
      return false;
   }

   /*
    * Returns the summary metadata associated with the most recent acquisition.
    */
   @Override
   public JSONObject getSummaryMetadata() {
      return summaryMetadata_;
   }

   @Override
   public String getComment() {
      return sequenceSettings_.comment();
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
      if (event.getStore().equals(curStore_)) {
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
         curStore_ = null;
         curPipeline_ = null;
         currentAcquisition_ = null;
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
            if (currentAcquisition_ != null) {
               currentAcquisition_.abort();
            }
         } else {
            event.cancelShutdown();
         }
      }
   }
}