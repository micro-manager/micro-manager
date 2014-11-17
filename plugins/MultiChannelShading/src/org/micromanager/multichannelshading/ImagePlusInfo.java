

package org.micromanager.multichannelshading;

import ij.ImagePlus;
import ij.process.ImageProcessor;
import java.awt.Rectangle;

/**
 *
 * @author nico
 */
public class ImagePlusInfo extends ImagePlus{
   private final int binning_;
   private final Rectangle roi_;
    
   
   public ImagePlusInfo(ImagePlus ip, int binning, Rectangle roi) {
      super(ip.getTitle(), ip.getProcessor());
      binning_ = binning;
      roi_ = roi;
   }
   
   public ImagePlusInfo(ImagePlus ip) {
      this(ip, 1, new Rectangle(0, 0, ip.getWidth(), ip.getHeight()));
   }
   
   public ImagePlusInfo(ImageProcessor ip) {
      super("", ip);
      binning_ = 1;
      roi_ = new Rectangle(0, 0, ip.getWidth(), ip.getHeight());
   }
   
   public int getBinning() {
      return binning_;
   }
   
   public Rectangle getOriginalRoi() {
      return roi_;
   }
}
