package acq;

import coordinates.PositionManager;
import gui.SettingsDialog;
import ij.IJ;
import imagedisplay.DisplayPlus;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import bidc.CoreCommunicator;
import mmcorej.CMMCore;
import mmcorej.TaggedImage;
import org.json.JSONArray;
import org.json.JSONObject;
import org.micromanager.MMStudio;
import org.micromanager.utils.ReportingUtils;

/**
 * Abstract class that manages a generic acquisition. Subclassed into specific
 * types of acquisition
 */
public abstract class Acquisition implements AcquisitionEventSource{

   //max numberof images that are held in queue to be saved
   private static final int OUTPUT_QUEUE_SIZE = 40;
   
   protected final double zStep_;
   private BlockingQueue<TaggedImage> engineOutputQueue_;
   protected CMMCore core_ = MMStudio.getInstance().getCore();
   protected String xyStage_, zStage_;
   protected boolean zStageHasLimits_ = false;
   protected double zStageLowerLimit_, zStageUpperLimit_;
   protected PositionManager posManager_;
   protected BlockingQueue<AcquisitionEvent> events_;
   protected TaggedImageSink imageSink_;
   protected String pixelSizeConfig_;
   protected volatile boolean finished_ = false;
   private String name_;
   private long startTime_ms_ = -1;
   private MultiResMultipageTiffStorage imageStorage_;
   private int overlapX_, overlapY_;
   private volatile boolean pause_ = false;
   private Object pauseLock_ = new Object();

   public Acquisition(double zStep) throws Exception {
      xyStage_ = core_.getXYStageDevice();
      zStage_ = core_.getFocusDevice();
      //TODO: "postion" is not generic name??
      String positionName = "Position";
       if (core_.hasProperty(zStage_, positionName)) {
           zStageHasLimits_ = core_.hasPropertyLimits(zStage_, positionName);
           if (zStageHasLimits_) {
               zStageLowerLimit_ = core_.getPropertyLowerLimit(zStage_, positionName);
               zStageUpperLimit_ = core_.getPropertyUpperLimit(zStage_, positionName);
           }
       }
      zStep_ = zStep;
      events_ = new LinkedBlockingQueue<AcquisitionEvent>(getAcqEventQueueCap());
      pixelSizeConfig_ = core_.getCurrentPixelSizeConfig();
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

   //TODO: change this when number of channels acutally implemented
   public int getNumChannels() {
      return (int) MMStudio.getInstance().getCore().getNumberOfCameraChannels();
   }
   
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
   
   protected void initialize(String dir, String name, double overlapPercent) {
      engineOutputQueue_ = new LinkedBlockingQueue<TaggedImage>(OUTPUT_QUEUE_SIZE);
      overlapX_ = (int) (CoreCommunicator.getInstance().getImageWidth() * overlapPercent / 100);
      overlapY_ = (int) (CoreCommunicator.getInstance().getImageHeight() * overlapPercent / 100);
      JSONObject summaryMetadata = CustomAcqEngine.makeSummaryMD(this, name);
      imageStorage_ = new MultiResMultipageTiffStorage(dir, true, summaryMetadata, overlapX_, overlapY_, pixelSizeConfig_,
              (this instanceof FixedAreaAcquisition)); //estimatye background pixel values for fixed acqs but not explore
      //storage class has determined unique acq name, so it can now be stored
      name_ = imageStorage_.getUniqueAcqName();
      MMImageCache imageCache = new MMImageCache(imageStorage_);
      imageCache.setSummaryMetadata(summaryMetadata);
      posManager_ = imageStorage_.getPositionManager();      
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

   public PositionManager getPositionManager() {
      return posManager_;
   }

   public void addImage(TaggedImage img) {
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

}
