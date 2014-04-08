/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package MMCustomization;

import java.awt.Point;
import java.awt.image.ColorModel;
import mmcorej.TaggedImage;
import org.micromanager.acquisition.AcquisitionVirtualStack;
import org.micromanager.acquisition.VirtualAcquisitionDisplay;
import org.micromanager.api.TaggedImageStorage;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author henrypinkard
 */
public class ZoomableVirtualStack extends AcquisitionVirtualStack {
   
   //width and height are both fixed for now
   public static final int WIDTH_HEIGHT_MAX = 512;
   
   private boolean zoomed_ = false;
   private TaggedImageStorage imageCache_;
   private int displayImageWidth_, displayImageHeight_;
   private int downsampleFactor_;
   private int fullResWidth_, fullResHeight_; 
   private double fullResXStart_ = 0, fullResYStart_ = 0;  
   private DynamicStitchingImageStorage storage_;
   
   public ZoomableVirtualStack(int fullResWidth, int fullResHeight, int type, TaggedImageStorage imageCache,
           int nSlices, VirtualAcquisitionDisplay vad, DynamicStitchingImageStorage storage) {
      super(fullResWidth / Math.max(1, Math.max(fullResWidth, fullResHeight) / WIDTH_HEIGHT_MAX), 
              fullResHeight / Math.max(1, Math.max(fullResWidth, fullResHeight) / WIDTH_HEIGHT_MAX), 
              type, null, imageCache, nSlices, vad);
      downsampleFactor_ = Math.max(1, Math.max(fullResWidth, fullResHeight) / WIDTH_HEIGHT_MAX);
      displayImageWidth_ = fullResWidth / downsampleFactor_;
      displayImageHeight_ = fullResHeight / downsampleFactor_;
      imageCache_ = imageCache;
      fullResWidth_ = fullResWidth;
      fullResHeight_ = fullResHeight;
      storage_ = storage;
   }  
   
   public void translateZoomPosition(int dx, int dy) {
      fullResXStart_ = Math.min(Math.max(fullResXStart_ + dx, 0.0), fullResWidth_ - displayImageWidth_);
      fullResYStart_ = Math.min(Math.max(fullResYStart_ + dy, 0.0), fullResHeight_ - displayImageHeight_);
   }
   
   public void activateFullImageMode() {
      zoomed_ = false;
   }
   
   public void activateZoomMode(int fullResX, int fullResY) {
      zoomed_ = true;
      //reset to top left corner for now
      fullResXStart_ = Math.min(Math.max(fullResX*downsampleFactor_ - displayImageWidth_ / 2, 0.0), fullResWidth_ - displayImageWidth_);
      fullResYStart_ = Math.min(Math.max(fullResY*downsampleFactor_ - displayImageHeight_ / 2, 0.0), fullResHeight_ - displayImageHeight_);
   }
   
   public int getDownsampleFactor() {
      return downsampleFactor_;
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