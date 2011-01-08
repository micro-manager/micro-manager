/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.acquisition;

import ij.ImagePlus;
import ij.process.ImageProcessor;
import java.awt.image.ColorModel;
import mmcorej.TaggedImage;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.api.TaggedImageStorage;
import org.micromanager.utils.ImageUtils;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.MMException;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author arthur
 */
public class AcquisitionVirtualStack extends ij.VirtualStack {

   private TaggedImageStorage imageCache_;
   
   protected int width_, height_, type_;
   private int nSlices_;
   private ImagePlus imagePlus_;
   private final int positionIndex_;
   private final MMVirtualAcquisitionDisplay acq_;

   public AcquisitionVirtualStack(int width, int height, ColorModel cm, 
           MMImageCache imageCache, int nSlices, int posIndex,
           MMVirtualAcquisitionDisplay acq) {
      super(width, height, cm, "");
      imageCache_ = imageCache;
      width_ = width;
      height_ = height;
      nSlices_ = nSlices;
      positionIndex_ = posIndex;
      acq_ = acq;
   }

   public MMVirtualAcquisitionDisplay getVirtualAcquisition() {
      return acq_;
   }

   public void setImagePlus(ImagePlus imagePlus) {
      imagePlus_ = imagePlus;
   }

   
   public void setType(int type) {
      type_ = type;
   }

   @Override
   public Object getPixels(int flatIndex) {
      Object pixels = null;
      try {
         TaggedImage image = getTaggedImage(flatIndex);
         if (image == null)
            pixels = ImageUtils.makeProcessor(type_, width_, height_).getPixels();
         else if(MDUtils.isGRAY(image)) {
            pixels = image.pix;
         } else if (MDUtils.isRGB32(image)) {
            pixels = ImageUtils.singleChannelFromRGB32((byte []) image.pix, (flatIndex-1) % 3);
         } else if (MDUtils.isRGB64(image)) {
            pixels = ImageUtils.singleChannelFromRGB64((short []) image.pix, (flatIndex-1) % 3);
         }

         if (image != null) {
            try {
               int binning = MDUtils.getBinning(image.tags);
               if (binning > 1) {
                  ImageProcessor proc = ImageUtils.makeProcessor(type_,
                          width_/binning, height_/binning, image.pix);
                  pixels = proc.resize(width_, height_).getPixels();
               }
            } catch (JSONException ex) {
               ReportingUtils.logError("Tagged image did not contain Binning Info");
            }
         }

      } catch (Exception ex) {
         ReportingUtils.logError(ex);
      }


      return pixels;
   }

   @Override
   public ImageProcessor getProcessor(int flatIndex) {
      return ImageUtils.makeProcessor(type_, width_, height_, getPixels(flatIndex));
   }

   public TaggedImage getTaggedImage(int flatIndex) {
      if (imagePlus_ == null)
         return null;
      
      try {
         int[] pos = imagePlus_.convertIndexToPosition(flatIndex);
         return imageCache_.getImage(pos[0] - 1, pos[1] - 1, pos[2] - 1, positionIndex_); // chan, slice, frame
      } catch (Exception e) {
         ReportingUtils.logError(e);
         return null;
      }
   }

   @Override
   public int getSize() {
      return nSlices_;
   }

   public void insertImage(TaggedImage taggedImg) {
      try {
         imageCache_.putImage(taggedImg);
      } catch (MMException e) {
         ReportingUtils.logError(e);
      }
   }

   private int getFlatIndex(JSONObject md) {
      try {
         int channel = MDUtils.getChannelIndex(md);
         int slice = MDUtils.getSliceIndex(md);
         int frame = MDUtils.getFrameIndex(md);
         if (imagePlus_ == null && slice == 0 && frame == 0 && channel == 0) {
            return 1;
         } else {
            return imagePlus_.getStackIndex(1 + channel, 1 + slice, 1 + frame);
         }
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
         return 0;
      }
   }

   MMImageCache getCache() {
      return (MMImageCache) this.imageCache_;
   }


   @Override
   public String getSliceLabel(int n) {
      TaggedImage img = getTaggedImage(n);
      if (img == null)
         return "";
      JSONObject md = img.tags;
      try {
         return md.get("Acquisition-PixelSizeUm") + " um/px";
         //return MDUtils.getChannelName(md) + ", " + md.get("Acquisition-ZPositionUm") + " um(z), " + md.get("Acquisition-TimeMs") + " s";
      } catch (Exception ex) {
         return "";
      }
   }

}
