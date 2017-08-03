
package org.micromanager.asidispim.Utils;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.measure.ResultsTable;
import ij.plugin.ZProjector;
import ij.plugin.filter.Analyzer;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import javax.vecmath.Point2d;
import javax.vecmath.Point3d;
import mmcorej.TaggedImage;
import org.micromanager.acquisition.MMAcquisition;
import org.micromanager.utils.ImageUtils;

/**
 *
 * @author Nico
 */
public class SamplePositionDetector {
   
   private enum Axis {X, Y, Z}
   

   
   public static Point3d getCenter(final MMAcquisition acq, final int ch, final int pos) {
      final ImagePlus zProjection = project (acq, ch, pos, Axis.Z);
      Point2d centerXY = centralXYPos(zProjection);
      
      final ImagePlus xProjection = project (acq, ch, pos, Axis.X);
      Point2d centerYZ = centralXYPos(xProjection);
      
      Point3d center = new Point3d(centerXY.x, centerXY.y, centerYZ.x);
      
      return center;
   }
   
   private static ImagePlus project (final MMAcquisition acq, final int ch, final int pos, final Axis axis) {
      ImageStack stack = new ImageStack (acq.getWidth(), acq.getHeight());
      int lastFrame = acq.getLastAcquiredFrame();
      for (int z=0; z < acq.getSlices(); z++) {
         TaggedImage tImg = acq.getImageCache().getImage(ch, z, lastFrame, pos);
         ImageProcessor processor = ImageUtils.makeProcessor(tImg);
         stack.addSlice(processor);
      }

      if (axis == Axis.Z) {
         ZProjector zp = new ZProjector(new ImagePlus("tmp", stack));
         zp.doHyperStackProjection(false);
         zp.setMethod(ZProjector.MAX_METHOD);
         zp.doProjection();
         return zp.getProjection();
      } else if (axis == Axis.X) {
         // do a sideways sum
         ImageProcessor projProc = new FloatProcessor(stack.getSize(), stack.getHeight());
         for (int slice = 0; slice < stack.getSize(); slice++) {
            for (int y = 0; y < stack.getHeight(); y++) {
               for (int x = 0; x < stack.getWidth(); x++) {
                  projProc.setf(slice, y, projProc.getf(slice, y) + stack.getProcessor(slice).get(x, y)); 
               }
            }
         }
         return new ImagePlus("projX", projProc);
      }
      // else (todo?)
      return null;              
      
   }
   
   /**
    * Find the averaged centroid xy position of the large objects in the image
    * Thresholds the image, closes gaps, and find objects larger than 50 micron^2
    * Averages the position of their centroids
    * @param projection
    * @return 
    */
   private static Point2d centralXYPos (ImagePlus projection) {
      IJ.setAutoThreshold(projection, "Li dark");
      IJ.run(projection, "Convert to Mask", "");
      IJ.run(projection, "Options...", "iterations=2 count=3 black pad edm=Overwrite do=Close");

      IJ.run("Set Measurements...", "area centroid redirect=None decimal=2");
      IJ.run("Analyze Particles...", "size=50-Infinity display exclude clear add");
      
      ResultsTable rt = Analyzer.getResultsTable();
      
      Point2d xyCenter = new Point2d();
      int numRows = rt.getCounter();
      for (int i = 0; i < numRows; i++) {
         xyCenter.x += rt.getValue("X", i);
         xyCenter.y += rt.getValue("Y", i);
      }
      xyCenter.x /= numRows;
      xyCenter.y /= numRows;
   
      return xyCenter;
   }

   
}
