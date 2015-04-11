/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package imageconstruction;

import acq.Acquisition;
import acq.AcquisitionEvent;
import acq.CustomAcqEngine;
import demo.DemoModeImageData;
import gui.SettingsDialog;
import ij.IJ;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Level;
import java.util.logging.Logger;
import misc.GlobalSettings;
import mmcorej.CMMCore;
import mmcorej.TaggedImage;
import org.json.JSONException;
import org.micromanager.MMStudio;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.ReportingUtils;

/**
 * Means for plugin classes to access the core, to support tricky things
 */
public class CoreCommunicator {

   
   private static final int IMAGE_CONSTRUCTION_QUEUE_SIZE = 200;
   
   private static CMMCore core_ = MMStudio.getInstance().getCore();
   private LinkedBlockingQueue<ImageAndInfo> imageConstructionQueue_ = new LinkedBlockingQueue<ImageAndInfo>(IMAGE_CONSTRUCTION_QUEUE_SIZE);
   private ExecutorService imageConstructionExecutor_ = Executors.newSingleThreadExecutor(new ThreadFactory() {
      @Override
      public Thread newThread(Runnable r) {
         return new Thread(r, "Image construction thread");
      }
   });
   
      //TODO:
   //0) change camera files
   //1) Do deinterlacing in Java
   //3) try summing edge pixels to alleviate flat fielding (compare # pixels tosses to linescan from flat field slide)
   //   -but you would hve to subtract the offset?
   //5) put images into circular buffer when in a certain mode
   
  
   
   public CoreCommunicator() {
      //start up image construction thread
      imageConstructionExecutor_.submit(new Runnable() {

         @Override
         public void run() {
            try {
               ImageAndInfo iai = imageConstructionQueue_.take();
               TaggedImage constructedImage = null;
               //substitute in dummy pixel data for demo mode
               if (GlobalSettings.getDemoMode()) {
                  constructedImage = makeDemoImage(iai);
               } else {
                  int numFrames = 1;
                  try {
                     numFrames = (int) core_.getExposure();
                  } catch (Exception e) {
                     IJ.log("Couldnt get exposure form core");
                  }
                  //construct image from double wide
                  FrameIntegrationMethod integrator;
                  if (iai.event_.acquisition_.getFilterType() == FrameIntegrationMethod.FRAME_AVERAGE) {
                     integrator = new FrameAverageWrapper(GlobalSettings.getChannelOffset(iai.camChannelIndex_),
                             MDUtils.getWidth(iai.img_.tags), numFrames);
                  } else {
                     integrator = new RankFilterWrapper(GlobalSettings.getChannelOffset(iai.camChannelIndex_),
                             MDUtils.getWidth(iai.img_.tags), numFrames, iai.event_.acquisition_.getRank());
                  }

                  TaggedImage firstImage;
                  try {

                     
                     //construct image
                     constructedImage = new TaggedImage(integrator.constructImage(), firstImage.tags);
                  } catch (Exception ex) {
                     IJ.log("Couldn't read iamge from circular buffer");
                  }
               }
               //add metadata
               CustomAcqEngine.addImageMetadata(constructedImage, iai.event_, iai.numCamChannels_,
                       iai.camChannelIndex_, iai.currentTime_ - iai.event_.acquisition_.getStartTime_ms());
               //add to acq for display/saving 
               iai.event_.acquisition_.addImage(constructedImage);
            } catch (InterruptedException ex) {
               IJ.log("Unexpected interrupt of image construction thread! Ignoring...");
            } catch (JSONException e) {
               IJ.log("Missing Image width tag!");
            }
         }
      });
   }

   public static int getImageWidth() {
      return (int) core_.getImageWidth();
   }

   public static int getImageHeight() {
      return (int) core_.getImageHeight();
   }

   public static void snapImage() throws Exception {
      core_.snapImage();
   }
   
   /**
    * need to do this through core communicator so it occurs on the same thread as image construction
    */
   public static void addSignalTaggedImage(Acquisition acq, TaggedImage img) {
      asdfg
      //mak sure to put in queu rather than take
   }

   /**
    * Intercept calls to get tagged image so image can be created in java layer
    * Grab raw images from core and insert them into image construction executor
    * for processing return immediately if theres space in processing queue so acq can continue, otherwise
    * block until space opens up so acq doesnt try to go way faster than images can be constructed
    */
   public static void getTaggedImageAndAddToAcq(int cameraChannelIndex, AcquisitionEvent event, 
           final long currentTime, final int numCamChannels) throws Exception {


      //TODO: or use getLastTaggedImage(in cam channel index)???
      firstImage = core_.getNBeforeLastTaggedImage(numFrames * iai.numCamChannels_ - 1 + iai.camChannelIndex_);
      //add first frame
      integrator.addBuffer((byte[]) firstImage.pix);
      //add all frames
      for (int i = 0; i < numFrames; i++) {
         integrator.addBuffer((byte[]) core_.getNBeforeLastTaggedImage(i).pix);
      }
      //TODO: alter more first image tags, like bit and byte depth if doing frame integration
      MDUtils.setWidth(firstImage.tags, integrator.getConstructedImageWidth());
      MDUtils.setHeight(firstImage.tags, integrator.getConstructedImageHeight());

      //add images on channel after another 
      
   }
  
//   public static void main(String[] args) {
////      int[] lut = getCosineWarpLUT(1400);
//      
//      
//      
//      System.out.println();
//   }

   private TaggedImage makeDemoImage(ImageAndInfo iai) {
      Object demoPix;
      try {
         if (core_.getBytesPerPixel() == 1) {
            demoPix = DemoModeImageData.getBytePixelData(iai.camChannelIndex_, (int) iai.event_.xyPosition_.getCenter().x,
                    (int) iai.event_.xyPosition_.getCenter().y, (int) iai.event_.zPosition_, MDUtils.getWidth(iai.img_.tags), MDUtils.getHeight(iai.img_.tags));
         } else {
            demoPix = DemoModeImageData.getShortPixelData(iai.camChannelIndex_, (int) iai.event_.xyPosition_.getCenter().x,
                    (int) iai.event_.xyPosition_.getCenter().y, (int) iai.event_.zPosition_, MDUtils.getWidth(iai.img_.tags), MDUtils.getHeight(iai.img_.tags));
         }
         return new TaggedImage(demoPix, iai.img_.tags);
      } catch (Exception e) {
         e.printStackTrace();
         ReportingUtils.showError("Problem getting demo data");
         throw new RuntimeException();
      }

   }

   class ImageAndInfo {
      
      TaggedImage img_;
      AcquisitionEvent event_;
      int numCamChannels_;
      int camChannelIndex_;
      long currentTime_;

      public ImageAndInfo(TaggedImage img, AcquisitionEvent e, int numCamChannels, int cameraChannelIndex, long currentTime) {
         img_ = img;
         event_ = e;
         numCamChannels_ = numCamChannels;
         camChannelIndex_ = cameraChannelIndex;
         currentTime_ = currentTime;
      }

   }
   
}
