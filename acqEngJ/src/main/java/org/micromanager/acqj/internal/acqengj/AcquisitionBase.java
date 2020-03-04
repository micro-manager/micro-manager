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
package org.micromanager.acqj.internal.acqengj;


import org.micromanager.acqj.api.Acquisition;
import org.micromanager.acqj.api.AcqEngMetadata;
import org.micromanager.acqj.api.ChannelGroupSettings;
import org.micromanager.acqj.api.DataSink;
import java.awt.geom.AffineTransform;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import mmcorej.CMMCore;
import mmcorej.TaggedImage;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.acqj.api.AcquisitionEvent;
import org.micromanager.acqj.api.XYStagePosition;
import org.micromanager.acqj.internal.acqengj.Engine;

/**
 * Abstract class that manages a generic acquisition. Subclassed into specific
 * types of acquisition. Minimal set of assumptions that mirror those in the core.
 * For example, assumes one Z stage, one XY stage, one channel group, etc
 */
public abstract class AcquisitionBase implements Acquisition {
   
   protected String xyStage_, zStage_;
   protected boolean zStageHasLimits_ = false;
   protected double zStageLowerLimit_, zStageUpperLimit_;
   protected AcquisitionEvent lastEvent_ = null;
   protected volatile boolean finished_ = false;
   private JSONObject summaryMetadata_;
   private String name_;
   private long startTime_ms_ = -1;
   private volatile boolean paused_ = false;
   protected ChannelGroupSettings channels_;
   //map generated at runtime of channel names to channel indices
   private HashMap<String, Integer> channelIndices_ = new HashMap<String, Integer>();
   //map generated at runtime of XY positions to position indices
   private HashMap<XYStagePosition, Integer> positionIndices_ = new HashMap<XYStagePosition, Integer>();
   
   protected MinimalAcquisitionSettings settings_;
   protected DataSink dataSink_;
   protected CMMCore core_;
   
   public AcquisitionBase(MinimalAcquisitionSettings settings, DataSink sink) {
      core_ = Engine.getCore();
      channels_ = settings.channels_;
      settings_ = settings;
      dataSink_ = sink;
      initialize();
   }
   
   
   /**
    * Cancel any unexecuted events and shutdown. Non-blocking
    */
   public abstract void abort();
   
   public abstract void waitForCompletion();
   
   protected abstract void addToSummaryMetadata(JSONObject summaryMetadata);
   
   protected abstract void addToImageMetadata(JSONObject tags);
   
   public MinimalAcquisitionSettings getAcquisitionSettings() {
      return settings_;
   }
   
   /**
    * 1) Get the names or core devices to be used in acquistion
    * 2) Create Summary metadata
    * 3) Initialize data sink
    */
   private void initialize() {              
      xyStage_ = core_.getXYStageDevice();
      zStage_ = core_.getFocusDevice();
      //"postion" is not generic name..and as of right now there is now way of getting generic z positions
      //from a z deviec in MM
      String positionName = "Position";
      try {
         if (core_.hasProperty(zStage_, positionName)) {
            zStageHasLimits_ = core_.hasPropertyLimits(zStage_, positionName);
            if (zStageHasLimits_) {
               zStageLowerLimit_ = core_.getPropertyLowerLimit(zStage_, positionName);
               zStageUpperLimit_ = core_.getPropertyUpperLimit(zStage_, positionName);
            }
         }
      } catch (Exception ex) {
         throw new RuntimeException("Problem communicating with core to get Z stage limits");
      }
      JSONObject summaryMetadata = AcqEngMetadata.makeSummaryMD(settings_.name_, this);
      addToSummaryMetadata(summaryMetadata);
     
      try {
         //keep local copy for viewer
         summaryMetadata_ = new JSONObject(summaryMetadata.toString());
      } catch (JSONException ex) {
         System.err.print("Couldn't copy summaary metadata");
         ex.printStackTrace();
      }
      dataSink_.initialize(this, summaryMetadata);
   }
   
   public void onDataSinkClosing() {
      dataSink_ = null;      
   }

   /**
    * Called by acquisition engine to save an image, shoudn't return until it as
    * been written to disk
    */
   public void saveImage(TaggedImage image) {
      if (image.tags == null && image.pix == null) {
         if (!finished_) {
            dataSink_.finished();
            finished_ = true;
         }
      } else {
         //this method doesnt return until all images have been writtent to disk
         dataSink_.putImage(image);
      }
   }

   /**
    * Get index of channel with given name, appending it to the list
    * of channels seen in acq so far. This enables adding arbitrary channels
    * at acquisition time
    * @param channelName
    * @return 
    */
  public int getChannelIndexFromName(String channelName) {
      if (!channelIndices_.containsKey(channelName)) {    
         List<Integer> indices = new LinkedList<Integer>(channelIndices_.values());
         indices.add(0, -1);
         int maxIndex = indices.stream().mapToInt(v -> v).max().getAsInt();
         channelIndices_.put(channelName, maxIndex + 1);
      }
      return channelIndices_.get(channelName);
   }
  
  /**
    * Get index of position based on it's intended x and y coordinates
    * @param channelName
    * @return 
    */
  public int getPositionIndexFromName(XYStagePosition position) {
      if (!positionIndices_.containsKey(position)) {
         
         List<Integer> indices = new LinkedList<Integer>(positionIndices_.values());
         indices.add(0, -1);
         int maxIndex = indices.stream().mapToInt(v -> v).max().getAsInt();
         positionIndices_.put(position, maxIndex + 1);
      }
      return positionIndices_.get(position);
   }
      
   protected String getXYStageName() {
      return xyStage_;
   }
   
   protected String getZStageName() {
      return zStage_;
   }
   
   public ChannelGroupSettings getChannels() {
      return channels_;
   }
   
   public boolean isComplete() {
      return finished_;
   }
   
   protected long getStartTime_ms() {
      return startTime_ms_;
   }
   
   protected void setStartTime_ms(long time) {
      startTime_ms_ = time;
   }
   
   public boolean isPaused() {
      return paused_;
   }
   
   public synchronized void togglePaused() {
      paused_ = !paused_;
   }
   
   public JSONObject getSummaryMetadata() {
      return summaryMetadata_;
   }
   
   public boolean anythingAcquired() {
      return dataSink_ == null ? true : dataSink_.anythingAcquired();
   }

   public boolean saveToDisk() {
      //TODO: add option not to
      return true;
   }
 
}
