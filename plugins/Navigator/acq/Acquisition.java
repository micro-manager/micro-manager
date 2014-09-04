/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package acq;

import imagedisplay.DisplayPlus;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
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
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.ReportingUtils;

/**
 * Abstract class that manages a generic acquisition. Subclassed into specific types of acquisition
 */
public abstract class Acquisition {
   
   protected volatile double zStep_ = 1;
   protected BlockingQueue<TaggedImage> engineOutputQueue_;
   protected CMMCore core_ = MMStudio.getInstance().getCore();
   protected CustomAcqEngine eng_;
   protected String xyStage_, zStage_;
   protected PositionManager posManager_;


   public Acquisition(CustomAcqEngine eng, double zStep) {
      eng_ = eng;
      xyStage_ = core_.getXYStageDevice();
      zStage_ = core_.getFocusDevice();
      zStep_ = zStep;
   }
   
   public void initialize(int xOverlap, int yOverlap, String dir, String name) {

      //TODO: add limit to this queue in case saving and display goes much slower than acquisition?
      engineOutputQueue_ = new LinkedBlockingQueue<TaggedImage>();

      JSONObject summaryMetadata = makeSummaryMD(1, (int) core_.getNumberOfCameraChannels(), name);
//         JSONObject summaryMetadata = makeSummaryMD(1,2);
      MultiResMultipageTiffStorage storage = new MultiResMultipageTiffStorage(dir, true, summaryMetadata, xOverlap, yOverlap);
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
      new DisplayPlus(imageCache, this, summaryMetadata, storage, eng_.getRegionManager(),eng_.getSurfaceManager());

      DefaultTaggedImageSink sink = new DefaultTaggedImageSink(engineOutputQueue_, imageCache);
      sink.start();

   }
   
   /**
    * indices are 1 based and positive
    * @param sliceIndex - 
    * @param frameIndex - 
    * @return 
    */
   public abstract double getZCoordinateOfSlice(int displaySliceIndex, int displayFrameIndex);
   
   public abstract int getDisplaySliceIndexFromZCoordinate(double z, int displayFrameIndex);
   
   public double getZStep() {
      return zStep_;
   }
   
   public PositionManager getPositionManager() {
      return posManager_;
   }
   
   public void addEvent(AcquisitionEvent e) {
       eng_.addEvent(e);
   }
   
   public void addImage(TaggedImage img) {
      engineOutputQueue_.add(img);
   }

   public void finish() {
      engineOutputQueue_.add(new TaggedImage(null, null));
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

