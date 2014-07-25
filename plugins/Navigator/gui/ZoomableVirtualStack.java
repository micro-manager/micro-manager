package gui;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
import acq.MultiResMultipageTiffStorage;
import acq.PositionManager;
import java.awt.Point;
import java.awt.image.ColorModel;
import mmcorej.TaggedImage;
import org.micromanager.imagedisplay.AcquisitionVirtualStack;
import org.micromanager.imagedisplay.VirtualAcquisitionDisplay;
import org.micromanager.api.TaggedImageStorage;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.ReportingUtils;

/**
 * This class acts as an intermediary between display and multiresolution storage.
 * Since the number of pixels in the viewer does not change, this class should track,
 * the zoom level and the part of the larger stitched image that is showing, and
 * deliver appropriate images to the viewer as such
 * @author henrypinkard
 */
public class ZoomableVirtualStack extends AcquisitionVirtualStack {
      
   public static int DISPLAY_IMAGE_LENGTH_MAX = 800;
   
   private int downsampleIndex_ = 0;
   private int displayImageWidth_, displayImageHeight_;
   private double fullResXStart_ = 0, fullResYStart_ = 0;  
   private MultiResMultipageTiffStorage multiResStorage_;
   
   public ZoomableVirtualStack(int type, int width, int height, TaggedImageStorage imageCache,
           int nSlices, VirtualAcquisitionDisplay vad, MultiResMultipageTiffStorage multiResStorage) {
      super(width, height,type, null, imageCache, nSlices, vad);
      multiResStorage_ = multiResStorage;
      //display image could conceivably be bigger than a single FOV, but not smaller
      displayImageWidth_ = width;
      displayImageHeight_ = height;
   }  
   
   public int getDownsampleIndex() {
      return downsampleIndex_;
   }
   
   public int getDownsampleFactor() {
      return (int) Math.pow(2, downsampleIndex_);
   }
   
   //this method is called to get the tagged image for display purposes only
   //return the zoomed or downsampled image here for fast performance
   @Override
   protected TaggedImage getTaggedImage(int channel, int slice, int frame) {
      //tags and images ultimately get split apart from this function, so it is okay
      //to alter image size and not change tags to reflect that
      
      
      //TODO: return appropriate part of image at approipriate zoom level
      return multiResStorage_.getImageForDisplay(channel, slice, frame, downsampleIndex_, 0, 0, displayImageWidth_, displayImageHeight_);
      
      
   }
   
}
