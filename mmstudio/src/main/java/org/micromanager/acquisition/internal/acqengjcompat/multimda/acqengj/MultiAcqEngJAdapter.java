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
import org.micromanager.acquisition.internal.DefaultAcquisitionEndedEvent;
import org.micromanager.acquisition.internal.DefaultAcquisitionStartedEvent;
import org.micromanager.acquisition.internal.MMAcquisition;
import org.micromanager.acquisition.internal.acqengjcompat.AcqEngJAdapter;
import org.micromanager.acquisition.internal.acqengjcompat.MDAAcqEventModules;
import org.micromanager.data.DataProvider;
import org.micromanager.data.Datastore;
import org.micromanager.data.Pipeline;
import org.micromanager.data.internal.DefaultDatastore;
import org.micromanager.data.internal.PropertyKey;
import org.micromanager.display.DisplayWindow;
import org.micromanager.events.NewPositionListEvent;
import org.micromanager.events.internal.InternalShutdownCommencingEvent;
import org.micromanager.internal.interfaces.AcqSettingsListener;
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


   public static final String ACQ_IDENTIFIER = "Acq_Identifier";

   private Acquisition currentMultiMDA_;

   private CMMCore core_;
   protected Studio studio_;
   private PositionList posList_;
   private String zStage_;
   private SequenceSettings sequenceSettings_;

   //protected JSONObject summaryMetadata_;
   private ArrayList<AcqSettingsListener> settingsListeners_;
   private List<Datastore> stores_;
   private List<Pipeline> pipelines_;

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
      sequenceSettings_ = (new SequenceSettings.Builder()).build();
   }

   /**
    * This is where the work happens.
    *
    * @param sequenceSettings First SequenceSettings defines the time lapse, all subsequent
    *                         ones define acquisitions that should be executed consecutively.
    * @param positionLists PositionLists for each sequenceSetting (>=1).  The size of this list
    *                      must be 1 fewer than the size of sequenceSettings.
    * @return Datastores corresponding to sequenceSettings 1 and up
    */
   public List<Datastore> runAcquisition(List<SequenceSettings> sequenceSettings,
                                          List<PositionList> positionLists) {
      if (sequenceSettings.size() < 1 || positionLists.size() != sequenceSettings.size() - 1) {
         studio_.logs().logError("Please use Position Lists for each acquisition");
         return null;
      }
      stores_ = new ArrayList<>(sequenceSettings.size() - 1);
      pipelines_ = new ArrayList<>(sequenceSettings.size() - 1);
      for (int i = 1; i < sequenceSettings.size(); i++) {
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

      // manipulate positionlist
      PositionList posListToUse = posList_;
      if (posList_ == null && sequenceSettings_.usePositionList()) {
         posListToUse = studio_.positions().getPositionList();
      }
      posList_ = posListToUse;

      try {
         // Start up the acquisition engine

         MultiAcqEngJMDADataSink sink = new MultiAcqEngJMDADataSink(studio_.events());
         currentMultiMDA_ = new Acquisition(sink);
         currentMultiMDA_.setDebugMode(core_.debugLogEnabled());

         // TODO:
         // loadRunnables(acquisitionSettings);

         // This TaggedImageProcessor is used to divert images away from the optional
         // processing and saving of AcqEngJ, and into the system used by the studio API
         // (which has its own system for processing and saving)

         // MMAcquisition
         for (int i = 1; i < sequenceSettings.size(); i++) {
            JSONObject summaryMetadata = currentMultiMDA_.getSummaryMetadata();
            addMMSummaryMetadata(summaryMetadata, sequenceSettings.get(i),
                  positionLists.get(i - 1));
            MMAcquisition acq = new MMAcquisition(studio_,
                  sequenceSettings.get(i).save() ? sequenceSettings.get(i).root() : null,
                  sequenceSettings.get(i).prefix(),
                  summaryMetadata,
                  this,
                  sequenceSettings.get(i).shouldDisplayImages());
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

         for (int i = 0; i < sequenceSettings.size() - 1; i++) {
            studio_.events().post(new DefaultAcquisitionStartedEvent(stores_.get(i), this,
                  sequenceSettings.get(i + 1)));
         }

         // Read for events
         currentMultiMDA_.start();

         // Start the events and signal to finish when complete
         currentMultiMDA_.submitEventIterator(createAcqEventIterator(
               sequenceSettings, positionLists));
         currentMultiMDA_.finish();

         return stores_;

      } catch (Throwable ex) {
         ReportingUtils.showError(ex);
         if (currentMultiMDA_ != null && currentMultiMDA_.areEventsFinished()) {
            for (int i = 0; i < sequenceSettings.size() - 1; i++) {
               studio_.events().post(new DefaultAcquisitionEndedEvent(stores_.get(i), this));
            }
         }
         return null;
      }
   }


   /**
    * Higher level code in MMStudio expects certain metadata tags that
    * are added by the Clojure engine. For compatibility, we must translate
    * AcqEnJ's metadata to include this here
    */
   public static void addMMImageMetadata(JSONObject imageMD) {
      // These might be required...

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
            imageMD.put(PropertyKey.POSITION_INDEX.key(),
                  AcqEngMetadata.getAxisPosition(imageMD, "position"));
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
         currentMultiMDA_.addHook(new AcquisitionHook() {
            @Override
            public AcquisitionEvent run(AcquisitionEvent event) {
               boolean zMatch = event.getZIndex() == null || event.getZIndex() == r.slice_;
               boolean tMatch = event.getTIndex() == null || event.getTIndex() == r.frame_;
               boolean cMatch = event.getConfigPreset() == null
                     || acquisitionSettings.channels().get(r.channel_).config()
                           .equals(event.getConfigPreset());
               boolean pMatch = event.getAxisPosition(
                     MultiMDAAcqEventModules.POSITION_AXIS) == null
                     || ((Integer) event.getAxisPosition(
                     MultiMDAAcqEventModules.POSITION_AXIS))
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
   private Iterator<AcquisitionEvent> createAcqEventIterator(
         List<SequenceSettings> acquisitionSettings, List<PositionList> positionLists)
         throws Exception {
      Function<AcquisitionEvent, Iterator<AcquisitionEvent>> channels = null;
      Function<AcquisitionEvent, Iterator<AcquisitionEvent>> zStack = null;
      Function<AcquisitionEvent, Iterator<AcquisitionEvent>> positions = null;
      Function<AcquisitionEvent, Iterator<AcquisitionEvent>> timelapse = null;

      ArrayList<Function<AcquisitionEvent, Iterator<AcquisitionEvent>>> acqFunctions
            = new ArrayList<>();

      // First add timePoints, these are contained in acquisitionSettings.get(0)
      if (acquisitionSettings.get(0).useFrames()) {
         timelapse = MDAAcqEventModules.timelapse(acquisitionSettings.get(0).numFrames(),
               acquisitionSettings.get(0).intervalMs(), null);
         acqFunctions.add(timelapse);

         //TODO custom time intervals
      }

      for (int i = 1; i < acquisitionSettings.size(); i++) {
         HashMap<String, String> tag = new HashMap<>(1);
         tag.put(ACQ_IDENTIFIER, String.valueOf(i - 1));

         if (acquisitionSettings.get(i).useSlices()) {
            double origin = acquisitionSettings.get(i).slices().get(0);
            if (acquisitionSettings.get(i).relativeZSlice()) {
               origin = studio_.core().getPosition() + acquisitionSettings.get(i).slices().get(0);
            }
            zStack = MDAAcqEventModules.zStack(0,
                  acquisitionSettings.get(i).slices().size() - 1,
                  acquisitionSettings.get(i).sliceZStepUm(),
                  origin,
                  acquisitionSettings.get(i).channels(),
                  tag);
         }

         if (acquisitionSettings.get(i).useChannels()) {
            Integer middleSliceIndex = (acquisitionSettings.get(i).slices().size() - 1) / 2;
            channels = MDAAcqEventModules.channels(
                  acquisitionSettings.get(i).channels(), middleSliceIndex, tag);
            //TODO: keep shutter open
            //TODO: skip frames
            //TODO: z stack off for channel
         }

         if (acquisitionSettings.get(i).usePositionList()) {
            positions = MDAAcqEventModules.positions(positionLists.get(i - 1), tag);
         }

         if (acquisitionSettings.get(i).acqOrderMode() == AcqOrderMode.TIME_POS_CHANNEL_SLICE) {
            if (acquisitionSettings.get(i).usePositionList()) {
               acqFunctions.add(positions);
            }
            if (acquisitionSettings.get(i).useChannels()) {
               acqFunctions.add(channels);
            }
            if (acquisitionSettings.get(i).useSlices()) {
               acqFunctions.add(zStack);
            }
         } else if (acquisitionSettings.get(i).acqOrderMode() == AcqOrderMode
               .TIME_POS_SLICE_CHANNEL) {
            if (acquisitionSettings.get(i).usePositionList()) {
               acqFunctions.add(positions);
            }
            if (acquisitionSettings.get(i).useSlices()) {
               acqFunctions.add(zStack);
            }
            if (acquisitionSettings.get(i).useChannels()) {
               acqFunctions.add(channels);
            }
         } else {
            throw new RuntimeException("Unknown acquisition order");
         }
      }

      AcquisitionEvent baseEvent = new AcquisitionEvent(currentMultiMDA_);
      return new AcquisitionEventIterator(baseEvent, acqFunctions, acqEventMonitor());

   }

   /**
    * This function monitors acquisition events as they are dynamically created.
    *
    * @return Function with Acquisition Events
    */
   private Function<AcquisitionEvent, AcquisitionEvent> acqEventMonitor() {
      return new Function<AcquisitionEvent, AcquisitionEvent>() {
         private int lastPositionIndex_ = 0;
         private long relativePositionStartTime_ = 0;
         private long startTime_ = 0;
         private boolean positionMoved_ = false;

         @Override
         public AcquisitionEvent apply(AcquisitionEvent event) {
            /*
            if (sequenceSettings_.acqOrderMode() == AcqOrderMode.POS_TIME_CHANNEL_SLICE
                    || sequenceSettings_.acqOrderMode() == AcqOrderMode.POS_TIME_SLICE_CHANNEL
                     && event.getAxisPosition("position") != null) {
               if (startTime_ == 0) {
                  startTime_ = System.currentTimeMillis();
               }

               int thisPosition = (int) event.getAxisPosition("position");
               if (thisPosition != lastPositionIndex_) {
                  relativePositionStartTime_ =  System.currentTimeMillis() - startTime_;
                  System.out.println("Position " + thisPosition + " started "
                          + relativePositionStartTime_ + "  ms after acquisition start");
                  lastPositionIndex_ = (int) event.getAxisPosition("position");
                  positionMoved_ = true;
               }
               if (positionMoved_) {
                  long relativeStartTime = relativePositionStartTime_
                          + event.getMinimumStartTimeAbsolute() - startTime_;
                  int frame = (int) event.getAxisPosition("time");
                  System.out.println("Pos " + thisPosition + ", Frame " + frame
                          + " start at " + relativeStartTime);
                  event.setMinimumStartTime(relativeStartTime);
               }
            }
             */
            if (event.getMinimumStartTimeAbsolute() != null) {
               nextWakeTime_ = event.getMinimumStartTimeAbsolute();
            }
            return event;
         }
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
                  System.out.println("Position " + thisPosition + " started "
                        + relativePositionStartTime_ + "  ms after acquisition start");
                  lastPositionIndex_ = (int) event.getAxisPosition("position");
                  positionMoved_ = true;
               }
               if (positionMoved_) {
                  long relativeStartTime = relativePositionStartTime_
                        + event.getMinimumStartTimeAbsolute() - startTime_;
                  int frame = (int) event.getAxisPosition("time");
                  System.out.println("Pos " + thisPosition + ", Frame " + frame
                        + " start at " + relativeStartTime);
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

   /**
    * Returns estimate of memory that will be used by this acquisition.
    *
    * @return Estimate of memory used by the acquii
    */
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
         return (!currentMultiMDA_.areEventsFinished() || pipeLinesFinished);
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

   public double getFrameIntervalMs() {
      return sequenceSettings_.intervalMs();
   }

   public boolean isZSliceSettingEnabled() {
      return sequenceSettings_.useSlices();
   }

   public void setChannel(int row, ChannelSpec sp) {
      ArrayList<ChannelSpec> channels = sequenceSettings_.channels();
      channels.add(row, sp);
      sequenceSettings_ = (new SequenceSettings.Builder(sequenceSettings_))
            .channels(channels).build();
   }

   public void setChannels(ArrayList<ChannelSpec> channels) {
      sequenceSettings_ = (new SequenceSettings.Builder(sequenceSettings_))
            .channels(channels).build();
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


   public void setShouldDisplayImages(boolean shouldDisplay) {
      sequenceSettings_ =
            sequenceSettings_.copyBuilder().shouldDisplayImages(shouldDisplay).build();
   }

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