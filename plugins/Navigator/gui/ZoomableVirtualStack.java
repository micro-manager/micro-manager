package gui;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
import acq.DynamicStitchingImageStorage;
import java.awt.Point;
import java.awt.image.ColorModel;
import mmcorej.TaggedImage;
import org.micromanager.imagedisplay.AcquisitionVirtualStack;
import org.micromanager.imagedisplay.VirtualAcquisitionDisplay;
import org.micromanager.api.TaggedImageStorage;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author henrypinkard
 */
public class ZoomableVirtualStack extends AcquisitionVirtualStack {
      
   public static int DISPLAY_IMAGE_LENGTH_MAX = 800;
   
   private boolean zoomed_ = false;
   private int displayImageWidth_, displayImageHeight_;
   private double fullResXStart_ = 0, fullResYStart_ = 0;  
   private DynamicStitchingImageStorage storage_;
   
   public ZoomableVirtualStack(int type, TaggedImageStorage imageCache,
           int nSlices, VirtualAcquisitionDisplay vad, DynamicStitchingImageStorage storage) {
      super((int) Math.round(storage.getFullResWidth() / storage.getDSFactor()), 
              (int) Math.round(storage.getFullResHeight() / storage.getDSFactor()), 
              type, null, imageCache, nSlices, vad);
      
      storage_ = storage;
      displayImageWidth_ = (int) Math.round(storage.getFullResWidth() / storage.getDSFactor());
      displayImageHeight_ = (int) Math.round(storage.getFullResHeight() / storage.getDSFactor());
   }  
   
   public void translateZoomPosition(int dx, int dy) {
      fullResXStart_ = Math.min(Math.max(fullResXStart_ + dx, 0.0), storage_.getFullResWidth() - displayImageWidth_);
      fullResYStart_ = Math.min(Math.max(fullResYStart_ + dy, 0.0), storage_.getFullResHeight() - displayImageHeight_);
   }
   
   public void activateFullImageMode() {
      zoomed_ = false;
   }
   
   public void activateZoomMode(int fullResX, int fullResY) {
      zoomed_ = true;
      //reset to top left corner for now
      fullResXStart_ = Math.min(Math.max(fullResX*storage_.getDSFactor() - displayImageWidth_ / 2, 0.0), 
              storage_.getFullResWidth() - displayImageWidth_);
      fullResYStart_ = Math.min(Math.max(fullResY*storage_.getDSFactor() - displayImageHeight_ / 2, 0.0), 
              storage_.getFullResHeight() - displayImageHeight_);
   }
   
   public Point getZoomPosition() {
      return new Point((int)fullResXStart_,(int)fullResYStart_);
   }
   
   //this method is called to get the tagged image for display purposes only
   //return the zoomed or downsampled image here for fast performance
   @Override
   protected TaggedImage getTaggedImage(int channel, int slice, int frame) {
      //tags and images ultimately get split apart from this function, so it is okay
      //to alter image size and not change tags to reflect that
      if (zoomed_) {
         //return full res part of larger stitched image
         return storage_.getFullResStitchedSubImage(channel, slice, frame, 
                 (int) fullResXStart_, (int) fullResYStart_, displayImageWidth_, displayImageHeight_);
      } else {        
         //return downsampled full image
         return storage_.getImage(channel, slice, frame, 0);
      }
   }
   
}
