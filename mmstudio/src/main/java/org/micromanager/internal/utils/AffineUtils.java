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
import mmcorej.DoubleVector;
import org.apache.commons.math.util.MathUtils;

/**
 * Utilities specific to the use of affine transforms in Micro-Manager
 * Mainly concerns itself with translation between the different data formats
 * for affine transform
 * @author nico
 */
public class AffineUtils {

   public static final DoubleVector noTransform() {
      DoubleVector affineTransform = new DoubleVector(6);
      for (int i = 1; i < 6; i++) {
         affineTransform.set(i, 0.0);
      }
      affineTransform.set(0, 1.0);
      affineTransform.set(4, 1.0);
      return affineTransform;
   }
   
   public static final AffineTransform doubleToAffine(DoubleVector atf) {
      double[] flatMatrix = {atf.get(0), atf.get(3), atf.get(1),
         atf.get(4), atf.get(2), atf.get(5)};
      return new AffineTransform(flatMatrix);
   }
   
   public static final DoubleVector affineToDouble(AffineTransform atf) {
      DoubleVector out = new DoubleVector(6);
      out.set(0, atf.getScaleX());
      out.set(1, atf.getShearX());
      out.set(2, atf.getTranslateX());
      out.set(3, atf.getShearY());
      out.set(4, atf.getScaleY());
      out.set(5, atf.getTranslateY());
      return out;
   }
   
   public static double deducePixelSize(AffineTransform atf) {
      return MathUtils.round( Math.sqrt( 
              Math.abs(atf.getDeterminant())), 4);
   }
   
}
