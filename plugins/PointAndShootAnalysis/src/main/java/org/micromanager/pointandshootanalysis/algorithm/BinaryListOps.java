
package org.micromanager.pointandshootanalysis.algorithm;

import georegression.struct.point.Point2D_I32;
import java.awt.Point;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Dilation/Erosion/combination option on lists of pixels
 * 
 * @author nico
 */
public class BinaryListOps {
   
   /**
    * Dilate a set of points with "4 neighbor rule"
    * I originally had this with Point2D_I32, but somehow that resulted in 
    * adding duplicate Points.  I don't understand why, since the equals method
    * of POint2D_I32 looks fine.
    * 
    * @param input Set of points to be dilated
    * @param width width of the image/dataset
    * @param height height of the image/dataset
    * @return Set with dilated points
    */
   public static Set<Point> dilate4(Set<Point> input, 
           final int width, final int height) {
      Set<Point> output = new HashSet<>();
      for (Point pixel : input) {
         if (!output.contains(pixel)) {
            output.add(pixel);
         }
         if (pixel.x > 1) {
            output.add(new Point(pixel.x - 1, pixel.y));
         }
         if (pixel.x < width - 2) {
            output.add(new Point(pixel.x + 1, pixel.y));
         }
         if (pixel.y > 1) {
            output.add(new Point(pixel.x, pixel.y - 1));
         }
         if (pixel.y < height - 2) {
            output.add(new Point(pixel.x, pixel.y + 1));
         }
      }
      return output;
   }
   
   public static List<Point2D_I32> setToList(Set<Point> input) {
      List<Point2D_I32> output = new ArrayList<>();
      for (Point pixel : input) {
         Point2D_I32 np = new Point2D_I32(pixel.x, pixel.y);
         output.add(np);
      }
      return output;
   }
   
   public static Set<Point> listToSet(List<Point2D_I32> input) {
      Set<Point> output = new HashSet<>();
      for (Point2D_I32 p : input) {
         output.add(new Point(p.x, p.y));
      }
      return output;
   }
   
   public static Set<Point> combineSets(Set<Point>... input) {
      Set<Point> output = new HashSet<>();
      for (Set<Point> mySet : input) {
         for (Point p : mySet) {
            output.add(p);
         }
      }
      return output;   
   }
   
}
