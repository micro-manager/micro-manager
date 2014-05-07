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

   private final boolean explore_;
   private volatile double zTop_, zBottom_, zStep_ = 1, zAbsoluteTop_ = 0, zAbsoluteBottom_ = 50;
   private BlockingQueue<TaggedImage> engineOutputQueue_;
   private BlockingQueue<AcquisitionEvent> events_;
   private DynamicStitchingImageStorage storage_;
   private CMMCore core_ = MMStudioMainFrame.getInstance().getCore();
   private CustomAcqEngine eng_;
   private String xyStage_, zStage_;
   private Thread acquisitionThread_;

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
         storage_ = new DynamicStitchingImageStorage(summaryMetadata, true);
         ImageCache imageCache = new MMImageCache(storage_) {
            @Override
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

            @Override
            public void close() {
               finish();
               super.close();
            }
         };
         imageCache.setSummaryMetadata(summaryMetadata);

         new DisplayPlus(imageCache, eng_, summaryMetadata, storage_, true);

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

      //TODO   
      }
      
      
      
   }
   
   public void addEvent(AcquisitionEvent e) {
       events_.add(e); 
   }
  
   public double getXPosition(int posIndex) throws JSONException {
      return getPositionList().getJSONObject(posIndex).getJSONObject("DeviceCoordinatesUm").getJSONArray(xyStage_).getDouble(0);
   }

   public double getYPosition(int posIndex) throws JSONException {
      return getPositionList().getJSONObject(posIndex).getJSONObject("DeviceCoordinatesUm").getJSONArray(xyStage_).getDouble(1);
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
   
   public int[] addPositionsIfNeeded(JSONObject[] newPositions) throws JSONException {
      return storage_.addPositionsIfNeeded(newPositions);
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

   public JSONArray getPositionList() {
      return storage_.getPositionList();
   }
   
   
      //to be removed once this is factored out of acq engine in micromanager
   private JSONObject makeSummaryMD(int numSlices, int numChannels) {
      try {
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
         summary.put("Width", core_.getImageWidth());
         summary.put("Height", core_.getImageHeight());
         summary.put("Prefix", "ExplorerTest");
         summary.put("Directory", DynamicStitchingImageStorage.HI_RES_SAVING_DIR);

         //make intitial position list
         JSONArray pList = new JSONArray();
         //create first position based on current XYStage position
         JSONObject coordinates = new JSONObject();
         JSONArray xy = new JSONArray();
         xy.put(core_.getXPosition(core_.getXYStageDevice()));
         xy.put(core_.getYPosition(core_.getXYStageDevice()));
         coordinates.put(core_.getXYStageDevice(),xy );
         JSONObject pos = new JSONObject();
         pos.put("DeviceCoordinatesUm", coordinates);
         //first position is 1,1
         pos.put("GridColumnIndex", 1);
         pos.put("GridRowIndex", 1);          
         pList.put(pos);
         
          JSONArray chNames = new JSONArray();
          JSONArray chColors = new JSONArray();
          for (int i = 0; i < core_.getNumberOfCameraChannels(); i++) {
              chNames.put(core_.getCameraChannelName(i));
              chColors.put(MMAcquisition.getMultiCamDefaultChannelColor(i, core_.getCameraChannelName(i)));
          }
          summary.put("ChNames", chNames);
          summary.put("ChColors", chColors);
         
         summary.put("InitialPositionList", pList);
         
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

