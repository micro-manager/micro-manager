
package org.micromanager.asidispim.Utils;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.measure.ResultsTable;
import ij.plugin.ZProjector;
import ij.plugin.filter.Analyzer;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import java.awt.geom.Point2D;

import mmcorej.TaggedImage;
import org.micromanager.acquisition.MMAcquisition;
import org.micromanager.utils.ImageUtils;

import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;


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
   public static Vector3D getCenter(final MMAcquisition acq, final int ch, final int pos) {
      /*
      ImagePlus xyProjection = project(acq, ch, pos, Axis.Z);
      xyProjection.setTitle("XYProjection");
      xyProjection.show();
      ImagePlus xzProjection = project(acq, ch, pos, Axis.Y);
      xzProjection.setTitle("XZProjection");
      xzProjection.show();
      ImagePlus yzProjection = project(acq, ch, pos, Axis.X);
      yzProjection.setTitle("YZProjection");
      yzProjection.show();
      */
      ImageStack tmpStack = getStack(acq, ch, pos);
      return centerOfMass(tmpStack, 450);
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
         ImageProcessor projProc = new FloatProcessor( stack.getSize(), stack.getHeight()  );
         for (int slice = 1; slice <= stack.getSize(); slice++) {
            for (int y = 0; y < stack.getHeight(); y++) {
               for (int x = 0; x < stack.getWidth(); x++) {
                  projProc.setf(slice-1, y, projProc.getf(slice - 1, y) + stack.getProcessor(slice).get(x, y)); 
               }
            }
         }
         return new ImagePlus("YZProjection", projProc);
      } else if (axis == Axis.Y) {
         // do a sideways sum
         ImageProcessor projProc = new FloatProcessor( stack.getWidth(), stack.getSize() );
         for (int slice = 1; slice <= stack.getSize(); slice++) {
            for (int x = 0; x < stack.getWidth(); x++) {
               for (int y = 0; y < stack.getHeight(); y++) {
                  projProc.setf(x, slice-1, projProc.getf(x, slice-1) + stack.getProcessor(slice).get(x, y)); 
               }
            }
         }
         return new ImagePlus("XZProjection", projProc);
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
   private static Point2D centralXYPos (ImagePlus projection) {
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
      
      double x = 0.0;
      double y = 0.0;
      int numRows = rt.getCounter();
      for (int i = 0; i < numRows; i++) {
         x += rt.getValue("X", i);
         y += rt.getValue("Y", i);
      }
      x /= numRows;
      y /= numRows;
   
      return new Point2D.Double(x, y);
   }
   
   private static Vector3D centerOfMass(ImageStack stack, int offset) {
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
               int mass = imgProcs[z].get(x, y) - offset;
               if (mass < 0) {
                  mass = 0;
               }
               totalMass += mass;
               totalX += x * mass;
               totalY += y * mass;
               totalZ += z * mass;
            }
         }
      }
      Vector3D centerOfMass = new Vector3D(
            totalX / totalMass,
            totalY / totalMass,
            totalZ / totalMass);
      
      return centerOfMass;
   }

   
}
