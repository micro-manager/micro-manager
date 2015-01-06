package acq;

import coordinates.PositionManager;
import gui.SettingsDialog;
import imagedisplay.DisplayPlus;
import imagedisplay.DisplayPlusControls;
import imagedisplay.SubImageControls;
import java.awt.Color;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import javax.swing.JFrame;
import mmcloneclasses.acquisition.MMImageCache;
import imagedisplay.ContrastMetadataCommentsPanel;
import mmcorej.CMMCore;
import mmcorej.TaggedImage;
import org.json.JSONArray;
import org.json.JSONObject;
import org.micromanager.MMStudio;
import org.micromanager.acquisition.MMAcquisition;
import org.micromanager.utils.ReportingUtils;

/**
 * Abstract class that manages a generic acquisition. Subclassed into specific
 * types of acquisition
 */
public abstract class Acquisition {

   protected volatile double zStep_ = 1;
   protected BlockingQueue<TaggedImage> engineOutputQueue_;
   protected CMMCore core_ = MMStudio.getInstance().getCore();
   protected String xyStage_, zStage_;
   protected PositionManager posManager_;
   protected BlockingQueue<AcquisitionEvent> events_;
   protected TaggedImageSink imageSink_;
   protected String pixelSizeConfig_;
   protected volatile boolean finished_ = false;


   public Acquisition(double zStep) {
      xyStage_ = core_.getXYStageDevice();
      zStage_ = core_.getFocusDevice();
      zStep_ = zStep;
      events_ = new LinkedBlockingQueue<AcquisitionEvent>();
      try {
         pixelSizeConfig_ = MMStudio.getInstance().getCore().getCurrentPixelSizeConfig();
      } catch (Exception ex) {
         ReportingUtils.showError("couldnt get pixel size config");
      }
   }

   /**
    * indices are 1 based and positive
    *
    * @param sliceIndex -
    * @param frameIndex -
    * @return
    */
   public abstract double getZCoordinateOfSlice(int displaySliceIndex, int displayFrameIndex);

   public abstract int getDisplaySliceIndexFromZCoordinate(double z, int displayFrameIndex);

   public boolean isFinished() {
      return finished_;
   }
   
   public abstract void abort();
   
  
   public void finish() {
      finished_ = true;
   }
   
   protected void initialize(String dir, String name) {
      int xOverlap = SettingsDialog.getOverlapX();
      int yOverlap = SettingsDialog.getOverlapY();

      //TODO: add limit to this queue in case saving and display goes much slower than acquisition?
      engineOutputQueue_ = new LinkedBlockingQueue<TaggedImage>();

      JSONObject summaryMetadata = makeSummaryMD(1, (int) core_.getNumberOfCameraChannels(), name);
//         JSONObject summaryMetadata = makeSummaryMD(1,2);
      MultiResMultipageTiffStorage storage = new MultiResMultipageTiffStorage(dir, true, summaryMetadata, xOverlap, yOverlap, pixelSizeConfig_);
      MMImageCache imageCache = new MMImageCache(storage);
      imageCache.setSummaryMetadata(summaryMetadata);
      posManager_ = storage.getPositionManager();
      
      DisplayPlus disp = new DisplayPlus(imageCache, this, summaryMetadata, storage);
         
      imageSink_ = new TaggedImageSink(engineOutputQueue_, imageCache, this);
      imageSink_.start();
   }
   
   public BlockingQueue<AcquisitionEvent> getEventQueue() {
      return events_;
   }

   public double getZStep() {
      return zStep_;
   }

   public PositionManager getPositionManager() {
      return posManager_;
   }

   public void addEvent(AcquisitionEvent e) {
      events_.add(e);
   }

   public void addImage(TaggedImage img) {
      engineOutputQueue_.add(img);
   }

   protected abstract JSONArray createInitialPositionList();

   //to be removed if this is factored out of acq engine in micromanager
   //TODO: mkae sure Prefix is in new summary MD
   private JSONObject makeSummaryMD(int numSlices, int numChannels, String prefix) {
      try {
         if (SettingsDialog.getDemoMode()) {
            numChannels = SettingsDialog.getDemoNumChannels();
         }
         
         CMMCore core = MMStudio.getInstance().getCore();
         JSONObject summary = new JSONObject();
         summary.put("Slices", numSlices);
         //TODO: set slices to maximum number given the z device so that file size is overestimated
         summary.put("Channels", numChannels);
         summary.put("Frames", 1);
         summary.put("SlicesFirst", true);
         summary.put("TimeFirst", false);
         summary.put("PixelType", "GRAY8");
         summary.put("BitDepth", 8);
         summary.put("Width", core.getImageWidth());
         summary.put("Height", core.getImageHeight());
         summary.put("Prefix", prefix);
         JSONArray initialPosList = createInitialPositionList();
         summary.put("InitialPositionList", initialPosList);
         summary.put("Positions", initialPosList);


         JSONArray chNames = new JSONArray();
         JSONArray chColors = new JSONArray();
         for (int i = 0; i < numChannels; i++) {
            if (SettingsDialog.getDemoMode()) {
               String[] names = {"Violet", "Blue", "Green", "Yellow", "Red", "Far red"};
               int[] colors = {new Color(127,0,255).getRGB(), Color.blue.getRGB(), Color.green.getRGB(), 
                  Color.yellow.getRGB(), Color.red.getRGB(), Color.pink.getRGB()};
               chNames.put(names[i]);
               chColors.put(colors[i]);
            } else {
               chNames.put(core.getCameraChannelName(i));
               chColors.put(MMAcquisition.getMultiCamDefaultChannelColor(i, core.getCameraChannelName(i)));
            }
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
