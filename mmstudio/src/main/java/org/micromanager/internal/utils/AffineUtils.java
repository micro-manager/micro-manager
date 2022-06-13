///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman, 2018
//
// COPYRIGHT:    Regents of the University of California, 2018
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

package org.micromanager.internal.utils;

import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import mmcorej.DoubleVector;
import org.apache.commons.math.util.MathUtils;

/**
 * Utilities specific to the use of affine transforms in Micro-Manager.
 * Mainly concerns itself with translation between the different data formats
 * for affine transform
 *
 * @author nico
 */
public class AffineUtils {

   /**
    * No op affine transform.
    *
    * @return Returns the No op affine transform as a DoubleVector (for consumption by the core).
    */
   public static DoubleVector noTransform() {
      DoubleVector affineTransform = new DoubleVector(6);
      for (int i = 1; i < 6; i++) {
         affineTransform.set(i, 0.0);
      }
      affineTransform.set(0, 1.0);
      affineTransform.set(4, 1.0);
      return affineTransform;
   }

   /**
    * Converts the affine transform from a DoubleVector (as supplied by the Core) to a
    * Java AffineTransform.
    *
    * @param atf Affine Transform as a DoubleVector
    * @return Affine Transform as a Java Affine Transform
    */
   public static AffineTransform doubleToAffine(DoubleVector atf) {
      if (atf.size() != 6) {
         double[] flatMatrix = {0.0, 0.0, 0.0, 0.0, 0.0, 0.0};
         return new AffineTransform(flatMatrix);
      }
      double[] flatMatrix = {atf.get(0), atf.get(3), atf.get(1),
            atf.get(4), atf.get(2), atf.get(5)};
      return new AffineTransform(flatMatrix);
   }

   /**
    * Converts Affine TRansform from a Java Affine Transform to a DoubleVector.
    *
    * @param atf Java Affine Transform
    * @return Corresponding Affine Transform as a DoubleVector
    */
   public static DoubleVector affineToDouble(AffineTransform atf) {
      DoubleVector out = new DoubleVector(6);
      out.set(0, atf.getScaleX());
      out.set(1, atf.getShearX());
      out.set(2, atf.getTranslateX());
      out.set(3, atf.getShearY());
      out.set(4, atf.getScaleY());
      out.set(5, atf.getTranslateY());
      return out;
   }

   /**
    * Convert affine transform to human-interpretable measurements of rotation, scale, and shear.
    *
    * @param transform - input affine transform
    * @return {xScale, yScale, rotationDeg, shear}
    */
   public static double[] affineToMeasurements(AffineTransform transform) {
      try {
         //[T] = [Rot][Sca][She]
         //{ m00 m10 m01 m11 m02 m12 }
         double[] matrix = new double[6];
         transform.getMatrix(matrix);
         double angle = Math.atan(matrix[1] / matrix[0]); //radians
         //figure out which quadrant
         //sin && cos
         if (matrix[1] > 0 && matrix[0] >= 0) {
            //first quadrant, make sure angle is positive (in case ts exactly 90 degrees
            angle = Math.abs(angle);
         } else if (matrix[1] > 0 && matrix[0] < 0) {
            //second quadrant
            angle = Math.abs(angle - 2 * (Math.PI / 2 + angle));
         } else if (matrix[1] <= 0 && matrix[0] >= 0) {
            //fourth quadrant, do nothing
         } else {
            //third quadrant, subtract 90 degrees
            angle += 2 * (Math.PI / 2 - angle);
            angle *= -1; //make sure angle is negative
         }

         //get shear by reversing the rotation, then reversing the scaling
         AffineTransform at = AffineTransform.getRotateInstance(angle).createInverse();
         at.concatenate(transform); //take out the rotations
         //get scales
         double[] newMat = new double[6];
         at.getMatrix(newMat);
         double xScale = Math.sqrt(newMat[0] * newMat[0] + newMat[1] * newMat[1])
               * (newMat[0] > 0 ? 1.0 : -1.0);
         double yScale = Math.sqrt(newMat[2] * newMat[2] + newMat[3] * newMat[3])
               * (newMat[3] > 0 ? 1.0 : -1.0);
         AffineTransform at2 = AffineTransform.getScaleInstance(xScale, yScale).createInverse();
         at2.concatenate(at); // take out the scale
         //should now be left with shear transform;
         double shear = at2.getShearX();
         double rotationDeg = angle / Math.PI * 180.0;
         return new double[] {xScale, yScale, rotationDeg, shear};
      } catch (NoninvertibleTransformException ex) {
         return new double[] {0, 0, 0, 0};
      }
   }

   public static double deducePixelSize(AffineTransform atf) {
      return MathUtils.round(Math.sqrt(Math.abs(atf.getDeterminant())), 4);
   }

}