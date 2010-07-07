/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.acquisition;

import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import java.awt.image.ColorModel;
import java.util.HashMap;
import org.micromanager.utils.ImageUtils;

/**
 *
 * @author arthur
 */
public class AcquisitionVirtualStack extends ij.VirtualStack {

   private MMImageCache imageCache_;
   private HashMap<Integer,String> filenames_ = new HashMap();

   protected int width_, height_, type_;
   private final int nSlices_;

   public AcquisitionVirtualStack(int width, int height, ColorModel cm, String path, MMImageCache imageCache, int nSlices)
   {
      super(width, height, cm, path);
      imageCache_ = imageCache;
      width_ = width;
      height_ = height;
      nSlices_ = nSlices;
   }

   public void setType(int type) {
      type_ = type;
   }

   public Object getPixels(int flatIndex) {
      if (!filenames_.containsKey(flatIndex))
         return new byte[width_*height_];
      else
         return imageCache_.getImage(filenames_.get(flatIndex)).img;
   }

   public ImageProcessor getProcessor(int flatIndex) {
      return new ByteProcessor(width_, height_);
   }

   public int getSize() {
      return nSlices_;
   }

 
   void insertImage(int index, MMImageBuffer imgBuf) {
      filenames_.put(index, imageCache_.putImage(imgBuf));
   }
}
