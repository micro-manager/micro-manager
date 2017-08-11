
package org.micromanager.asidispim.Utils;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.measure.ResultsTable;
import ij.plugin.FFTMath;
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
      ImageStack tmpStack = getStack(acq, acq.getLastAcquiredFrame(), ch, pos);
      return centerOfMass(tmpStack, 450);
   }
   
   /**
    * Calculates the displacement (in microns) between object in frame and
    * frame -1 by projecting the stacks on the X, Y, and Z axis, Phase
    * Correlation of these 3 sets of 2D images (see 
    * https://en.wikipedia.org/wiki/Phase_correlation), using the ImageJ 
    * FD Math method, calculating the position of the maxima, and averaging
    * the two measurements.  
    * Projections are sized to be a square of a power of 2 (i.e. 256x256, 1024, 1024).
    * For best and fastest results, all sizes in the stack should be a power of 2.
    * Returns a vector indicating the displacement (in microns) between the two frames.
    * @param acq - Micro-Manager acquisition to be analyzed
    * @param frame - Frame number to be analyzed.  If <= 1, the zero vector will be returned
    * @param ch - Channel in the acquisition to be used
    * @param pos - Position in the acquistion to be used
    * @param pixelSize - PixelSize in the XY plane
    * @param zStep - Stepsize between Z planes
    * @return - Vector showing the object displacement in microns
    */
   public static Vector3D getDisplacementUsingIJPhaseCorrelation(
           MMAcquisition acq, final int frame, final int ch, final int pos, 
           final double pixelSize, final double zStep) {
      
      Vector3D zeroVector = new Vector3D (0.0, 0.0, 0.0);
      
      if (frame < 1) {
         return zeroVector;
      }
      if (acq.getFrames() < frame) {
         return zeroVector; // TODO: throw an exception instead?
      }
      ImageStack stackCurrent = getStack(acq, frame, ch, pos);
      ImageStack stackPrevious = getStack(acq, frame, ch, pos);
      
      ImagePlus XYProjectCurrent = project(stackCurrent, Axis.Z);
      ImagePlus XYProjectionPrevious = project(stackPrevious, Axis.Z);
      ImagePlus XZProjectionCurrent = project(stackCurrent, Axis.Y);
      ImagePlus XZProjectionPrevious = project(stackPrevious, Axis.Y);
      ImagePlus YZProjectionCurrent = project(stackCurrent, Axis.X);
      ImagePlus YZProjectionPrevious = project(stackPrevious, Axis.X);
      
      ImagePlus[] ips = {XYProjectCurrent, XYProjectionPrevious, 
                        XZProjectionCurrent, XZProjectionPrevious,
                        YZProjectionCurrent, YZProjectionPrevious};
      for (ImagePlus ip : ips) {
         IJ.setAutoThreshold(ip, "Default dark");
         IJ.run(ip, "Convert to Mask", "");
      }
      //IJ.run
              
      FFTMath fftm = new FFTMath();
      // run a correlation, do inverse
      fftm.run("operation=Correlate, result=tmp do");
      fftm.doMath(XYProjectCurrent, XYProjectionPrevious);
      
      
      return zeroVector;
      
   }
   
   
   /**
    * Returns an ImageJ stack for the given frame, channel and position in this
    * Micro-manager acquisition
    * @param acq   - Micro-Manager dataset
    * @param ch    - Channel for which we want the stack
    * @param pos   - Position for which we want the stack
    * @return      - ImageJ stack containing a volume in one channel at one position
    */
   private static ImageStack getStack (final MMAcquisition acq, final int frame, 
           final int ch, final int pos) {
      final ImageStack stack = new ImageStack (acq.getWidth(), acq.getHeight());
      for (int z=0; z < acq.getSlices(); z++) {
         TaggedImage tImg = acq.getImageCache().getImage(ch, z, frame, pos);
         ImageProcessor processor = ImageUtils.makeProcessor(tImg);
         stack.addSlice(processor);
      }
      return stack;
   }
   
   private static ImagePlus project (ImageStack stack, final Axis axis) {
      
      if (null != axis) 
         switch (axis) {
         case Z:
            ZProjector zp = new ZProjector(new ImagePlus("tmp", stack));
            zp.doHyperStackProjection(false);
            zp.setMethod(ZProjector.MAX_METHOD);
            zp.doProjection();
            return zp.getProjection();
         case X:
         {
            // do a sideways sum
            ImageProcessor projProc = new FloatProcessor( stack.getSize(), stack.getHeight()  );
            for (int slice = 1; slice <= stack.getSize(); slice++) {
               for (int y = 0; y < stack.getHeight(); y++) { 
                  float sum = 0.0f;
                  for (int x = 0; x < stack.getWidth(); x++) {
                     sum += stack.getProcessor(slice).get(x, y);
                  }
                  projProc.setf(slice-1, y, sum);
               }
            }
            return new ImagePlus("YZProjection", projProc);
         }
         case Y:
         {
            // do a sideways sum
            ImageProcessor projProc = new FloatProcessor( stack.getWidth(), stack.getSize() );
            for (int slice = 1; slice <= stack.getSize(); slice++) {
               for (int x = 0; x < stack.getWidth(); x++) {
                  float sum = 0.0f;
                  for (int y = 0; y < stack.getHeight(); y++) {
                      sum += stack.getProcessor(slice).get(x, y); 
                  }
                  projProc.setf(x, slice-1, sum);
               }
            }
            return new ImagePlus("XZProjection", projProc);
         }
         default:
            break;
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
