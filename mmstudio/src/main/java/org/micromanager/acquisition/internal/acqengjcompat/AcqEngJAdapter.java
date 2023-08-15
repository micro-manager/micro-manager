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


package org.micromanager.acquisition.internal.acqengjcompat;

import com.google.common.eventbus.Subscribe;
import java.awt.Component;
import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import javax.swing.JOptionPane;
import mmcorej.CMMCore;
import mmcorej.Configuration;
import mmcorej.PropertySetting;
import mmcorej.StrVector;
import mmcorej.org.json.JSONArray;
import mmcorej.org.json.JSONException;
import mmcorej.org.json.JSONObject;
import org.micromanager.PositionList;
import org.micromanager.Studio;
import org.micromanager.UserProfile;
import org.micromanager.acqj.api.AcquisitionAPI;
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
import org.micromanager.acquisition.internal.MMAcquistionControlCallbacks;
import org.micromanager.data.DataProvider;
import org.micromanager.data.Datastore;
import org.micromanager.data.Pipeline;
import org.micromanager.data.internal.DefaultDatastore;
import org.micromanager.data.internal.DefaultSummaryMetadata;
import org.micromanager.data.internal.PropertyKey;
import org.micromanager.display.DisplayWindow;
import org.micromanager.events.NewPositionListEvent;
import org.micromanager.events.internal.InternalShutdownCommencingEvent;
import org.micromanager.internal.interfaces.AcqSettingsListener;
import org.micromanager.internal.utils.AcqOrderMode;
import org.micromanager.internal.utils.MMException;
import org.micromanager.internal.utils.NumberUtils;


/**
 * This class provides a compatibility layer between AcqEngJ and the
 * AcquisitionEngine interface. It is analagous to AcquisitionWrapperEngine.java,
 * which does the same thing for the clojure acquisition engine
 *
 * <p>AcquisitionEngine implements a subset of the functionality of AcqEngJ,
 * and this class enforces those specific assumptions. These include:
 * - One and only one acquisition can be run at any given time
 * - The axes of the acquisition are limited to channel, slice, frame, and position
 * - The number of images and other parameters are all known at the start of acquisition
 */
public class AcqEngJAdapter implements AcquisitionEngine, MMAcquistionControlCallbacks {

