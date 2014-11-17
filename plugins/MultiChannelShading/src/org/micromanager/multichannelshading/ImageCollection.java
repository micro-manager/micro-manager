
package org.micromanager.multichannelshading;

import ij.ImagePlus;
import ij.process.ImageProcessor;
import java.awt.Rectangle;
import java.util.HashMap;
import org.micromanager.utils.ImageUtils;
import org.micromanager.utils.MMException;

/**
 *
 * @author nico
 */
public class ImageCollection {
   private final HashMap<String, ImagePlusInfo> background_;
   private final HashMap<String, HashMap<String, ImagePlusInfo>> flatFields_;
   
   private final String BASEIMAGE = "base";
   
   public ImageCollection() {
      background_ = new HashMap<String, ImagePlusInfo>();
      flatFields_ = new HashMap<String, HashMap<String, ImagePlusInfo>>();
   }
   
   public void setBackground(String file) throws MMException {
      background_.clear();
      ij.io.Opener opener = new ij.io.Opener();
      ImagePlus ip = opener.openImage(file);
      if (ip == null) {
         throw new MMException("Failed to open file: " + file);
      }
      background_.put(BASEIMAGE, new ImagePlusInfo(ip));
   }
   
   public ImagePlusInfo getBackground() {
      return background_.get(BASEIMAGE);
   }
   
   public ImagePlusInfo getBackground(int binning, Rectangle roi) 
           throws MMException {
      String key = makeKey(binning, roi);
      if (background_.containsKey(key)) {
         return background_.get(key);
      }
      // key not found, so derive the image from the original
      ImagePlusInfo bg = getBackground();
      if (bg == null) {
         return null;
      }
      
      return makeDerivedImage(bg, binning, roi); 
   }
   
   public void addFlatField(String preset, String file) throws MMException {
      ij.io.Opener opener = new ij.io.Opener();
      ImagePlus ip = opener.openImage(file);
      ImagePlusInfo bg = getBackground();
      ImagePlusInfo flatField;
      if (bg != null) {
      ImageProcessor differenceProcessor = ImageUtils.subtractImageProcessors(
              ip.getProcessor(), bg.getProcessor());
         flatField = new ImagePlusInfo(differenceProcessor);
      } else {
         flatField = new ImagePlusInfo(ip);
      }
      HashMap<String, ImagePlusInfo> newFlatField = 
              new HashMap<String, ImagePlusInfo>();
      newFlatField.put(BASEIMAGE, flatField);
      flatFields_.put(preset, newFlatField);
   }
   
   public ImagePlusInfo getFlatField(String preset) {
      return flatFields_.get(preset).get(BASEIMAGE);
   }
   
   public void clearFlatFields() {
      flatFields_.clear();
   }
   
   public void removeFlatField(String preset) {
      flatFields_.remove(preset);
   }
   
   public ImagePlusInfo getFlatField(String preset, int binning, Rectangle roi) 
           throws MMException {
      String key = makeKey(binning, roi);
      if (flatFields_.get(preset).containsKey(key)) {
         return flatFields_.get(preset).get(key);
      }
      // key not found, so derive the image from the original
      ImagePlusInfo ff = getFlatField(preset);
      if (ff == null) {
         return null;
      }
      return makeDerivedImage(ff, binning, roi);
   }
   
   private String makeKey(int binning, Rectangle roi) {
      if (binning == 1 && (roi == null || roi.width == 0) ) {
         return BASEIMAGE;
      }
      String key = binning + "-" + roi.x + "-" + roi.y + "-" + roi.width + "-" +
              roi.height;
      return key;
   }
   
    /**
    * Generates a new ImagePlus from this one by applying the 
    * requested binning and setting the desired ROI.
    * Should only be called on the original image (i.e. binning = 1, full field image)
    * If the original image was normalized, this one will be as well (as it 
    * is derived from the normalized image)
    * @param ipi
    * @param binning
    * @param roi
    * @return 
    * @throws org.micromanager.utils.MMException 
    */
   private ImagePlusInfo makeDerivedImage(ImagePlusInfo ipi, int binning, Rectangle roi) 
           throws MMException {
      if (ipi.getBinning() != 1) {
         throw new MMException("This is not an unbinned image.  " +
                 "Can not derive binned images from this one");
      }
      ImageProcessor resultProcessor;
      if (binning != 1) {
         resultProcessor = ipi.getProcessor().bin(binning);
      } else {
         resultProcessor = ipi.getProcessor().duplicate();
      }
      resultProcessor.setRoi(roi);
      resultProcessor.duplicate();
      ImagePlusInfo newIp = new ImagePlusInfo(new ImagePlus(), binning, roi);
      newIp.setProcessor(resultProcessor.duplicate());
      
      return newIp;         
   }
   
   
}
