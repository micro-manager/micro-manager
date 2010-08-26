/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.acquisition;

import ij.ImagePlus;
import ij.process.ImageProcessor;
import java.awt.image.ColorModel;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import mmcorej.TaggedImage;
import org.micromanager.utils.ImageUtils;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author arthur
 */
public class AcquisitionVirtualStack extends ij.VirtualStack {

   private MMImageCache imageCache_;
   private HashMap<Integer,String> filenames_ = new HashMap();
   private HashMap<Integer,Integer> serialNos_ = new HashMap();
   
   protected int width_, height_, type_;
   private int nSlices_;
   private ImagePlus imagePlus_;

   public AcquisitionVirtualStack(int width, int height, ColorModel cm, MMImageCache imageCache, int nSlices)
   {
      super(width, height, cm, "");
      imageCache_ = imageCache;
      width_ = width;
      height_ = height;
      nSlices_ = nSlices;
   }


   public void setImagePlus(ImagePlus imagePlus) {
      imagePlus_ = imagePlus;
   }

   
   public void setType(int type) {
      type_ = type;
   }

   public Object getPixels(int flatIndex) {
      if (!filenames_.containsKey(flatIndex))
         return ImageUtils.makeProcessor(type_, width_, height_).getPixels();
      else {
         try {
            TaggedImage image = getTaggedImage(flatIndex);
            if (MDUtils.isGRAY(image)) {
               return image.pix;
            } else if (MDUtils.isRGB32(image)) {
               return ImageUtils.singleChannelFromRGB32((byte []) image.pix, (flatIndex-1) % 3);
            } else if (MDUtils.isRGB64(image)) {
               return ImageUtils.singleChannelFromRGB64((short []) image.pix, (flatIndex-1) % 3);
            }
         } catch (Exception ex) {
            ReportingUtils.logError(ex);
            return null;
         }
         return null;
      }
   }

   public ImageProcessor getProcessor(int flatIndex) {
      return ImageUtils.makeProcessor(type_, width_, height_, getPixels(flatIndex));
   }

   public TaggedImage getTaggedImage(int flatIndex) {
      if (filenames_.containsKey(flatIndex))
         return imageCache_.getImage(filenames_.get(flatIndex));
      else
         return null;
   }

   public int getSize() {
      return nSlices_;
   }

   void insertImage(int flatIndex, TaggedImage taggedImg) {
      try {
         String filename = imageCache_.putImage(taggedImg);
         filenames_.put(flatIndex, filename);
         if (MDUtils.isRGB(taggedImg)) {
            filenames_.put(flatIndex + 1, filename);
            filenames_.put(flatIndex + 2, filename);
         }
         
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
      }
   }

   void insertImage(TaggedImage taggedImg) {
      int flatIndex = getFlatIndex(taggedImg.tags);
      insertImage(flatIndex, taggedImg);
   }

   public String getSliceLabel(int n) {
      if (filenames_.containsKey(n)) {
         return new File(filenames_.get(n)).getName();
      } else {
         return "";
      }
   }

   public void rememberImage(Map<String,String> md) {
      try {
         int flatIndex = getFlatIndex(md);
         String filename = md.get("Filename");
         filenames_.put(flatIndex, filename);
         if (MDUtils.isRGB(md)) {
            filenames_.put(flatIndex + 1, filename);
            filenames_.put(flatIndex + 2, filename);
         }
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
      }
   }

   private int getFlatIndex(Map<String,String> md) {
      try {
         int slice = MDUtils.getSliceIndex(md);
         int frame = MDUtils.getFrameIndex(md);
         int channel = MDUtils.getChannelIndex(md);
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



   void setComment(String text) {
      imageCache_.setComment(text);
   }

   MMImageCache getCache() {
      return this.imageCache_;
   }

}
