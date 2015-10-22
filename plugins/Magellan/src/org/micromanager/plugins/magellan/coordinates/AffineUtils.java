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
package org.micromanager.plugins.magellan.coordinates;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
import java.awt.geom.AffineTransform;
import java.util.TreeMap;
import java.util.prefs.Preferences;
import org.micromanager.plugins.magellan.misc.JavaUtils;
import org.micromanager.plugins.magellan.misc.Log;
import org.micromanager.MMStudio;

/**
 *
 * @author Henry
 */
public class AffineUtils {
   
   private static TreeMap<String, AffineTransform> affineTransforms_ = new TreeMap<String,AffineTransform>();
   
   public static String transformToString(AffineTransform transform) {
      double[] matrix = new double[4];
      transform.getMatrix(matrix);
      return matrix[0] +"_"+matrix[1]+"_"+matrix[2]+"_"+matrix[3];
   }
   
   public static AffineTransform stringToTransform(String s) {
      double[] mat = new double[4];
      String[] vals = s.split("_");
      for (int i = 0; i < 4; i ++) {
         mat[i] = Double.parseDouble(vals[i]);
      }
      return new AffineTransform(mat);
   }
   
   
   //called when an affine transform is updated
   public static void transformUpdated(String pixelSizeConfig, AffineTransform transform) {
      if (transform != null) {
         affineTransforms_.put(pixelSizeConfig, transform);
      }
   }
   
   //Only read from preferences one time, so that an inordinate amount of time isn't spent in native system calls
   public static AffineTransform getAffineTransform(String pixelSizeConfig, double xCenter, double yCenter) {
      try {
         AffineTransform transform = null;
         if (affineTransforms_.containsKey(pixelSizeConfig)) {
            transform = affineTransforms_.get(pixelSizeConfig);
            //copy transform so multiple referneces with different translations cause problems
            double[] newMat = new double[6];
            transform.getMatrix(newMat);
            transform = new AffineTransform(newMat);
         } else {
            //Get affine transform from prefs
            Preferences prefs = Preferences.userNodeForPackage(MMStudio.class);
            transform = JavaUtils.getObjectFromPrefs(prefs, "affine_transform_" + pixelSizeConfig, (AffineTransform) null);
            affineTransforms_.put(pixelSizeConfig, transform);
         }
         //set map origin to current stage position
         double[] matrix = new double[6];
         transform.getMatrix(matrix);
         matrix[4] = xCenter;
         matrix[5] = yCenter;
         return new AffineTransform(matrix);
      } catch (Exception ex) {
         Log.log(ex);
         Log.log("Couldnt get affine transform");
         return null;
      }
   }

 
}
