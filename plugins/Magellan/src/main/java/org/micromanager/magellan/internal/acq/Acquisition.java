///////////////////////////////////////////////////////////////////////////////
// AUTHOR:       Henry Pinkard, henry.pinkard@gmail.com
//
// COPYRIGHT:    University of California, San Francisco, 2015
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
package org.micromanager.magellan.internal.acq;

import org.micromanager.magellan.internal.imagedisplay.MagellanImageCache;
import com.google.common.eventbus.Subscribe;
import java.awt.geom.AffineTransform;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.micromanager.magellan.internal.channels.MagellanChannelSpec;
import org.micromanager.magellan.internal.coordinates.MagellanAffineUtils;
import org.micromanager.magellan.internal.coordinates.XYStagePosition;
import org.micromanager.magellan.internal.main.Magellan;
import org.micromanager.magellan.internal.misc.Log;
import org.micromanager.magellan.internal.misc.MD;
import mmcorej.CMMCore;
import mmcorej.TaggedImage;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.magellan.api.MagellanAcquisitionAPI;
import org.micromanager.magellan.internal.imagedisplay.DisplaySettings;
import org.micromanager.magellan.internal.imagedisplay.events.ImageCacheClosingEvent;
import org.micromanager.magellan.internal.imagedisplay.MagellanDisplayController;

/**
 * Abstract class that manages a generic acquisition. Subclassed into specific
 * types of acquisition
 */
public abstract class Acquisition implements MagellanAcquisitionAPI {
   
   protected double zStep_;
   protected double zOrigin_;
   protected volatile int minSliceIndex_ = 0, maxSliceIndex_ = 0;
   protected String xyStage_, zStage_;
   protected boolean zStageHasLimits_ = false;
   protected double zStageLowerLimit_, zStageUpperLimit_;
   protected AcquisitionEvent lastEvent_ = null;
   protected volatile boolean finished_ = false;
   private JSONObject summaryMetadata_;
   private String name_;
   private long startTime_ms_ = -1;
   private int overlapX_, overlapY_;
   private volatile boolean paused_ = false;
   protected MagellanChannelSpec channels_;
   private MagellanEngine eng_;
   protected volatile boolean aborted_ = false;
   //map generated at runtime of channel names to channel indices
   private HashMap<String, Integer> channelIndices_ = new HashMap<String, Integer>();
   protected AcquisitionSettingsBase settings_;
   protected MagellanImageCache dataProvider_;
   
   public Acquisition(AcquisitionSettingsBase settings) {
      settings_ = settings;
   }
   
   public AcquisitionSettingsBase getAcquisitionSettings() {
      return settings_;
   }
   
   protected void initialize(String dir, String name, double overlapPercent, double zStep) {
      eng_ = MagellanEngine.getInstance();
      xyStage_ = Magellan.getCore().getXYStageDevice();
      zStage_ = Magellan.getCore().getFocusDevice();
      //"postion" is not generic name..and as of right now there is now way of getting generic z positions
      //from a z deviec in MM
      String positionName = "Position";
      try {
         if (Magellan.getCore().hasProperty(zStage_, positionName)) {
            zStageHasLimits_ = Magellan.getCore().hasPropertyLimits(zStage_, positionName);
            if (zStageHasLimits_) {
               zStageLowerLimit_ = Magellan.getCore().getPropertyLowerLimit(zStage_, positionName);
               zStageUpperLimit_ = Magellan.getCore().getPropertyUpperLimit(zStage_, positionName);
            }
         }
      } catch (Exception ex) {
         Log.log("Problem communicating with core to get Z stage limits");
      }
      zStep_ = zStep;
      overlapX_ = (int) (Magellan.getCore().getImageWidth() * overlapPercent / 100);
      overlapY_ = (int) (Magellan.getCore().getImageHeight() * overlapPercent / 100);
      JSONObject summaryMetadata = makeSummaryMD(name);
      try {
         //keep local copy for viewer
         summaryMetadata_ = new JSONObject(summaryMetadata.toString());
      } catch (JSONException ex) {
         System.err.print("Couldn'r copy summaary metadata");
         ex.printStackTrace();
      }
      
      DisplaySettings displaySettings = new DisplaySettings(channels_, summaryMetadata);
      dataProvider_ = new MagellanImageCache(dir, summaryMetadata, displaySettings);
      //storage class has determined unique acq name, so it can now be stored
      name_ = dataProvider_.getUniqueAcqName();
      
      dataProvider_.registerForEvents(this);

      //create display
      try {
         new MagellanDisplayController(dataProvider_, displaySettings, this);
      } catch (Exception e) {
         e.printStackTrace();
         Log.log("Couldn't create display succesfully");
      }
   }
   
