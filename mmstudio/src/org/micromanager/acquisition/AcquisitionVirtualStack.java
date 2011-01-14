/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.acquisition;

import ij.process.ImageProcessor;
import java.awt.image.ColorModel;
import mmcorej.TaggedImage;
import org.json.JSONObject;
import org.micromanager.api.TaggedImageStorage;
import org.micromanager.utils.ImageUtils;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author arthur
 */
public class AcquisitionVirtualStack extends ij.VirtualStack {
   final private TaggedImageStorage imageCache_;
   private final VirtualAcquisitionDisplay acq_;
   protected int width_, height_, type_;
   private int nSlices_;
   private int positionIndex_ = 0;

   public AcquisitionVirtualStack(int width, int height, int type,
           ColorModel cm, MMImageCache imageCache, int nSlices,
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

   public VirtualAcquisitionDisplay getVirtualAcquisition() {
      return acq_;
   }

   public void setSize(int size) {
      nSlices_ = size;
   }

   MMImageCache getCache() {
      return (MMImageCache) this.imageCache_;
   }

   public TaggedImage getTaggedImage(int flatIndex) {
      if (acq_.getImagePlus() == null)
         return null;

      try {
         int[] pos = acq_.getImagePlus().convertIndexToPosition(flatIndex);
         return imageCache_.getImage(pos[0] - 1, pos[1] - 1, pos[2] - 1, positionIndex_); // chan, slice, frame
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
         if (image == null)
            pixels = ImageUtils.makeProcessor(type_, width_, height_).getPixels();
         else if(MDUtils.isGRAY(image)) {
            pixels = image.pix;
         } else if (MDUtils.isRGB32(image)) {
            pixels = ImageUtils.singleChannelFromRGB32((byte []) image.pix, (flatIndex-1) % 3);
         } else if (MDUtils.isRGB64(image)) {
            pixels = ImageUtils.singleChannelFromRGB64((short []) image.pix, (flatIndex-1) % 3);
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
      return nSlices_;
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
