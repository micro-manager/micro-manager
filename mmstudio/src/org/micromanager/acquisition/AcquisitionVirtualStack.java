/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.acquisition;

import ij.ImagePlus;
import ij.process.ImageProcessor;
import java.awt.image.ColorModel;
import mmcorej.TaggedImage;
import org.json.JSONObject;
import org.micromanager.api.TaggedImageStorage;
import org.micromanager.utils.ImageUtils;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.ReportingUtils;

/**
 * This stack class provides the ImagePlus with images from the MMImageCache.
 * 
 */
public class AcquisitionVirtualStack extends ij.VirtualStack {

   final private TaggedImageStorage imageCache_;
   final private VirtualAcquisitionDisplay acq_;
   final protected int width_, height_, type_;
   private int nSlices_;
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

   public void setSize(int size) {
      nSlices_ = size;
   }

   public TaggedImageStorage getCache() {
      return imageCache_;
   }

   public TaggedImage getTaggedImage(int flatIndex) {
      try {
         int[] pos;
         // If we don't have the ImagePlus yet, then we need to assume
         // we are on the very first image.
         ImagePlus imagePlus = acq_.getImagePlus();
         int nSlices;
         if (imagePlus == null) {
            pos = new int[]{1, 1, 1};
            nSlices = 1;
         } else {
            pos = imagePlus.convertIndexToPosition(flatIndex);
            nSlices = imagePlus.getNSlices();
         }
         TaggedImage img;
         int chanIndex = acq_.grayToRGBChannel(pos[0] - 1);
         int frame = pos[2] - 1;
         int slice = pos[1] - 1;

         img = imageCache_.getImage(chanIndex, slice, frame, positionIndex_);
         int backIndex = slice - 1, forwardIndex = slice + 1;
         //If some but not all channels have z stacks, find the closest slice for the given
         //channel that has an image
         while (img == null) {
            if (backIndex >= 0) {
               img = imageCache_.getImage(chanIndex, backIndex, frame, positionIndex_);
               if (img != null) {
                  break;
               }
               backIndex--;
            }
            if (forwardIndex < nSlices) {
               img = imageCache_.getImage(chanIndex, forwardIndex, frame, positionIndex_);
               if (img != null) {
                  break;
               }
               forwardIndex++;
            }
            if (backIndex < 0 && forwardIndex >= nSlices) {
               break;
            }
         }

         return img;
      } catch (Exception e) {
         ReportingUtils.logError(e);
         return null;
      }
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
      } catch (Exception ex) {
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
