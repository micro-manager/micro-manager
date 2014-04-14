/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package MMCustomization;

import com.imaging100x.twophoton.SettingsDialog;
import java.util.concurrent.ArrayBlockingQueue;
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
import org.micromanager.acquisition.MMImageCache;
import org.micromanager.api.ImageCache;
import org.micromanager.api.MMTags;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.MMScriptException;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author henrypinkard
 */
public class CustomAcqEngine {
   
   private CMMCore core_;
   private BlockingQueue<TaggedImage> engineOutputQueue_;
   private DynamicStitchingImageStorage currentExploreStorage_;
   private DisplayPlus currentExploreDisplay_;
   private double zTop_ = 0, zBottom_ = 5, zStep_ = 1;
   private int sliceIndex_, positionIndex_, frameIndex_, channelIndex_;
   private int numSlices_;

   public CustomAcqEngine(CMMCore core) {
      core_ = core;
   }
   
   public void newExploreWindow() {
      new Thread(new Runnable() {
         @Override
         public void run() {
            try {
               createExploreAcquisition();
            } catch (MMScriptException ex) {
               ex.printStackTrace();
               ReportingUtils.showError("couldn't run acquisition");
            }
         }
      }).start();
   }

   public void acquireTile(final int row, final int col) {
      new Thread(new Runnable() {
         @Override
         public void run() {
            try {
               positionIndex_ = currentExploreStorage_.addPositionIfNeeded(createPosition(row, col));
               acquireZStack();
            } catch (Exception e) {
               ReportingUtils.showError("Couldn't acquire tile");
            }
         }
      }).start();
   }
   
   
   
   public void createExploreAcquisition() throws MMScriptException {
      // Start up the acquisition engine
      engineOutputQueue_ = new LinkedBlockingQueue<TaggedImage>();

      numSlices_ = (int) ((zBottom_ - zTop_) / zStep_); 
      JSONObject summaryMetadata = makeSummaryMD();

      // Set up the DataProcessor<TaggedImage> sequence--no data processors for now
      // BlockingQueue<TaggedImage> procStackOutputQueue = ProcessorStack.run(engineOutputQueue, imageProcessors);


      currentExploreStorage_ = new DynamicStitchingImageStorage(summaryMetadata);

      ImageCache imageCache = new MMImageCache(currentExploreStorage_) {
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
      };
      imageCache.setSummaryMetadata(summaryMetadata);

      currentExploreDisplay_ = new DisplayPlus(imageCache, this, summaryMetadata, currentExploreStorage_, true);
      

      DefaultTaggedImageSink sink = new DefaultTaggedImageSink(engineOutputQueue_, imageCache);
      sink.start();
      
      
      //acquire images and send them to output queue
      positionIndex_ = 0;
      acquireZStack();
      
      //Poison image to end sink
//      engineOutputQueue_.add(new TaggedImage(null, null));
   }
   
   private void acquireImage() {
      try {
         core_.snapImage();
         //send to storage
         TaggedImage img = core_.getTaggedImage();
         //add tags
         img.tags.put(MMTags.Image.POS_INDEX, positionIndex_);
         img.tags.put(MMTags.Image.SLICE_INDEX, sliceIndex_);
         img.tags.put(MMTags.Image.FRAME_INDEX, frameIndex_);
         img.tags.put(MMTags.Image.CHANNEL_INDEX, channelIndex_);
         //TODO: add elapsed time tag
         engineOutputQueue_.add(img);
    
      } catch (Exception ex) {
         ReportingUtils.showError("Couldn't acquire Z stack");
      }
   }

   public void acquireZStack() {
      try {
         for (sliceIndex_ = 0; sliceIndex_ < numSlices_; sliceIndex_++) {
            //move focus
            core_.setPosition(core_.getFocusDevice(), zTop_ + sliceIndex_ * zStep_);
            //acquire image      
            acquireImage();
         }
      } catch (Exception ex) {
         ReportingUtils.showError("Couldn't acquire Z stack");
      }
   }

//   public void moveStage(double x, double y) {
//      try {
//         core_.setXYPosition(core_.getXYStageDevice(), x, y);
//      } catch (Exception ex) {
//         ReportingUtils.showError("Couldn't move XY stage");
//      }
//   }

   //TODO: actual coordinates 
   private JSONObject createPosition(long row, long col) {
      try {
         JSONArray xy = new JSONArray();
         xy.put(core_.getXPosition(core_.getXYStageDevice()));
         xy.put(core_.getYPosition(core_.getXYStageDevice()));
         JSONObject pos = new JSONObject();
         pos.put("DeviceCoordinatesUm", xy);
         pos.put("GridColumnIndex", col);
         pos.put("GridRowIndex", row);
         return pos;
      } catch (Exception e) {
         ReportingUtils.showError("Couldn't create XY position");
         return null;
      }
   }

   //to be removed once this is factored out of acq engine in micromanager
   private JSONObject makeSummaryMD() {
      try {
         JSONObject summary = new JSONObject();
         summary.put("Slices", numSlices_);
         //TODO: set slices to maximum number given the z device so that file size is overestimated
         summary.put("Positions", 100);
         summary.put("Channels", 1);
         summary.put("Frames", 1);
         summary.put("SlicesFirst", true);
         summary.put("TimeFirst", false);
         summary.put("PixelType", "GRAY8");
         summary.put("BitDepth", 8);
         summary.put("Width", core_.getImageWidth());
         summary.put("Height", core_.getImageHeight());
         summary.put("Prefix", "ExplorerTest");
         summary.put("Directory", "/Users/henrypinkard/Desktop/datadump");

         //make intitial position list
         JSONArray pList = new JSONArray();

         
         pList.put(createPosition(0,0));
         
         summary.put("InitialPositionList", pList);
         
         //write pixel overlap into metadata
         summary.put("GridPixelOverlapX", SettingsDialog.getXOverlap());
         summary.put("GridPixelOverlapY", SettingsDialog.getYOverlap());
         return summary;
      } catch (Exception ex) {
         ReportingUtils.showError("couldnt make summary metadata");
      }
      return null;
   }
}
