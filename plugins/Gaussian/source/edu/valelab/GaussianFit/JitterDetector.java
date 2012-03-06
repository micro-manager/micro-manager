/*
 * This class uses autocorrelation to detect the movement between a reference image
 * and the given image
 */
package edu.valelab.GaussianFit;

import ij.ImagePlus;
import ij.process.FHT;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import java.awt.Point;
import java.awt.geom.Point2D;

/**
 *
 * @author Nico Stuurman
 */
public class JitterDetector {
   private final FHT ref_;
   
   public JitterDetector(ImageProcessor reference) {
     ref_ = new FHT(reference);
     ref_.transform();
     ref_.resetMinAndMax();
     //new ImagePlus("Ref test", ref_).show();
   }
   
   public void getJitter(ImageProcessor test, Point2D.Double com) {
      FHT t = new FHT(test);
      t.transform();
      t.resetMinAndMax();
      //new ImagePlus("Ref Test2", t).show();
      
      FHT m = ref_.conjugateMultiply(t);
            
      m.inverseTransform();
      m.swapQuadrants();
      //m.resetMinAndMax();
      int midx = m.getWidth() / 2;
      int midy = m.getHeight() / 2;
      
      // return the position of the brightest pixel     
      float pixels[] = (float[]) m.getPixels();
      Point brightPix = new Point(0, 0);
      int fpi = 0;
      double max = pixels[fpi];
      brightPix.x = fpi;
      brightPix.y = fpi;
      int height = m.getHeight();
      int width = m.getWidth();
      // this could be optimized by only searching in the center
      for (int y = 0; y < height; y++) {
         for (int x = 0; x < width; x++) {
            if (pixels[y*width + x] > max) {
               max = pixels[y*width + x];
               brightPix.x = x;
               brightPix.y = y;
            }
         }
      }

      com.x = brightPix.x;
      com.y = brightPix.y;
      
      //ImagePlus mp = new ImagePlus("JitterTest", m);
      //mp.show();
      
      // return the centerofMass
     /*
      int hw = 4;
      m.setRoi(brightPix.x - hw, brightPix.y - hw, 2 * hw, 2 * hw);
      ImagePlus mp = new ImagePlus("JitterTest", m);
      
      //ij.IJ.showStatus("Display image");
      // mp.show();
      
      ImageStatistics stats = mp.getStatistics(ij.measure.Measurements.CENTER_OF_MASS);
      

      com.x = stats.xCenterOfMass;
      com.y = stats.yCenterOfMass;
      */
         
   }
   
}
