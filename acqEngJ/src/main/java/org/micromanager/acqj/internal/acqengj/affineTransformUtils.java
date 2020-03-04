///////////////////////////////////////////////////////////////////////////////
// AUTHOR:       Henry Pinkard, henry.pinkard@gmail.com
//
// COPYRIGHT:    University of California, San Francisco, 2015
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
//
package org.micromanager.acqj.internal.acqengj;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
import java.awt.geom.AffineTransform;
import mmcorej.DoubleVector;
import org.micromanager.acqj.internal.acqengj.Engine;

public class affineTransformUtils {

   public static String transformToString(AffineTransform transform) {
      double[] matrix = new double[4];
      transform.getMatrix(matrix);
      return matrix[0] + "_" + matrix[1] + "_" + matrix[2] + "_" + matrix[3];
   }

   public static AffineTransform stringToTransform(String s) {
       if (s.equals("Undefined")) {
           return null;
       }
      double[] mat = new double[4];
      String[] vals = s.split("_");
      for (int i = 0; i < 4; i++) {
         mat[i] = NumUtils.parseDouble(vals[i]);
      }
      return new AffineTransform(mat);
   }
 
   public static boolean isAffineTransformDefined() {
       try {
           DoubleVector v = Engine.getCore().getPixelSizeAffine(true);
           for (int i = 0; i < v.size(); i++) {
               if (v.get(i) != 0.0) {
                   return true;
               }
           }
           return false;
       } catch (Exception ex) {
           throw new RuntimeException(ex);
       }
   }
   
   public static AffineTransform getAffineTransform(double xCenter, double yCenter) {
      try {
         AffineTransform transform = doubleToAffine(Engine.getCore().getPixelSizeAffineByID(Engine.getCore().getCurrentPixelSizeConfig()));
         //set map origin to current stage position
         double[] matrix = new double[6];
         transform.getMatrix(matrix);
         matrix[4] = xCenter;
         matrix[5] = yCenter;
         return new AffineTransform(matrix);
      } catch (Exception ex) {
         throw new RuntimeException(ex);
      }
   }
   
   public static final AffineTransform doubleToAffine(DoubleVector atf) {
      if (atf.size() != 6) {
          double[] flatMatrix = {0.0, 0.0, 0.0, 0.0, 0.0, 0.0};
          return new AffineTransform(flatMatrix);
      }
      double[] flatMatrix = {atf.get(0), atf.get(3), atf.get(1),
         atf.get(4), atf.get(2), atf.get(5)};
      return new AffineTransform(flatMatrix);
   }

}