   @Subscribe
   public void onImageCacheClosingEvent(ImageCacheClosingEvent event) {
      dataProvider_.unregisterForEvents(this);
      dataProvider_ = null;      
   }
   
   public abstract void start();
   
   protected abstract void shutdownEvents();
   
   public abstract boolean waitForCompletion();

   /**
    * Called by acquisition engine to save an image, shoudn't return until it as
    * been written to disk
    */
   void saveImage(TaggedImage image) {
      if (image.tags == null && image.pix == null) {
         if (!finished_) {
            dataProvider_.finished();
            finished_ = true;
         }
      } else {
         //this method doesnt return until all images have been writtent to disk
         dataProvider_.putImage(image);
      }
   }
   
   protected abstract JSONArray createInitialPositionList();
   
   public void abort() {
      //Do this on a seperate thread. Maybe this was to avoid deadlock?
      new Thread(new Runnable() {
         @Override
         public void run() {
            if (finished_) {
               //acq already aborted
               return;
            }
            aborted_ = true;
            if (Acquisition.this.isPaused()) {
               Acquisition.this.togglePaused();
            }
            shutdownEvents();
            //signal acquisition engine to start finishing process and wait for its completion
            if (Acquisition.this instanceof ExploreAcquisition) { //Magellan GUI acquisition already has a trailing finishing event
               eng_.finishAcquisition(Acquisition.this);
            }
            waitForCompletion();
         }
      }, "Aborting thread").start();
   }
   
   private int getChannelIndex(String channelName) {
      if (!channelIndices_.containsKey(channelName)) {
         
         List<Integer> indices = new LinkedList<Integer>(channelIndices_.values());
         indices.add(0, -1);
         int maxIndex = indices.stream().mapToInt(v -> v).max().getAsInt();
         channelIndices_.put(channelName, maxIndex + 1);
      }
      return channelIndices_.get(channelName);
   }
   
   public void addImageMetadata(JSONObject tags, AcquisitionEvent event, int timeIndex,
           int camChannelIndex, long elapsed_ms, double exposure, boolean multicamera) {
      //add tags
      try {
         long gridRow = 0, gridCol = 0;
         if (event.xyPosition_ != null) {
            gridRow = event.xyPosition_.getGridRow();
            gridCol = event.xyPosition_.getGridCol();
            MD.setStageX(tags, event.xyPosition_.getCenter().x);
            MD.setStageY(tags, event.xyPosition_.getCenter().y);
         }
         MD.setPositionName(tags, "Grid_" + gridRow + "_" + gridCol);
         MD.setPositionIndex(tags, event.positionIndex_);
         MD.setSliceIndex(tags, event.zIndex_);
         MD.setFrameIndex(tags, timeIndex);
         String channelName = event.channelName_;
         if (multicamera) {
            channelName += "_" + Magellan.getCore().getCameraChannelName(camChannelIndex);
         }
         //infer channel index at runtime
         int cIndex = getChannelIndex(event.channelName_);
         MD.setChannelIndex(tags, cIndex + camChannelIndex);
         MD.setChannelName(tags, channelName == null ? "" : channelName);
         MD.setZPositionUm(tags, event.zPosition_);
         MD.setElapsedTimeMs(tags, elapsed_ms);
         MD.setImageTime(tags, (new SimpleDateFormat("yyyy-MM-dd HH:mm:ss -")).format(Calendar.getInstance().getTime()));
         MD.setExposure(tags, exposure);
         MD.setGridRow(tags, gridRow);
         MD.setGridCol(tags, gridCol);

         //add data about surface
         //right now this only works for fixed distance from the surface
         if ((event.acquisition_ instanceof MagellanGUIAcquisition)
                 && ((MagellanGUIAcquisition) event.acquisition_).getSpaceMode() == MagellanGUIAcquisitionSettings.SURFACE_FIXED_DISTANCE_Z_STACK) {
            //add metadata about surface
            MD.setSurfacePoints(tags, ((MagellanGUIAcquisition) event.acquisition_).getFixedSurfacePoints());
         }
      } catch (Exception e) {
         e.printStackTrace();
         Log.log("Problem adding image metadata");
         throw new RuntimeException();
      }
   }
   
