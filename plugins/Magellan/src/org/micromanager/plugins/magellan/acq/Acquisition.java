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
package org.micromanager.plugins.magellan.acq;

import ij.IJ;
import org.micromanager.plugins.magellan.imagedisplay.DisplayPlus;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.micromanager.plugins.magellan.bidc.JavaLayerImageConstructor;
import org.micromanager.plugins.magellan.channels.ChannelSetting;
import java.awt.Color;
import java.util.ArrayList;
import org.micromanager.plugins.magellan.json.JSONArray;
import org.micromanager.plugins.magellan.json.JSONObject;
import org.micromanager.plugins.magellan.main.Magellan;

/**
 * Abstract class that manages a generic acquisition. Subclassed into specific
 * types of acquisition
 */
public abstract class Acquisition implements AcquisitionEventSource{

   //max numberof images that are held in queue to be saved
   private static final int OUTPUT_QUEUE_SIZE = 40;
   
   protected final double zStep_;
   private BlockingQueue<MagellanTaggedImage> engineOutputQueue_;
   protected String xyStage_, zStage_;
   protected boolean zStageHasLimits_ = false;
   protected double zStageLowerLimit_, zStageUpperLimit_;
   protected BlockingQueue<AcquisitionEvent> events_;
   protected TaggedImageSink imageSink_;
   protected volatile boolean finished_ = false;
   private String name_;
   private long startTime_ms_ = -1;
   protected MultiResMultipageTiffStorage imageStorage_;
   private int overlapX_, overlapY_;
   private volatile boolean pause_ = false;
   private Object pauseLock_ = new Object();
   protected ArrayList<ChannelSetting> channels_ = new ArrayList<ChannelSetting>();

   public Acquisition(double zStep, ArrayList<ChannelSetting> channels) throws Exception {
      xyStage_ = Magellan.getCore().getXYStageDevice();
      zStage_ = Magellan.getCore().getFocusDevice();
      channels_ = channels;
      //"postion" is not generic name..and as of right now there is now way of getting generic z positions
      //from a z deviec in MM
      String positionName = "Position";
       if (Magellan.getCore().hasProperty(zStage_, positionName)) {
           zStageHasLimits_ = Magellan.getCore().hasPropertyLimits(zStage_, positionName);
           if (zStageHasLimits_) {
               zStageLowerLimit_ = Magellan.getCore().getPropertyLowerLimit(zStage_, positionName);
               zStageUpperLimit_ = Magellan.getCore().getPropertyUpperLimit(zStage_, positionName);
           }
       }
      zStep_ = zStep;
      events_ = new LinkedBlockingQueue<AcquisitionEvent>(getAcqEventQueueCap());
   }
   
   public AcquisitionEvent getNextEvent() throws InterruptedException {
      synchronized (pauseLock_) {
         while (pause_) {
            pauseLock_.wait();
         }
      }   
      return events_.take();
   }

   public abstract double getRank();
   
   public abstract int getFilterType(); 
   
   public abstract int getAcqEventQueueCap();

   public MultiResMultipageTiffStorage getStorage() {
      return imageStorage_;
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
   public abstract double getZCoordinateOfSlice(int displaySliceIndex);

   public abstract int getSliceIndexFromZCoordinate(double z);

   /**
    * Return the maximum number of possible channels for the acquisition, not all of which are neccessarily active
    * @return 
    */
   public int getNumChannels() {
      return channels_.size();
   }
   
   public ArrayList<ChannelSetting> getChannels() {
      return channels_;
   }
   
   /**
    * Get initial number of frames (but this can change during acq)
    * @return 
    */
   public abstract int getInitialNumFrames();
   
   public abstract int getInitialNumSlicesEstimate();
   
   public boolean isFinished() {
      return finished_;
   }
   
   
   public abstract void abort();
   
   public void markAsFinished() {
      finished_ = true;
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
   
   public void waitUntilClosed() {
       imageSink_.waitToDie();
   }
   
   protected void initialize(String dir, String name, double overlapPercent) {
      engineOutputQueue_ = new LinkedBlockingQueue<MagellanTaggedImage>(OUTPUT_QUEUE_SIZE);
      overlapX_ = (int) (JavaLayerImageConstructor.getInstance().getImageWidth() * overlapPercent / 100);
      overlapY_ = (int) (JavaLayerImageConstructor.getInstance().getImageHeight() * overlapPercent / 100);
      JSONObject summaryMetadata = MagellanEngine.makeSummaryMD(this, name);
      imageStorage_ = new MultiResMultipageTiffStorage(dir, summaryMetadata,
              (this instanceof FixedAreaAcquisition)); //estimatye background pixel values for fixed acqs but not explore
      //storage class has determined unique acq name, so it can now be stored
      name_ = imageStorage_.getUniqueAcqName();
      MMImageCache imageCache = new MMImageCache(imageStorage_);
      imageCache.setSummaryMetadata(summaryMetadata);
      new DisplayPlus(imageCache, this, summaryMetadata, imageStorage_);         
      imageSink_ = new TaggedImageSink(engineOutputQueue_, imageCache, this);
      imageSink_.start();
   }
   
   public String getName() {
      return name_;
   }

   public double getZStep() {
      return zStep_;
   }

   public void addImage(MagellanTaggedImage img) {
      try {
         engineOutputQueue_.put(img);
      } catch (InterruptedException ex) {
         IJ.log("Acquisition engine thread interrupted");
      }
   }

   protected abstract JSONArray createInitialPositionList();

   public boolean isPaused() {
      return pause_; 
   }
   
   public void togglePaused() {
      pause_ = !pause_;
      synchronized (pauseLock_) {
         pauseLock_.notifyAll();
      }
   }

   
   public String[] getChannelNames() {
      String[] names = new String[channels_.size()];
      for (int i = 0; i < names.length; i++) {
         names[i] = channels_.get(i).name_;
      }
      return names; 
   }
   
    public Color[] getChannelColors() {
      Color[] colors = new Color[channels_.size()];
      for (int i = 0; i < colors.length; i++) {
         colors[i] = channels_.get(i).color_;
      }
      return colors;
    }

}
