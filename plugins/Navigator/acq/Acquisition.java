/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package acq;

import gui.DisplayPlus;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import mmcorej.CMMCore;
import mmcorej.TaggedImage;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.MMStudio;
import org.micromanager.acquisition.DefaultTaggedImageSink;
import org.micromanager.acquisition.MMAcquisition;
import org.micromanager.acquisition.MMImageCache;
import org.micromanager.api.ImageCache;
import org.micromanager.utils.JavaUtils;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author Henry
 */
public class Acquisition {
   
   private final boolean explore_;
   private final double zOrigin_;
   private volatile double zTop_, zBottom_, zStep_ = 1, zAbsoluteTop_ = 0, zAbsoluteBottom_ = 50;
   private BlockingQueue<TaggedImage> engineOutputQueue_;
   private BlockingQueue<AcquisitionEvent> events_;
   private CMMCore core_ = MMStudio.getInstance().getCore();
   private CustomAcqEngine eng_;
   private String xyStage_, zStage_;
   private Thread acquisitionThread_;
   private PositionManager posManager_;
   private int lowestSliceIndex_ = 0, highestSliceIndex_ = 0;

   /*
    * Class that sets up thread to run acquisition and 
    */
   public Acquisition(boolean explore, CustomAcqEngine eng, double zTop, double zBottom, double zStep) {
      eng_ = eng;
      explore_ = explore;
      double zPos = 0;
      try {
         zPos = core_.getPosition(core_.getFocusDevice());
      } catch (Exception ex) {
         ReportingUtils.showError("Couldn't get z positon from core");
      }
      zOrigin_ = zTop; //used for assigning slice indexing
      zTop_ = zTop;
      zBottom_ = zBottom;      
      zStep_ = zStep;
   }
   
   public void initialize(int xOverlap, int yOverlap, String dir, String name) {
      xyStage_ = core_.getXYStageDevice();
      zStage_ = core_.getFocusDevice();
      if (explore_) {
         //TODO: add limit to this queue in case saving and display goes much slower than acquisition?
         engineOutputQueue_ = new LinkedBlockingQueue<TaggedImage>();

         JSONObject summaryMetadata = makeSummaryMD(1,(int)core_.getNumberOfCameraChannels(), name);
//         JSONObject summaryMetadata = makeSummaryMD(1,2);
         MultiResMultipageTiffStorage storage = new MultiResMultipageTiffStorage(dir,
                 true, summaryMetadata, xOverlap, yOverlap);
         ImageCache imageCache = new MMImageCache(storage) {
            @Override
            //TODO: is this needed?
            public JSONObject getLastImageTags() {
               //So that display doesnt show a position scrollbar when imaging finished
               JSONObject newTags = null;
               try {
                  newTags = new JSONObject(super.getLastImageTags().toString());
                  MDUtils.setPositionIndex(newTags, 0);
               } catch (JSONException ex) {
                  ReportingUtils.showError("Unexpected JSON Error");
               }
               return newTags;
            }
         };
         imageCache.setSummaryMetadata(summaryMetadata);

         posManager_ = storage.getPositionManager();
         new DisplayPlus(imageCache, eng_, summaryMetadata,storage,true);

         DefaultTaggedImageSink sink = new DefaultTaggedImageSink(engineOutputQueue_, imageCache);
         sink.start();
         
         events_ = new LinkedBlockingQueue<AcquisitionEvent>();
         
         acquisitionThread_ = new Thread(new Runnable() {
               @Override
               public void run() {
                  while (!Thread.interrupted()) {
                     if (events_.size() > 0) {
                        eng_.runEvent(events_.poll());
                     } else {
                        try {
                           //wait for more events to acquire
                           Thread.sleep(5);
                        } catch (InterruptedException ex) {
                           Thread.currentThread().interrupt();
                        }
                     }
                  }
               }
            }, "Explorer acquisition thread");
            acquisitionThread_.start();
         
         
      } else {

      //TODO  non explore acquisition
      }
   }
   
   /**
    * return the slice index of the lowest slice seen in this acquisition
    * @return 
    */
   public int getLowestSliceIndex() {
      return lowestSliceIndex_;
   }
   