   private JSONObject makeSummaryMD(String prefix) {
      //num channels is camera channels * acquisitionChannels

      CMMCore core = Magellan.getCore();
      JSONObject summary = new JSONObject();
      MD.setAcqDate(summary, getCurrentDateAndTime());
      
      MD.setZCTOrder(summary, false);
      MD.setPixelTypeFromByteDepth(summary, (int) Magellan.getCore().getBytesPerPixel());
      MD.setBitDepth(summary, (int) Magellan.getCore().getImageBitDepth());
      MD.setWidth(summary, (int) Magellan.getCore().getImageWidth());
      MD.setHeight(summary, (int) Magellan.getCore().getImageHeight());
      MD.setSavingPrefix(summary, prefix);
      JSONArray initialPosList = this.createInitialPositionList();
      MD.setInitialPositionList(summary, initialPosList);
      MD.setPixelSizeUm(summary, core.getPixelSizeUm());
      MD.setZStepUm(summary, this.getZStep());
      MD.setIntervalMs(summary, this instanceof MagellanGUIAcquisition ? ((MagellanGUIAcquisition) this).getTimeInterval_ms() : 0);
      MD.setPixelOverlapX(summary, this.getOverlapX());
      MD.setPixelOverlapY(summary, this.getOverlapY());
      MD.setExploreAcq(summary, this instanceof ExploreAcquisition);
      //affine transform
      if (MagellanAffineUtils.isAffineTransformDefined()) {
         AffineTransform at = MagellanAffineUtils.getAffineTransform(0, 0);
         MD.setAffineTransformString(summary, MagellanAffineUtils.transformToString(at));
      } else {
         MD.setAffineTransformString(summary, "Undefined");
      }
      try {
         MD.setCoreXY(summary, Magellan.getCore().getXYStageDevice());
         MD.setCoreFocus(summary, Magellan.getCore().getFocusDevice());
      } catch (Exception e) {
         Log.log("couldn't get XY or Z stage from core");
      }
      return summary;
   }

