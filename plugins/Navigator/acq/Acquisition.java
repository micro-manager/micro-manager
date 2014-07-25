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
import org.micromanager.MMStudioMainFrame;
import org.micromanager.acquisition.DefaultTaggedImageSink;
import org.micromanager.acquisition.MMAcquisition;
import org.micromanager.acquisition.MMImageCache;
import org.micromanager.api.ImageCache;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author Henry
 */
public class Acquisition {

   //TODO: delete
   private static final String STORAGE_DIR = "C:/Users/Henry/Desktop/MultiRes/";
   
   private final boolean explore_;
   private volatile double zTop_, zBottom_, zStep_ = 1, zAbsoluteTop_ = 0, zAbsoluteBottom_ = 50;
   private BlockingQueue<TaggedImage> engineOutputQueue_;
   private BlockingQueue<AcquisitionEvent> events_;
   private CMMCore core_ = MMStudioMainFrame.getInstance().getCore();
   private CustomAcqEngine eng_;
   private String xyStage_, zStage_;
   private Thread acquisitionThread_;
   private PositionManager posManager_;

   /*
    * Class that sets up thread to run acquisition and 
    */
   public Acquisition(boolean explore, CustomAcqEngine eng, double zTop, double zBottom, double zStep) {
      eng_ = eng;
      explore_ = explore;
      zTop_ = zTop;
      zBottom_ = zBottom;
      zStep_ = zStep;
      xyStage_ = core_.getXYStageDevice();
      zStage_ = core_.getFocusDevice();
      if (explore) {
         //TODO: add limit to this queue in case saving and display goes much slower than acquisition?
         engineOutputQueue_ = new LinkedBlockingQueue<TaggedImage>();

         JSONObject summaryMetadata = makeSummaryMD(1,(int)core_.getNumberOfCameraChannels());
//         JSONObject summaryMetadata = makeSummaryMD(1,2);
         MultiResMultipageTiffStorage storage = new MultiResMultipageTiffStorage(STORAGE_DIR, true, summaryMetadata, 0, 0);
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
   
   public PositionManager getPositionManager() {
      return posManager_;
   }
   
   public void addEvent(AcquisitionEvent e) {
       events_.add(e); 
   }
  
   public double getZTop() {
      return zTop_;
   }
   
   public double getZBottom() {
      return zBottom_;
   }
   
   public double getZStep() {
      return zStep_;
   }
   
   public double getZAbsoluteTop() {
      return zAbsoluteTop_;
   }
   
   public double getZAbsoluteBottom() {
      return zAbsoluteBottom_;
   }
   
   public void setZLimits(double zTop, double zBottom) {
      zTop_ = zTop;
      zBottom_ = zBottom;
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
   public static JSONObject makeSummaryMD(int numSlices, int numChannels) {
      try {
         CMMCore core = MMStudioMainFrame.getInstance().getCore();
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
         summary.put("Prefix", "ExplorerTest");
         summary.put("Directory", "C:/Users/Henry/Desktop/MultiRes/"); //TODO: delete this

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

