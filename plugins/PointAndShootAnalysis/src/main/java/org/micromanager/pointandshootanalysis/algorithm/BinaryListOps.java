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
   * Dilate a set of points with "4 neighbor rule" I originally had this with Point2D_I32, but
   * somehow that resulted in adding duplicate Points. I don't understand why, since the equals
   * method of POint2D_I32 looks fine.
   *
   * @param input Set of points to be dilated
   * @param width width of the image/dataset
   * @param height height of the image/dataset
   * @return Set with dilated points
   */
  public static Set<Point> dilate4(Set<Point> input, final int width, final int height) {
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

  public static <E> List<E> setToList(Set<E> input) {
    List<E> output = new ArrayList<>();
    input.forEach(
        (pixel) -> {
          output.add(pixel);
        });
    return output;
  }

  public static <E> Set<E> listToSet(List<E> input) {
    Set<E> output = new HashSet<>();
    input.forEach(
        (p) -> {
          output.add(p);
        });
    return output;
  }

  public static <E> Set<E> combineSets(Set<E>... input) {
    Set<E> output = new HashSet<>();
    for (Set<E> mySet : input) {
      for (E p : mySet) {
        output.add(p);
      }
    }
    return output;
  }
}
