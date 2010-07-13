/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.acquisition;

import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import java.awt.image.ColorModel;
import java.io.File;
import java.io.FileFilter;
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
   private int nSlices_;
   private String path_;

   public AcquisitionVirtualStack(int width, int height, ColorModel cm, String path, MMImageCache imageCache, int nSlices)
   {
      super(width, height, cm, path);
      imageCache_ = imageCache;
      width_ = width;
      height_ = height;
      nSlices_ = nSlices;
      path_ = path;
   }

   public AcquisitionVirtualStack(int width, int height, ColorModel cm, String path, MMImageCache imageCache) {
      this(width, height, cm, path, imageCache, 0);
      final File dir = new File(path);
      FileFilter filter = new FileFilter() {
         public boolean accept(File pathname) {
            String filePath = pathname.getAbsolutePath();
            return (filePath.endsWith(".tiff") || filePath.endsWith(".tif"));
         }
      };
      final File[] files = dir.listFiles(filter);
      nSlices_ = files.length;
      int i = 1;
      for (File file : files) {
         filenames_.put(i, file.getAbsolutePath());
         ++i;
      }
   }

   public String getPath() {
      return path_;
   }
   
   public void setType(int type) {
      type_ = type;
   }

   public Object getPixels(int flatIndex) {
      if (!filenames_.containsKey(flatIndex))
         return new byte[width_*height_];
      else {
         return imageCache_.getImage(filenames_.get(flatIndex)).img;
      }
   }

   public ImageProcessor getProcessor(int flatIndex) {
      return new ByteProcessor(width_, height_, (byte []) getPixels(flatIndex), null);
   }

   public int getSize() {
      return nSlices_;
   }

 
   void insertImage(int index, MMImageBuffer imgBuf) {
      filenames_.put(index, imageCache_.putImage(imgBuf));
   }

   public String getSliceLabel(int n) {
      if (filenames_.containsKey(n)) {
         return new File(filenames_.get(n)).getName();
      } else {
         return "";
      }

   }
}
