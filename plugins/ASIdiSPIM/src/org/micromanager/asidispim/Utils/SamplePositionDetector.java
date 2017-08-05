
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
   

   /**
    * Calculates the sample of mass (using pixel coordinates) of the 3D volume of
    * the given channel and position in this acquisition.
    * Returns the center of mass in pixel coordinates
    * @param acq  - Micro-Manager acquisition for which we want to know the center
    *                of mass
    * @param ch   - Channel to be used
    * @param pos  - position to be used
    * @return     - Center of mass in pixel coordinates
    */
   public static Point3d getCenter(final MMAcquisition acq, final int ch, final int pos) {
      return centerOfMass(getStack(acq, ch, pos));
   }
   
   
   /**
    * Returns an ImageJ stack for the given channel and position in this
    * Micro-manager acquisition
    * @param acq   - Micro-Manager dataset
    * @param ch    - Channel for which we want the stack
    * @param pos   - Position for which we want the stack
    * @return      - ImageJ stack containing a volume in one channel at one position
    */
   private static ImageStack getStack (final MMAcquisition acq, final int ch, final int pos) {
      final ImageStack stack = new ImageStack (acq.getWidth(), acq.getHeight());
      final int lastFrame = acq.getLastAcquiredFrame();
      for (int z=0; z < acq.getSlices(); z++) {
         TaggedImage tImg = acq.getImageCache().getImage(ch, z, lastFrame, pos);
         ImageProcessor processor = ImageUtils.makeProcessor(tImg);
         stack.addSlice(processor);
      }
      return stack;
   }
   
   private static ImagePlus project (final MMAcquisition acq, final int ch, final int pos, final Axis axis) {
      ImageStack stack = getStack(acq, ch, pos);

      if (axis == Axis.Z) {
         ZProjector zp = new ZProjector(new ImagePlus("tmp", stack));
         zp.doHyperStackProjection(false);
         zp.setMethod(ZProjector.MAX_METHOD);
         zp.doProjection();
         return zp.getProjection();
      } else if (axis == Axis.X) {
         // do a sideways sum
         ImageProcessor projProc = new FloatProcessor(stack.getSize(), stack.getHeight());
         for (int slice = 1; slice <= stack.getSize(); slice++) {
            for (int y = 0; y < stack.getHeight(); y++) {
               for (int x = 0; x < stack.getWidth(); x++) {
                  projProc.setf(slice-1, y, projProc.getf(slice-1, y) + stack.getProcessor(slice).get(x, y)); 
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
      IJ.run(projection, "Convert to Mask", "method=Li background=Dark black");
      IJ.run(projection, "Options...", "iterations=2 count=3 black pad edm=Overwrite do=Close");

      IJ.run("Set Measurements...", "center redirect=None decimal=2");
      // NS: I don't know why, but it seems that ImageJ uses pixel units rather than 
      // micron^2, as it is supposed to.  Take no chances, and force it to use
      // pixels^2 and convert ourselves.
      double minSize = 50.0;
      minSize /= 0.165;  // TODO: get pixel Width correctly
      minSize /= 0.165;  
      IJ.run(projection, "Measure", "");
      
      projection.show();
      
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
   
   private static Point3d centerOfMass(ImageStack stack) {
      int depth = stack.getSize();
      ImageProcessor[] imgProcs = new ImageProcessor[stack.getSize()];
      for (int i = 0; i < stack.getSize(); i++) {
         imgProcs[i] = stack.getProcessor(i+1);
      }
      
      double totalMass = 0.0;
      double totalX = 0.0;
      double totalY = 0.0;
      double totalZ = 0.0;
      for (int z = 0; z < depth; z++) {
         for (int y = 0; y < stack.getHeight(); y++) {
            for (int x = 0; x < stack.getWidth(); x++) {
               totalMass += imgProcs[z].get(x, y);
               totalX += x *  imgProcs[z].get(x, y);
               totalY += y * imgProcs[z].get(x, y);
               totalZ += z * imgProcs[z].get(x, y);
            }
         }
      }
      Point3d centerOfMass = new Point3d(
            totalX / totalMass,
            totalY / totalMass,
            totalZ / totalMass);
      
      return centerOfMass;
   }

   
}