   /**
    * Build a lazy stream of events based on the hierarchy of acquisition
    * functions
    *
    * @param acqFunctions
    * @return
    */
   protected Stream<AcquisitionEvent> makeEventStream(List<Function<AcquisitionEvent, Iterator<AcquisitionEvent>>> acqFunctions) {
      //Make a composed function that expands every level of the acquisition tree as needed
      AcquisitionEventIterator iterator = new AcquisitionEventIterator(new AcquisitionEvent(this), acqFunctions);
      Stream<AcquisitionEvent> targetStream = StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED), false);
      return targetStream;
   }
   
   protected Function<AcquisitionEvent, Iterator<AcquisitionEvent>> channels(MagellanChannelSpec channels) {
      return (AcquisitionEvent event) -> {
         return new Iterator<AcquisitionEvent>() {
            String channelName_ = null;
            
            @Override
            public boolean hasNext() {
               if (channels.nextActiveChannel(channelName_) != null) {
                  return true;
               }
               return false;
            }
            
            @Override
            public AcquisitionEvent next() {
               AcquisitionEvent channelEvent = event.copy();
               channelName_ = channels.nextActiveChannel(channelName_);
               channelEvent.channelName_ = channelName_;
               channelEvent.zPosition_ += channels.getChannelSetting(channelName_).offset_;
               return channelEvent;
            }
         };
      };
   }
   
   protected Function<AcquisitionEvent, Iterator<AcquisitionEvent>> zStack(int startSliceIndex, int stopSliceIndex) {
      return (AcquisitionEvent event) -> {
         return new Iterator<AcquisitionEvent>() {
            
            private int sliceIndex_ = startSliceIndex;
            
            @Override
            public boolean hasNext() {
               return sliceIndex_ < stopSliceIndex;
            }
            
            @Override
            public AcquisitionEvent next() {
               double zPos = sliceIndex_ * zStep_ + zOrigin_;
               AcquisitionEvent sliceEvent = event.copy();
               sliceEvent.zIndex_ = sliceIndex_;
               //Do plus equals here in case z positions have been modified by another function (e.g. channel specific focal offsets)
               sliceEvent.zPosition_ += zPos;
               sliceIndex_++;
               return sliceEvent;
            }
         };
      };
   }
   
   protected Function<AcquisitionEvent, Iterator<AcquisitionEvent>> positions(
           int[] positionIndices, List<XYStagePosition> positions) {
      return (AcquisitionEvent event) -> {
         Stream.Builder<AcquisitionEvent> builder = Stream.builder();
         if (positions == null) {
            builder.accept(event);
         } else {
            for (int index = 0; index < positionIndices.length; index++) {
               AcquisitionEvent posEvent = event.copy();
               posEvent.positionIndex_ = positionIndices[index];
               posEvent.xyPosition_ = positions.get(posEvent.positionIndex_);
               builder.accept(posEvent);
            }
         }
         return builder.build().iterator();
      };
   }
   
   public String getXYStageName() {
      return xyStage_;
   }
   
   public String getZStageName() {
      return zStage_;
   }

   /**
    * indices are 1 based and positive
    *
    * @param sliceIndex -
    * @param frameIndex -
    * @return
    */
   public double getZCoordinateOfDisplaySlice(int displaySliceIndex) {
      displaySliceIndex += minSliceIndex_;
      return zOrigin_ + zStep_ * displaySliceIndex;
   }
   
   public int getDisplaySliceIndexFromZCoordinate(double z) {
      return (int) Math.round((z - zOrigin_) / zStep_) - minSliceIndex_;
   }
   
   public MagellanChannelSpec getChannels() {
      return channels_;
   }
   
   public int getNumSlices() {
      return maxSliceIndex_ - minSliceIndex_ + 1;
   }
   
   public int getMinSliceIndex() {
      return minSliceIndex_;
   }
   
   public int getMaxSliceIndex() {
      return maxSliceIndex_;
   }
   
   public boolean isFinished() {
      return finished_;
   }
   
   public long getStartTime_ms() {
      return startTime_ms_;
   }
   
   public void setStartTime_ms(long time) {
      startTime_ms_ = time;
   }
   
   public int getOverlapX() {
      return overlapX_;
   }
   
   public int getOverlapY() {
      return overlapY_;
   }
   
   public String getName() {
      return name_;
   }
   
   public double getZStep() {
      return zStep_;
   }
   
   public boolean isPaused() {
      return paused_;
   }
   
   public synchronized void togglePaused() {
      paused_ = !paused_;
   }
   
   private static String getCurrentDateAndTime() {
      DateFormat df = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
      Calendar calobj = Calendar.getInstance();
      return df.format(calobj.getTime());
   }
   
   public JSONObject getSummaryMetadata() {
      return summaryMetadata_;
   }
   
   public boolean anythingAcquired() {
      return dataProvider_ == null ? true : dataProvider_.anythingAcquired();
   }
   
}
