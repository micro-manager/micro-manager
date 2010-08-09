/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.acquisition;

import ij.ImagePlus;
import ij.process.ImageProcessor;
import java.awt.image.ColorModel;
import java.io.File;
import java.io.FileFilter;
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

   protected int width_, height_, type_;
   private int nSlices_;
   private String path_;
   private ImagePlus imagePlus_;

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

   public void setImagePlus(ImagePlus imagePlus) {
      imagePlus_ = imagePlus;
   }

   public String getPath() {
      return path_;
   }
   
   public void setType(int type) {
      type_ = type;
   }

   public Object getPixels(int flatIndex) {
      if (!filenames_.containsKey(flatIndex))
         return ImageUtils.makeProcessor(type_, width_, height_).getPixels();
      else {
         return getTaggedImage(flatIndex).pix;
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
      filenames_.put(flatIndex, imageCache_.putImage(taggedImg));
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
      int flatIndex = getFlatIndex(md);
      String filename = md.get("Filename");
      filenames_.put(flatIndex, filename);
   }

   private int getFlatIndex(Map<String,String> md) {
      try {
         int slice = MDUtils.getSlice(md);
         int frame = MDUtils.getFrame(md);
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

}
