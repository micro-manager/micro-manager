/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.acquisition;

import ij.process.ImageProcessor;
import java.awt.image.ColorModel;
import java.util.Map;
import org.micromanager.utils.ImageUtils;

/**
 *
 * @author arthur
 */
public class AcquisitionVirtualStack extends ij.VirtualStack {

   private Map<Integer, ImageProcessor> ramImages_;
   protected int width_, height_, type_;

   public AcquisitionVirtualStack(int width, int height, ColorModel cm, String path)
   {
      super(width, height, cm, path);
      width_ = width;
      height_ = height;
   }

   public void setType(int type) {
      type_ = type;
   }

   public void addSlice(int sliceIndex, Object pixels) {
      ramImages_.put(sliceIndex, ImageUtils.makeProcessor(type_, width_, height_, pixels));
   }

   public void clearCachedImages() {
      ramImages_.clear();
   }

   public ImageProcessor getProcessor(int n) {
      // If in RAM, return from RAM. Otherwise read from disk and return.
      if (ramImages_.containsKey(n)) {
         return (ramImages_.get(n));
      } else {
         return super.getProcessor(n);
      }
   }

   /** Sets the bit depth (8, 16, 24 or 32). */
   public void setBitDepth(int bitDepth) {
      super.setBitDepth(bitDepth);
      type_ = ImageUtils.BppToImageType(8 * bitDepth);
   }
}
