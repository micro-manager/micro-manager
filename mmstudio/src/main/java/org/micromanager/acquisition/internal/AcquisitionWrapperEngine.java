
package org.micromanager.acquisition.internal;

import com.google.common.eventbus.Subscribe;
import mmcorej.CMMCore;
import mmcorej.Configuration;
import mmcorej.PropertySetting;
import mmcorej.StrVector;
import mmcorej.TaggedImage;
import mmcorej.org.json.JSONObject;
import org.micromanager.PositionList;
import org.micromanager.Studio;
import org.micromanager.acquisition.ChannelSpec;
import org.micromanager.acquisition.SequenceSettings;
import org.micromanager.data.Datastore;
import org.micromanager.data.Pipeline;
import org.micromanager.data.internal.DefaultDatastore;
import org.micromanager.acquisition.AcquisitionEndedEvent;
import org.micromanager.events.NewPositionListEvent;
import org.micromanager.events.internal.InternalShutdownCommencingEvent;
import org.micromanager.internal.MMStudio;
import org.micromanager.internal.interfaces.AcqSettingsListener;
import org.micromanager.internal.utils.AcqOrderMode;
import org.micromanager.internal.utils.MMException;
import org.micromanager.internal.utils.NumberUtils;
import org.micromanager.internal.utils.ReportingUtils;

import javax.swing.JOptionPane;
import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;


public final class AcquisitionWrapperEngine implements AcquisitionEngine {

   private CMMCore core_;
   protected Studio studio_;
   private PositionList posList_;
   private String zstage_;
   private SequenceSettings sequenceSettings_;

   private IAcquisitionEngine2010 acquisitionEngine2010_;
   protected JSONObject summaryMetadata_;
   private ArrayList<AcqSettingsListener> settingsListeners_;
   private Datastore curStore_;
   private Pipeline curPipeline_;

   public AcquisitionWrapperEngine() {
      settingsListeners_ = new ArrayList<>();
      sequenceSettings_ = (new SequenceSettings.Builder()).build();
   }

   public SequenceSettings getSequenceSettings() { return sequenceSettings_; }

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
   
   public void settingsChanged() {
       for (AcqSettingsListener listener:settingsListeners_) {
           listener.settingsChanged();
       }
   }
   
