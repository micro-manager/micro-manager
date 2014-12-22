
package mmcloneclasses.imagedisplay;

import ij.ImagePlus;
import ij.process.ImageProcessor;
import java.awt.image.ColorModel;
import mmcorej.TaggedImage;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.api.TaggedImageStorage;
import org.micromanager.utils.ImageUtils;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.MMScriptException;
import org.micromanager.utils.ReportingUtils;

/**
 * This stack class provides the ImagePlus with images from the MMImageCache.
 * 
 */
public class AcquisitionVirtualStack extends ij.VirtualStack {

   final private TaggedImageStorage imageCache_;
   final private VirtualAcquisitionDisplay acq_;
   final protected int width_, height_, type_;
   private final int nSlices_;
   private int positionIndex_ = 0;

   public AcquisitionVirtualStack(int width, int height, int type,
           ColorModel cm, TaggedImageStorage imageCache, int nSlices,
           VirtualAcquisitionDisplay acq) {
      super(width, height, cm, "");
      imageCache_ = imageCache;
      width_ = width;
      height_ = height;
      nSlices_ = nSlices;

      acq_ = acq;
      type_ = type;
   }

   public void setPositionIndex(int pos) {
      positionIndex_ = pos;
   }

   public int getPositionIndex() {
      return positionIndex_;
   }

   public VirtualAcquisitionDisplay getVirtualAcquisitionDisplay() {
      return acq_;
   }

   
   private TaggedImage getTaggedImage(int flatIndex) {
      int[] pos;
      // If we don't have the ImagePlus yet, then we need to assume
      // we are on the very first image.
      ImagePlus imagePlus = acq_.getImagePlus();
      if (imagePlus == null) {
         return getTaggedImage(0,0,0);
      } else {
         pos = imagePlus.convertIndexToPosition(flatIndex);
      }
      int chanIndex = acq_.grayToRGBChannel(pos[0] - 1);
      int frame = pos[2] - 1;
      int slice = pos[1] - 1;

      return getTaggedImage(chanIndex, slice, frame);
   }

   //This method is the ultimate source of tagged images/metadata to update the display, but has no
   //relevance to image data on disk. It is protected so that this class can be overriden and a differnet image
   //used for display compared to the the underlying data
   protected TaggedImage getTaggedImage(int chanIndex, int slice, int frame) {
      int nSlices;
      ImagePlus imagePlus = acq_.getImagePlus();
      if (imagePlus == null) {
         nSlices = 1;
      } else {
         nSlices = imagePlus.getNSlices();
      }
      try {
         TaggedImage img;
         img = imageCache_.getImage(chanIndex, slice, frame, positionIndex_);
         int backIndex = slice - 1, forwardIndex = slice + 1;
         int frameSearchIndex = frame;
         //If some but not all channels have z stacks, find the closest slice for the given
         //channel that has an image.  Also if time point missing, go back until image is found
         while (img == null) {
            img = imageCache_.getImage(chanIndex, slice, frameSearchIndex, positionIndex_);
            if (img != null) {
               break;
            }

            if (backIndex >= 0) {
               img = imageCache_.getImage(chanIndex, backIndex, frameSearchIndex, positionIndex_);
               if (img != null) {
                  break;
               }
               backIndex--;
            }
            if (forwardIndex < nSlices) {
               img = imageCache_.getImage(chanIndex, forwardIndex, frameSearchIndex, positionIndex_);
               if (img != null) {
                  break;
               }
               forwardIndex++;
            }

            if (backIndex < 0 && forwardIndex >= nSlices) {
               frameSearchIndex--;
               backIndex = slice - 1;
               forwardIndex = slice + 1;
               if (frameSearchIndex < 0) {
                  break;
               }
            }
         }

         return img;
      } catch (Exception e) {
         ReportingUtils.logError(e);
         return null;
      }
   }
   
   //this method is available so that image tags can be synchrnized with the pixels displayed in the viewer,
   //since alternate images are filled in when some are missing (for example, when a z stack is not collecte din one channel
   //or when frames are skipped)
   public JSONObject getImageTags(int flatIndex) {
      TaggedImage img = getTaggedImage(flatIndex);
      if (img == null) {
         return null;
      }
      return img.tags;
      
   }

   @Override
   public Object getPixels(int flatIndex) {
      Object pixels = null;
      try {
         TaggedImage image = getTaggedImage(flatIndex);
         if (image == null) {
            pixels = ImageUtils.makeProcessor(type_, width_, height_).getPixels();
         } else if (MDUtils.isGRAY(image)) {
            pixels = image.pix;
         } else if (MDUtils.isRGB32(image)) {
            pixels = ImageUtils.singleChannelFromRGB32((byte[]) image.pix, (flatIndex - 1) % 3);
         } else if (MDUtils.isRGB64(image)) {
            pixels = ImageUtils.singleChannelFromRGB64((short[]) image.pix, (flatIndex - 1) % 3);
         }
      } catch (JSONException ex) {
         ReportingUtils.logError(ex);
      } catch (MMScriptException ex) {
         ReportingUtils.logError(ex);
      }

      return pixels;
   }

   @Override
   public ImageProcessor getProcessor(int flatIndex) {
      return ImageUtils.makeProcessor(type_, width_, height_, getPixels(flatIndex));
   }

   @Override
   public int getSize() {
      // returns the stack size of VirtualAcquisitionDisplay unless this size is -1
      // which occurs in constructor while hyperImage_ is still null. In this case
      // returns the number of slices speciefiec in AcquisitionVirtualStack constructor
      int size = acq_.getStackSize();
      if (size == -1) {
         return nSlices_;
      }
      return size;
   }

   @Override
   public String getSliceLabel(int n) {
      TaggedImage img = getTaggedImage(n);
      if (img == null) {
         return "";
      }
      JSONObject md = img.tags;
      try {
         return md.get("Acquisition-PixelSizeUm") + " um/px";
         //return MDUtils.getChannelName(md) + ", " + md.get("Acquisition-ZPositionUm") + " um(z), " + md.get("Acquisition-TimeMs") + " s";
      } catch (Exception ex) {
         return "";
      }
   }
}
