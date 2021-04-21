package org.micromanager.internal.utils;

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;
import org.junit.Test;

public class MathFunctionsTest {

  @Test
  public void runAffineTest() {

    final double delta = 0.0000000001;
    Map<Point2D.Double, Point2D.Double> pointPairs = new HashMap<Point2D.Double, Point2D.Double>();

    // Create sample src and dest points:
    pointPairs.put(new Point2D.Double(1, 1), new Point2D.Double(18, 2));
    pointPairs.put(new Point2D.Double(1, 9), new Point2D.Double(2, 2));
    pointPairs.put(new Point2D.Double(9, 9), new Point2D.Double(2, 18));
    pointPairs.put(new Point2D.Double(9, 1), new Point2D.Double(18, 18));

    // Run the computation to be tested:
    AffineTransform affineTransform =
        MathFunctions.generateAffineTransformFromPointPairs(pointPairs);

    // Print input and output:
    // System.out.println(pointPairs);
    // System.out.println(affineTransform);

    // Check that affineTransform works correctly:
    for (Map.Entry pair : pointPairs.entrySet()) {
      Point2D.Double uPt = (Point2D.Double) pair.getKey();
      Point2D.Double vPt = (Point2D.Double) pair.getValue();
      Point2D.Double result = new Point2D.Double();
      affineTransform.transform(uPt, result);
      assertEquals(0.0, vPt.distance(result), delta);
    }
  }
}