   public static final String ACQ_IDENTIFIER = "Acq_Identifier";
   private static final SimpleDateFormat DATE_FORMATTER =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z");
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
    * Constructor, take studio, create the Engine.
    *
    * @param studio Always there!
    */
   public AcqEngJAdapter(Studio studio) {
      // Create AcqEngJ
      studio_ = studio;
      core_ = studio_.core();
      new Engine(core_);
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
                  studio_.logs().showError(
                        "Unable to save data to selected location: check that "
                              + "location exists.\nAcquisition canceled.");
                  return null;
               }
            } else {
               studio_.logs().showMessage("Acquisition canceled.");
               return null;
            }
         } else if (!this.enoughDiskSpace()) {
            studio_.logs().showError(
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

      // Several "translations" for the Clojure engine may now be superfluous:
      if (!sequenceSettings.useFrames()) {
         sb.numFrames(0);
      }
      if (!sequenceSettings.useChannels()) {
         sb.channels(null);
      }

      // It is unclear if this code is still needed, it may be needed to add tags to OME TIFF
      // that are used by Bioformats to read the data correctly.
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

         AcqEngJMDADataSink sink = new AcqEngJMDADataSink(studio_.events(), this);
         currentAcquisition_ = new Acquisition(sink);
         currentAcquisition_.setDebugMode(core_.debugLogEnabled());

         loadRunnables(acquisitionSettings);

         summaryMetadata_ = currentAcquisition_.getSummaryMetadata();
         addMMSummaryMetadata(summaryMetadata_, sequenceSettings, posList_, studio_);

         MMAcquisition acq = new MMAcquisition(studio_,
               acquisitionSettings.save() ? acquisitionSettings.root() : null,
               acquisitionSettings.prefix(),
               summaryMetadata_,
               this,
               acquisitionSettings);
         curStore_ = acq.getDatastore();
         curPipeline_ = acq.getPipeline();
         sink.setDatastore(curStore_);
         sink.setPipeline(curPipeline_);

         zStage_ = core_.getFocusDevice();

         studio_.events().registerForEvents(this);
         studio_.events().post(new DefaultAcquisitionStartedEvent(curStore_, this,
               acquisitionSettings));


         if (sequenceSettings_.acqOrderMode() == AcqOrderMode.POS_TIME_CHANNEL_SLICE
               || sequenceSettings_.acqOrderMode() == AcqOrderMode.POS_TIME_SLICE_CHANNEL) {
            // Pos_time ordered acquisitions need their timelapse minimum start time to be
            // adjusted for each position.  The only place to do that seems to be a hardware hook.
            currentAcquisition_.addHook(timeLapseHook(acquisitionSettings),
                  AcquisitionAPI.BEFORE_HARDWARE_HOOK);
         }

         // Hook to move back the ZStage to its original position after a Z stack
         if (sequenceSettings.useSlices()) {
            currentAcquisition_.addHook(zPositionHook(acquisitionSettings,
                  Acquisition.BEFORE_HARDWARE_HOOK, null),
                  AcquisitionAPI.BEFORE_HARDWARE_HOOK);
            currentAcquisition_.addHook(zPositionHook(acquisitionSettings,
                        Acquisition.AFTER_EXPOSURE_HOOK, null),
                  AcquisitionAPI.AFTER_EXPOSURE_HOOK);
         }

         // These hooks make sure that continuousfocus is off when running a Z stack.
         if (studio_.core().isContinuousFocusEnabled()) {
            currentAcquisition_.addHook(continuousFocusHookBefore(acquisitionSettings),
                  AcquisitionAPI.BEFORE_HARDWARE_HOOK);
            currentAcquisition_.addHook(continuousFocusHookAfter(acquisitionSettings),
                  AcquisitionAPI.AFTER_EXPOSURE_HOOK);
         }

         // These hooks implement Autofocus
         if (sequenceSettings_.useAutofocus()) {
            currentAcquisition_.addHook(autofocusHookBefore(sequenceSettings_.skipAutofocusCount()),
                  AcquisitionAPI.BEFORE_HARDWARE_HOOK);
         }

         // Hooks to keep shutter open between channel and/or slices if desired
         if (((sequenceSettings.useChannels() && sequenceSettings.keepShutterOpenChannels())
               || (sequenceSettings.useSlices() && sequenceSettings.keepShutterOpenSlices()))
               && core_.getAutoShutter()) {
            currentAcquisition_.addHook(shutterHookBefore(acquisitionSettings),
                  AcquisitionAPI.AFTER_HARDWARE_HOOK);
            currentAcquisition_.addHook(shutterHookAfter(acquisitionSettings),
                  AcquisitionAPI.AFTER_EXPOSURE_HOOK);
         }

         if (sequenceSettings.useChannels()) {
            String channelGroup = core_.getChannelGroup();
            String channel = core_.getCurrentConfig(channelGroup);
            currentAcquisition_.addHook(restoreChannelHook(channelGroup, channel),
                  AcquisitionAPI.AFTER_HARDWARE_HOOK);
         }

         // Read for events
         currentAcquisition_.start();

         // Start the events and signal to finish when complete
         currentAcquisition_.submitEventIterator(createAcqEventIterator(acquisitionSettings));
         currentAcquisition_.finish();

         return curStore_;

      } catch (Throwable ex) {
         studio_.logs().showError((Exception) ex);
         if (currentAcquisition_ != null && currentAcquisition_.areEventsFinished()) {
            studio_.events().post(new DefaultAcquisitionEndedEvent(curStore_, this));
         }
         return null;
      }
   }

   /**
    * Higher level stuff in MM may depend on many hidden, poorly documented
    * ways on summary metadata generated by the acquisition engine.
    * This function adds in its fields in order to achieve compatibility.
    */
   protected static void addMMSummaryMetadata(JSONObject summaryMetadata,
                                              SequenceSettings acqSettings,
                                              PositionList posList,
                                              Studio studio)
         throws JSONException {

      final JSONArray chNames = new JSONArray();
      final JSONArray chColors = new JSONArray();
      if (acqSettings.useChannels() && acqSettings.channels().size() > 0) {
         for (ChannelSpec c : acqSettings.channels()) {
            if (c.useChannel()) {
               chNames.put(c.config());
               chColors.put(c.color().getRGB());
            }
         }
      } else {
         chNames.put("Default");
      }
      summaryMetadata.put(PropertyKey.CHANNEL_GROUP.key(), acqSettings.channelGroup());
      summaryMetadata.put(PropertyKey.CHANNEL_NAMES.key(), chNames);
      summaryMetadata.put(PropertyKey.CHANNEL_COLORS.key(), chColors);
      summaryMetadata.put(PropertyKey.CHANNELS.key(), getNumChannels(acqSettings));
      String computerName = "";
      try {
         computerName = InetAddress.getLocalHost().getHostName();
      } catch (UnknownHostException e) {
         studio.logs().logError(e);
      }
      summaryMetadata.put(PropertyKey.COMPUTER_NAME.key(), computerName);
      summaryMetadata.put(PropertyKey.DIRECTORY.key(), acqSettings.root());
      summaryMetadata.put(PropertyKey.FRAMES.key(), getNumFrames(acqSettings));
      summaryMetadata.put(PropertyKey.HEIGHT.key(), studio.core().getImageHeight());
      summaryMetadata.put(PropertyKey.INTERVAL_MS.key(), acqSettings.intervalMs());
      summaryMetadata.put(PropertyKey.KEEP_SHUTTER_OPEN_CHANNELS.key(),
               acqSettings.keepShutterOpenChannels());
      summaryMetadata.put(PropertyKey.KEEP_SHUTTER_OPEN_SLICES.key(),
               acqSettings.keepShutterOpenSlices());
      summaryMetadata.put("MDA_Settings", SequenceSettings.toJSONStream(acqSettings));
      summaryMetadata.put(PropertyKey.POSITIONS.key(), getNumPositions(acqSettings, posList));
      summaryMetadata.put(PropertyKey.PREFIX.key(), acqSettings.prefix());
      summaryMetadata.put(PropertyKey.PROFILE_NAME.key(), studio.profile().getProfileName());
      summaryMetadata.put(PropertyKey.SLICES.key(), getNumSlices(acqSettings));
      // MM MDA acquisitions have a defined order
      summaryMetadata.put(PropertyKey.SLICES_FIRST.key(),
            acqSettings.acqOrderMode() == AcqOrderMode.TIME_POS_SLICE_CHANNEL
                  || acqSettings.acqOrderMode() == AcqOrderMode.POS_TIME_CHANNEL_SLICE);
      summaryMetadata.put(PropertyKey.START_TIME.key(), DATE_FORMATTER.format(new Date()));
      summaryMetadata.put(PropertyKey.TIME_FIRST.key(),
            acqSettings.acqOrderMode() == AcqOrderMode.TIME_POS_SLICE_CHANNEL
                  || acqSettings.acqOrderMode() == AcqOrderMode.TIME_POS_CHANNEL_SLICE);
      summaryMetadata.put(PropertyKey.USER_NAME.key(), System.getProperty("user.name"));
      summaryMetadata.put(PropertyKey.WIDTH.key(), studio.core().getImageWidth());
      summaryMetadata.put(PropertyKey.Z_STEP_UM.key(), acqSettings.sliceZStepUm());
   }

   /**
    * Higher level code in MMStudio expects certain metadata tags that
    * are added by the Clojure engine. For compatibility, we must translate
    * AcqEnJ's metadata to include this here
    */
   public static void addMMImageMetadata(JSONObject imageMD) {
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
            String channelName = "" + AcqEngMetadata.getAxes(imageMD)
                  .get(AcqEngMetadata.CHANNEL_AXIS);
            imageMD.put(PropertyKey.CHANNEL_NAME.key(), channelName);
         }
         if (AcqEngMetadata.hasAxis(imageMD, "position")) {
            int index = (Integer) AcqEngMetadata.getAxisPosition(imageMD, "position");
            imageMD.put(PropertyKey.POSITION_INDEX.key(), index);
            if (imageMD.has(AcqEngMetadata.TAGS)) {
               JSONObject tags = imageMD.getJSONObject(AcqEngMetadata.TAGS);
               if (tags.has(AcqEngMetadata.POS_NAME)) {
                  imageMD.put(PropertyKey.POSITION_NAME.key(), tags.get(AcqEngMetadata.POS_NAME));
               }
            }
         }
         if (AcqEngMetadata.hasStageX(imageMD)) {
            imageMD.put(PropertyKey.X_POSITION_UM.key(), AcqEngMetadata.getStageX(imageMD));
         } else if (AcqEngMetadata.hasStageXIntended(imageMD)) {
            imageMD.put(PropertyKey.X_POSITION_UM.key(), AcqEngMetadata.getStageXIntended(imageMD));
         }
         if (AcqEngMetadata.hasStageY(imageMD)) {
            imageMD.put(PropertyKey.Y_POSITION_UM.key(), AcqEngMetadata.getStageY(imageMD));
         } else if (AcqEngMetadata.hasStageYIntended(imageMD)) {
            imageMD.put(PropertyKey.Y_POSITION_UM.key(), AcqEngMetadata.getStageYIntended(imageMD));
         }
         if (AcqEngMetadata.hasZPositionUm(imageMD)) {
            imageMD.put(PropertyKey.Z_POSITION_UM.key(), AcqEngMetadata.getZPositionUm(imageMD));
         } else if (AcqEngMetadata.hasStageZIntended(imageMD)) {
            imageMD.put(PropertyKey.Z_POSITION_UM.key(), AcqEngMetadata.getStageZIntended(imageMD));
         }
         // Add this in to avoid many errors being printed to the log, but probably this should
         // not be a required field
         imageMD.put(PropertyKey.FILE_NAME.key(), "Unknown");
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

      // Select channels that we are actually using
      List<ChannelSpec> chSpecs = new ArrayList<>();
      for (ChannelSpec chSpec : acquisitionSettings.channels()) {
         if (chSpec.useChannel()) {
            chSpecs.add(chSpec);
         }
      }

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
               null);
      }

      if (acquisitionSettings.useChannels()) {
         if (chSpecs.size() > 0) {
            Integer middleSliceIndex = (acquisitionSettings.slices().size() - 1) / 2;
            channels = MDAAcqEventModules.channels(chSpecs, middleSliceIndex, null);
         }
      }

      if (acquisitionSettings.usePositionList()) {
         positions = MDAAcqEventModules.positions(posList_, null);
         // TODO: is acq engine supposed to move multiple stages?
         // Yes: when moving to a new position, all stages in the MultiStagePosition instance
         // should be moved to the desired location
         // TODO: What about Z positions in position list
         // Yes: First move all stages in the MSP to their desired location, then do
         // whatever is asked to do.
      }

      if (acquisitionSettings.useFrames()) {
         timelapse = MDAAcqEventModules.timelapse(acquisitionSettings.numFrames(),
               acquisitionSettings.intervalMs(), null);
         // TODO custom time intervals
      }

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
    * @return Monitor function.
    */
   protected Function<AcquisitionEvent, AcquisitionEvent> acqEventMonitor(
         SequenceSettings settings) {
      return event -> {
         if (event != null && event.getMinimumStartTimeAbsolute() != null) {
            nextWakeTime_ = event.getMinimumStartTimeAbsolute();
         }
         return event;
      };
   }

   private AcquisitionHook timeLapseHook(SequenceSettings sequenceSettings) {
      return new AcquisitionHook() {
         private int lastPositionIndex_ = 0;
         private long relativePositionStartTime_ = 0;
         private long startTime_ = 0;
         private boolean positionMoved_ = false;

         @Override
         public AcquisitionEvent run(AcquisitionEvent event) {
            if (sequenceSettings.acqOrderMode() == AcqOrderMode.POS_TIME_CHANNEL_SLICE
                  || sequenceSettings.acqOrderMode() == AcqOrderMode.POS_TIME_SLICE_CHANNEL
                  && event.getAxisPosition("position") != null) {
               if (startTime_ == 0) {
                  startTime_ = System.currentTimeMillis();
               }

               int thisPosition = (int) event.getAxisPosition("position");
               if (thisPosition != lastPositionIndex_) {
                  relativePositionStartTime_ =  System.currentTimeMillis() - startTime_;
                  lastPositionIndex_ = (int) event.getAxisPosition("position");
                  positionMoved_ = true;
               }
               if (positionMoved_) {
                  long relativeStartTime = relativePositionStartTime_
                        + event.getMinimumStartTimeAbsolute() - startTime_;
                  event.setMinimumStartTime(relativeStartTime);
               }
            }
            if (event.getMinimumStartTimeAbsolute() != null) {
               nextWakeTime_ = event.getMinimumStartTimeAbsolute();
            }
            return event;
         }

         @Override
         public void close() {
         }
      };
   }


   /**
    * Hook function to disable continuous focus before running a Z Stack.
    * The hook should only be attached if continuous focus was on at the beginning of the
    * acquisition, which is why it is not checked here.
    *
    * @param sequenceSettings acquisition settings, ignored here.
    * @return The Hook.
    */
   private AcquisitionHook continuousFocusHookBefore(SequenceSettings sequenceSettings) {
      return new AcquisitionHook() {

         @Override
         public AcquisitionEvent run(AcquisitionEvent event) {
            if ((event.getZIndex() != null && event.getZIndex() == 0) || event.isZSequenced()) {
               try {
                  // this hook is called before the engine changes the hardware
                  // since we want to leave the system in a focussed state, first
                  // move the XY stage to where we want to image, then release autofocus.
                  if (event.getXPosition() != null && event.getYPosition() != null) {
                     studio_.core().setXYPosition(event.getXPosition(), event.getYPosition());
                  }
                  studio_.core().enableContinuousFocus(false);
               } catch (Exception ex) {
                  studio_.logs().logError(ex, "Failed to disable continuousfocus");
               }
            }
            return event;
         }

         @Override
         public void close() {
            // may be superfluous, but hey, why not
            try {
               studio_.core().enableContinuousFocus(true);
            } catch (Exception ex) {
               studio_.logs().logError(ex, "Failed to enable continuousfocus");
            }
         }
      };
   }


   /**
    * Hook function to re-enable continuous focus after running a z stack.
    *
    * @param sequenceSettings acquisition settings, used to see if we are at the end of
    *                         a Z Stack.
    * @return The Hook.
    */
   private AcquisitionHook continuousFocusHookAfter(SequenceSettings sequenceSettings) {
      return new AcquisitionHook() {

         @Override
         public AcquisitionEvent run(AcquisitionEvent event) {
            if (event.getZIndex() != null && sequenceSettings.useSlices()) {
               if (event.getZIndex() == sequenceSettings.slices().size() - 1) {
                  try {
                     studio_.core().enableContinuousFocus(true);
                  } catch (Exception ex) {
                     studio_.logs().logError(ex, "Failed to enable continuousfocus");
                  }
               }
            }
            return event;
         }

         @Override
         public void close() {
            // may be superfluous, but hey, why not
            try {
               studio_.core().enableContinuousFocus(true);
            } catch (Exception ex) {
               studio_.logs().logError(ex, "Failed to enable continuousfocus");
            }
         }
      };
   }

   /**
    * Hook function executing (software) autofocus.  When autofocus is checked in the MDA,
    * the autofocus should run before each channel / Z Stack combo (i.e. at each time point
    * and position.
    *
    * @return The Hook.
    */
   protected AcquisitionHook autofocusHookBefore(int skipFrames) {
      return new AcquisitionHook() {

         @Override
         public AcquisitionEvent run(AcquisitionEvent event) {
            if (!event.isAcquisitionFinishedEvent()
                  && (event.getZIndex() == null || event.getZIndex() == 0)
                  && (event.getAxisPosition(MDAAcqEventModules.POSITION_AXIS) == null
                        || (Integer) event.getAxisPosition(MDAAcqEventModules.POSITION_AXIS) == 0)
                  && (event.getAxisPosition(AcqEngMetadata.CHANNEL_AXIS) == null
                        || (Integer) event.getAxisPosition(AcqEngMetadata.CHANNEL_AXIS) == 0)) {
               if (event.getTIndex() != null && event.getTIndex() % skipFrames != 0) {
                  return event;
               }
               try {
                  // this hook is called before the engine changes the hardware
                  // since we want to leave the system in a focussed state, first
                  // move the XY stage to where we want to image, then autofocus.
                  if (event.getXPosition() != null && event.getYPosition() != null) {
                     studio_.core().setXYPosition(event.getXPosition(), event.getYPosition());
                  }
                  studio_.getAutofocusManager().getAutofocusMethod().fullFocus();
               } catch (Exception ex) {
                  studio_.logs().logError(ex, "Failed to autofocus.");
               }
            }
            return event;
         }

         @Override
         public void close() {
            // nothing to do here
         }
      };
   }

   private double zStagePositionBefore_;

   /**
    * Hook to return Z Stage to start position after a Z Stack.
    * May need to think about channels that do not do a Z Stack.
    *
    * @param sequenceSettings Settings for this acquisition
    * @param when Constant defined in acquisitoin API that define when this hook is run.
    * @return The actual Hook function
    **/
   protected AcquisitionHook zPositionHook(SequenceSettings sequenceSettings,
                                           int when, Integer acqIndex) {
      return new AcquisitionHook() {

         @Override
         public AcquisitionEvent run(AcquisitionEvent event) {
            if (event.isAcquisitionFinishedEvent()) {
               return event;
            }
            // do nothing if this is not our acquisition
            if (acqIndex != null
                  && event.getTags().containsKey(ACQ_IDENTIFIER)
                  && !(Integer.valueOf(event.getTags().get(ACQ_IDENTIFIER)).equals(acqIndex))) {
               return event;
            }
            try {
               if (when == AcquisitionAPI.BEFORE_HARDWARE_HOOK) {
                  if (event.isZSequenced() || event.getZIndex() == 0) {
                     if (!event.isZSequenced() && sequenceSettings.useChannels()
                             && (sequenceSettings.acqOrderMode()
                                       == AcqOrderMode.TIME_POS_SLICE_CHANNEL
                             || sequenceSettings.acqOrderMode()
                                       == AcqOrderMode.POS_TIME_SLICE_CHANNEL)) {
                        if ((Integer) event.getAxisPosition(AcqEngMetadata.CHANNEL_AXIS) != 0) {
                           return event;
                        }
                     }
                     zStagePositionBefore_ = core_.getPosition();
                  }
               } else if (when == AcquisitionAPI.AFTER_EXPOSURE_HOOK) {
                  if (event.isZSequenced()
                        || event.getZIndex() == sequenceSettings.slices().size() - 1) {
                     if (!event.isZSequenced() && sequenceSettings.useChannels()
                             && (sequenceSettings.acqOrderMode()
                                       == AcqOrderMode.TIME_POS_SLICE_CHANNEL
                             || sequenceSettings.acqOrderMode()
                                       == AcqOrderMode.POS_TIME_SLICE_CHANNEL)) {
                        if ((Integer) event.getAxisPosition(AcqEngMetadata.CHANNEL_AXIS)
                                != getNumChannels(sequenceSettings) - 1) {
                           return event;
                        }
                     }
                     core_.setPosition(zStagePositionBefore_);
                  }
               }
            } catch (Exception ex) {
               studio_.logs().logError(ex,
                     "Failed to return Z Stage to start position after Z Stack");
            }
            return event;
         }

         @Override
         public void close() {
            // nothing to do here
         }
      };
   }

   /**
    * Hook function to keep shutter open between channels of slices if desired.
    *
    * @param sequenceSettings acquisition settings, used to predict what the next event will be.
    * @return The Hook.
    */
   private AcquisitionHook shutterHookBefore(SequenceSettings sequenceSettings) {
      return new AcquisitionHook() {

         @Override
         public AcquisitionEvent run(AcquisitionEvent event) {
            if (!event.isAcquisitionFinishedEvent()) {
               try {
                  if (!event.isZSequenced() && sequenceSettings.keepShutterOpenSlices()) {
                     if (event.getZIndex() == 0) {
                        core_.setAutoShutter(false);
                        core_.setShutterOpen(true);
                     }
                  }
                  if (!event.isConfigGroupSequenced()
                        && sequenceSettings.keepShutterOpenChannels()) {
                     if ((Integer) event.getAxisPosition(AcqEngMetadata.CHANNEL_AXIS) == 0) {
                        core_.setAutoShutter(false);
                        core_.setShutterOpen(true);
                     }
                  }
               } catch (Exception ex) {
                  studio_.logs().logError(ex, "Failed to open shutter");
               }
            }
            return event;
         }

         @Override
         public void close() {
            // nothing to do here
         }
      };
   }

   /**
    * Hook function to close shutter when it was kept open between channels and/or slices.
    *
    * @param sequenceSettings acquisition settings, used to predict what the next event will be.
    * @return The Hook.
    */
   private AcquisitionHook shutterHookAfter(SequenceSettings sequenceSettings) {
      return new AcquisitionHook() {

         @Override
         public AcquisitionEvent run(AcquisitionEvent event) {
            if (!event.isAcquisitionFinishedEvent()) {
               try {
                  if (sequenceSettings.keepShutterOpenSlices()
                        && sequenceSettings.keepShutterOpenChannels()) {
                        if (event.getZIndex() == sequenceSettings.slices().size() - 1
                              && (Integer) event.getAxisPosition(AcqEngMetadata.CHANNEL_AXIS)
                                 == sequenceSettings.channels().size() - 1) {
                           core_.setShutterOpen(false);
                           core_.setAutoShutter(true);
                        }
                     } else {
                     if (!event.isZSequenced() && sequenceSettings.keepShutterOpenSlices()) {
                        if (event.getZIndex() == sequenceSettings.slices().size() - 1) {
                           core_.setShutterOpen(false);
                           core_.setAutoShutter(true);
                        }
                     }
                     if (!event.isConfigGroupSequenced()
                           && sequenceSettings.keepShutterOpenChannels()) {
                        if ((Integer) event.getAxisPosition(AcqEngMetadata.CHANNEL_AXIS)
                              == sequenceSettings.channels().size() - 1) {
                           core_.setShutterOpen(false);
                           core_.setAutoShutter(true);
                        }
                     }
                  }
               } catch (Exception ex) {
                  studio_.logs().logError(ex, "Failed to open shutter");
               }
            }
            return event;
         }

         @Override
         public void close() {
            // nothing to do here
         }
      };
   }

   private AcquisitionHook restoreChannelHook(String channelGroup, String channel) {
      return new AcquisitionHook() {
         @Override
         public AcquisitionEvent run(AcquisitionEvent event) {
            if (event.isAcquisitionFinishedEvent()) {
               try {
                  core_.setConfig(channelGroup, channel);
               } catch (Exception e) {
                  core_.logMessage(e.getMessage());
               }
            }
            return event;
         }

         @Override
         public void close() {

         }
      };
   }


   private void calculateSlices(SequenceSettings sequenceSettings) {
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
         sequenceSettings_ = sequenceSettings.copyBuilder().slices(slices).build();
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
         studio_.logs().logError(ex);
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


   private static int getNumChannels(SequenceSettings sequenceSettings) {
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
   private static int getNumFrames(SequenceSettings sequenceSettings) {
      int numFrames = sequenceSettings.numFrames();
      if (!sequenceSettings.useFrames()) {
         numFrames = 1;
      }
      return numFrames;
   }

   private static int getNumPositions(SequenceSettings sequenceSettings, PositionList posList) {
      if (posList == null) {
         return 1;
      }
      int numPositions = Math.max(1, posList.getNumberOfPositions());
      if (!sequenceSettings.usePositionList()) {
         numPositions = 1;
      }
      return numPositions;
   }

   private static int getNumSlices(SequenceSettings sequenceSettings) {
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
            studio_.logs().logError(ex);
            return false;
         }

      }
      return true;
   }


   private int getTotalImages(SequenceSettings sequenceSettings) {
      if (!sequenceSettings.useChannels() || sequenceSettings.channels().size() == 0) {
         return getNumFrames(sequenceSettings)
               * getNumSlices(sequenceSettings)
               * getNumChannels(sequenceSettings)
               * getNumPositions(sequenceSettings, posList_);
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
      return nrImages * getNumPositions(sequenceSettings, posList_);
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
      calculateSlices(sequenceSettings_);
      settingsChanged();
   }

   @Override
   public Datastore acquire() throws MMException {
      calculateSlices(sequenceSettings_);
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
            * ((long) getTotalImages(sequenceSettings_));
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
         studio_.logs().showError(ex, "Acquisition engine stop request failed");
      }
   }

   @Override
   public boolean abortRequest() {
      if (isAcquisitionRunning()) {
         String[] options = {"Abort", "Cancel"};
         List<DisplayWindow> displays = studio_.displays().getDisplays((DataProvider) curStore_);
         Component parentComponent = null;
         if (displays != null && ! displays.isEmpty()) {
            parentComponent = displays.get(0).getWindow();
         }
         int result = JOptionPane.showOptionDialog(parentComponent,
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
               studio_.logs().logError(ex);
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
      final int numFrames = getNumFrames(sequenceSettings_);
      final int numSlices = getNumSlices(sequenceSettings_);
      final int numPositions = getNumPositions(sequenceSettings_, posList_);
      final int numChannels = getNumChannels(sequenceSettings_);

      double exposurePerTimePointMs = 0.0;
      if (sequenceSettings_.useChannels()) {
         for (ChannelSpec channel : sequenceSettings_.channels()) {
            if (channel.useChannel()) {
               double channelExposure = channel.exposure();
               if (channel.doZStack()) {
                  channelExposure *= getNumSlices(sequenceSettings_);
               }
               channelExposure *= getNumPositions(sequenceSettings_, posList_);
               exposurePerTimePointMs += channelExposure;
            }
         }
      } else { // use the current settings for acquisition
         try {
            exposurePerTimePointMs = core_.getExposure()
                  * getNumSlices(sequenceSettings_)
                  * getNumPositions(sequenceSettings_, posList_);
         } catch (Exception ex) {
            studio_.logs().logError(ex, "Failed to get exposure time");
         }
      }

      final int totalImages = getTotalImages(sequenceSettings_);
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
         studio_.logs().logError(ex);
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
            studio_.logs().showError(e);
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