   public int getHighestSliceIndex() {
      return highestSliceIndex_;
   }
   
   public void updateLowestAndHighestSlices() {
      //keep track of this for the purposes of the viewer
      lowestSliceIndex_ = Math.min(lowestSliceIndex_, getMinSliceIndex());
      highestSliceIndex_ = Math.max(highestSliceIndex_, getMaxSliceIndex());
   }
   
   /**
    * get min slice index for current settings in explore acquisition
    * @return 
    */
   public int getMinSliceIndex() {
      return (int) ((zTop_ - zOrigin_) / zStep_);
   }
   
   /**
    * get max slice index for current settings in explore acquisition
    * @return 
    */
   public int getMaxSliceIndex() {
      return (int) ((zBottom_ - zOrigin_) / zStep_);
   }

   /**
    * get z coordinate for slice position
    * @return 
    */
   public double getZCoordinate(int sliceIndex) {
      return zOrigin_ + zStep_ * sliceIndex;
   }
   
   public PositionManager getPositionManager() {
      return posManager_;
   }
   
   public void addEvent(AcquisitionEvent e) {
       events_.add(e); 
   }
  
   public void setZLimits(double zTop, double zBottom) {
      //Convention: z top should always be lower than zBottom
      zBottom_ = Math.max(zTop, zBottom);
      zTop_ = Math.min(zTop, zBottom);     
   }
   
   public void addImage(TaggedImage img) {
      engineOutputQueue_.add(img);
   }

   public void finish() {
      if (explore_) {
          acquisitionThread_.interrupt();
          engineOutputQueue_.add(new TaggedImage(null, null));
      } else {
         //TODO: non explore acquisitions
      }
   }
   
      //to be removed once this is factored out of acq engine in micromanager
   //TODO: mkae sure Prefix is in new summary MD
   public static JSONObject makeSummaryMD(int numSlices, int numChannels, String prefix) {
      try {
         CMMCore core = MMStudio.getInstance().getCore();
         JSONObject summary = new JSONObject();
         summary.put("Slices", numSlices);
         //TODO: set slices to maximum number given the z device so that file size is overestimated
         summary.put("Positions", 100);
         summary.put("Channels", numChannels);
         summary.put("Frames", 1);
         summary.put("SlicesFirst", true);
         summary.put("TimeFirst", false);
         summary.put("PixelType", "GRAY8");
         summary.put("BitDepth", 8);
         summary.put("Width", core.getImageWidth());
         summary.put("Height", core.getImageHeight());
         summary.put("Prefix", prefix);

         //make intitial position list, with current position and 0,0 as coordinates
         JSONArray pList = new JSONArray();
         //create first position based on current XYStage position
         JSONObject coordinates = new JSONObject();
         JSONArray xy = new JSONArray();
         xy.put(core.getXPosition(core.getXYStageDevice()));
         xy.put(core.getYPosition(core.getXYStageDevice()));
         coordinates.put(core.getXYStageDevice(),xy );
         JSONObject pos = new JSONObject();
         pos.put("DeviceCoordinatesUm", coordinates);
         //first position is 1,1
         pos.put("GridColumnIndex", 0);
         pos.put("GridRowIndex", 0);   
         pos.put("Properties",new JSONObject());
         pList.put(pos);             
         summary.put("InitialPositionList", pList);

         
          JSONArray chNames = new JSONArray();
          JSONArray chColors = new JSONArray();
          for (int i = 0; i < core.getNumberOfCameraChannels(); i++) {
              chNames.put(core.getCameraChannelName(i));
              chColors.put(MMAcquisition.getMultiCamDefaultChannelColor(i, core.getCameraChannelName(i)));
          }
          summary.put("ChNames", chNames);
          summary.put("ChColors", chColors);
         
         
         //write pixel overlap into metadata
//         summary.put("GridPixelOverlapX", SettingsDialog.getXOverlap());
//         summary.put("GridPixelOverlapY", SettingsDialog.getYOverlap());
         return summary;
      } catch (Exception ex) {
         ReportingUtils.showError("couldnt make summary metadata");
      }
      return null;
   }
   
   
}