   protected IAcquisitionEngine2010 getAcquisitionEngine2010() {
      if (acquisitionEngine2010_ == null) {
         acquisitionEngine2010_ = ((MMStudio) studio_).getAcquisitionEngine2010();
      }
      return acquisitionEngine2010_;
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

   protected Datastore runAcquisition(SequenceSettings sequenceSettings) {
      SequenceSettings.Builder sb = sequenceSettings.copyBuilder();

      //Make sure computer can write to selected location and there is enough space to do so
      if (sequenceSettings.save()) {
         File root = new File(sequenceSettings.root());
         if (!root.canWrite()) {
            int result = JOptionPane.showConfirmDialog(null, 
                    "The specified root directory\n" + root.getAbsolutePath() +
                    "\ndoes not exist. Create it?", "Directory not found.", 
                    JOptionPane.YES_NO_OPTION);
            if (result == JOptionPane.YES_OPTION) {
               if (!root.mkdirs() || !root.canWrite()) {
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

         DefaultDatastore.setPreferredSaveMode(studio_, sequenceSettings.saveMode());

      }

      // manipulate positionlist
      PositionList posListToUse = posList_;
      if (posList_ == null && sequenceSettings_.usePositionList()) {
         posListToUse = studio_.positions().getPositionList();
      }

      // The clojure acquisition engine always uses numFrames, and customIntervals
      // unless they are null.
      if (sequenceSettings.useCustomIntervals()) {
         sb.numFrames(sequenceSettings.customIntervalsMs().size());
      } else {
         sb.customIntervalsMs(null);
      }

      // Several "translations" have to be made to accommodate the Clojure engine:
      if (!sequenceSettings.useFrames()) { sb.numFrames(0); }
      if (!sequenceSettings.useChannels()) { sb.channels(null); }
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
      }

      try {
         // Start up the acquisition engine
         SequenceSettings acquisitionSettings = sb.build();
         BlockingQueue<TaggedImage> engineOutputQueue = getAcquisitionEngine2010().run(
                 acquisitionSettings, true, posListToUse,
                 studio_.getAutofocusManager().getAutofocusMethod());

         // note: summaryMetadata contain instructions how/where to safe the data
         // summary metadata generated in the clojure acq engine will look at the
         // sequenceSettings.save flag and set "Directory" and "Prefix" only
         // when save == true.  summaryMetadata.Directory and Prefix are used
         // by MMAcquisition to decide whether and where to save the acquisition.
         summaryMetadata_ = getAcquisitionEngine2010().getSummaryMetadata();

         // file type (multi or single) is a global setting, not sure where/when that is applied

         boolean shouldShow = acquisitionSettings.shouldDisplayImages();
         MMAcquisition acq = new MMAcquisition(studio_, summaryMetadata_, this,
                 shouldShow);
         curStore_ = acq.getDatastore();
         curPipeline_ = acq.getPipeline();

         studio_.events().post(new DefaultAcquisitionStartedEvent(curStore_,
                  this, acquisitionSettings));

         // Start pumping images through the pipeline and into the datastore.
         DefaultTaggedImageSink sink = new DefaultTaggedImageSink(
                 engineOutputQueue, curPipeline_, curStore_, this, studio_.events());
         sink.start(() -> getAcquisitionEngine2010().stop());
        
         return curStore_;

      } catch (Throwable ex) {
         ReportingUtils.showError(ex);
         studio_.events().post(new DefaultAcquisitionEndedEvent(
                  curStore_, this));
         return null;
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

   public int getNumFrames() {
      int numFrames = sequenceSettings_.numFrames();
      if (!sequenceSettings_.useFrames()) {
         numFrames = 1;
      }
      return numFrames;
   }

   private int getNumPositions() {
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
         // XXX How should this be handled?
         return Integer.MAX_VALUE;
      }
      return 1 + (int)Math.abs( (sequenceSettings_.sliceZTopUm() - sequenceSettings_.sliceZBottomUm())
              / sequenceSettings_.sliceZStepUm());
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
                  if (t % (channel.skipFactorFrame() + 1) != 0 ) {
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

   public long getTotalMemory() {
      CMMCore core = studio_.core();
      return core.getImageWidth() * core.getImageHeight() *
              core.getBytesPerPixel() * ((long) getTotalImages());
   }

   private void updateChannelCameras() {
      ArrayList<ChannelSpec> camChannels = new ArrayList<>();
      ArrayList<ChannelSpec> channels = sequenceSettings_.channels();
      for (int row = 0; row < channels.size(); row++) {
         camChannels.add(row,
                 channels.get(row).copyBuilder().camera(getSource(channels.get(row))).build());
      }
      sequenceSettings_ = sequenceSettings_.copyBuilder().channels(camChannels).build();
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


//////////////////// Actions ///////////////////////////////////////////
   @Override
   public void stop(boolean interrupted) {
      try {
         if (acquisitionEngine2010_ != null) {
            acquisitionEngine2010_.stop();
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
         else {
            return false;
         }
      }
      return true;
   }

   @Override
   public boolean abortRequested() {
      return acquisitionEngine2010_.stopHasBeenRequested();
   }

   @Override
   public void shutdown() {
      stop(true);
   }

   @Override
   public void setPause(boolean state) {
      if (state) {
         acquisitionEngine2010_.pause();
      } else {
         acquisitionEngine2010_.resume();
      }
   }

//// State Queries /////////////////////////////////////////////////////
   @Override
   public boolean isAcquisitionRunning() {
      // Even after the acquisition finishes, if the pipeline is still "live",
      // we should consider the acquisition to be running.
      if (acquisitionEngine2010_ != null) {
         return (acquisitionEngine2010_.isRunning() ||
               (curPipeline_ != null && !curPipeline_.isHalted()));
      } else {
         return false;
      }
   }

   @Override
   public boolean isFinished() {
      if (acquisitionEngine2010_ != null) {
         return acquisitionEngine2010_.isFinished();
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
      return acquisitionEngine2010_.nextWakeTime();
   }


//////////////////// Setters and Getters ///////////////////////////////

   @Override
   public void setPositionList(PositionList posList) {
      posList_ = posList;
   }

   @Subscribe
   public void OnNewPositionListEvent(NewPositionListEvent newPositionListEvent) {
      posList_ = newPositionListEvent.getPositionList();
   }

   @Override
   public void setParentGUI(Studio parent) {
      studio_ = parent;
      core_ = studio_.core();
      studio_.events().registerForEvents(this);
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
      sequenceSettings_ = (new SequenceSettings.Builder(sequenceSettings_)).
              channels(channels).build();
   }
   @Override
   public void setChannels(ArrayList<ChannelSpec> channels) {
      sequenceSettings_ = (new SequenceSettings.Builder(sequenceSettings_)).
              channels(channels).build();
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

      if (groups.length < 1) {
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

   /**
    * Sets the channel group in the core
    * Replies on callbacks to update the UI as well as sequenceSettings
    * (SequenceSettings are updated in the callback function in AcqControlDlg)
    * @param group name of group to set as the new Channel Group
    * @return true when successful, false if no change is needed or when the change fails
    */
   @Override
   public boolean setChannelGroup(String group) {
      String curGroup = core_.getChannelGroup();
      if (!(group != null &&
            (curGroup == null || !curGroup.contentEquals(group)))) {
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
    * @deprecated unclear what this should be doing
    */
   @Override
   @Deprecated
   public void clear() {
      // unclear what the purpose is  Delete?
   }


   @Override
   public void setShouldDisplayImages(boolean shouldDisplay) {
      sequenceSettings_ = sequenceSettings_.copyBuilder().shouldDisplayImages(shouldDisplay).build();
   }

   protected boolean enoughDiskSpace() {
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

   @Override
   public String getVerboseSummary() {
      int numFrames = getNumFrames();
      int numSlices = getNumSlices();
      int numPositions = getNumPositions();
      int numChannels = getNumChannels();

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

      int totalImages = getTotalImages();
      long totalMB = getTotalMemory() / (1024 * 1024);

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
              + "\nTotal memory: " + (totalMB <= 1024 ? totalMB + " MB" : NumberUtils.doubleToDisplayString(totalMB/1024.0) + " GB")
              + durationString;

      if (sequenceSettings_.useFrames() || sequenceSettings_.usePositionList() ||
              sequenceSettings_.useChannels() || sequenceSettings_.useSlices()) {
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

         if ((sequenceSettings_.useFrames() || sequenceSettings_.usePositionList()) &&
                 (sequenceSettings_.useChannels() || sequenceSettings_.useSlices())) {
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
      return acquisitionEngine2010_.isPaused();
   }

   protected boolean isFocusStageAvailable() {
      return zstage_ != null && zstage_.length() > 0;
   }

   /**
    * The name of this function is a bit misleading
    * Every channel group name provided as an argument will return
    * true, unless the group exists and only contains a single property with
    * propertylimits (i.e. a slider in the UI)
    * @param group channel group name to be tested
    * @return false if the group exists and only has a single property that has
    *             propertylimits, true otherwise
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
   
   @Subscribe
   public void onAcquisitionEnded(AcquisitionEndedEvent event) {
      curStore_ = null;
      curPipeline_ = null;
   }

   @Subscribe
   public void onShutdownCommencing(InternalShutdownCommencingEvent event) {
      if (!event.isCanceled() && isAcquisitionRunning()) {
         int result = JOptionPane.showConfirmDialog(null,
               "Acquisition in progress. Are you sure you want to exit and discard all data?",
               "Micro-Manager", JOptionPane.YES_NO_OPTION,
               JOptionPane.INFORMATION_MESSAGE);

         if (result == JOptionPane.YES_OPTION) {
            getAcquisitionEngine2010().stop();
         }
         else {
            event.cancelShutdown();
         }
      }
   }
}