package org.micromanager.projector.internal;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.Assert;
import org.junit.Test;

/**
 * Row-then-column ordering of calibration check targets.
 */
public class SortByRowsTest {

   @Test
   public void chainedPointsDoNotBreakSorting() {
      // pairwise-tolerance comparator would give A < B < C < A here (tolerance 25)
      List<Point2D.Double> pts = Arrays.asList(new Point2D.Double(200, 340),
            new Point2D.Double(300, 320), new Point2D.Double(400, 300));
      List<Point2D.Double> sorted = Calibrator.sortByRows(pts, 25);
      Assert.assertEquals(3, sorted.size());
      Assert.assertEquals(200, sorted.get(0).x, 0);
      Assert.assertEquals(400, sorted.get(2).x, 0);
   }

   @Test
   public void rotatedGridSortsIntoRows() {
      final int n = 8;
      final double angle = Math.toRadians(3);
      List<Point2D.Double> pts = new ArrayList<>();
      for (int r = 0; r < n; r++) {
         for (int c = 0; c < n; c++) {
            double x = 50 + 120 * c;
            double y = 50 + 120 * r;
            pts.add(new Point2D.Double(x * Math.cos(angle) - y * Math.sin(angle) + 100,
                  x * Math.sin(angle) + y * Math.cos(angle)));
         }
      }
      List<Point2D.Double> expected = new ArrayList<>(pts);
      Collections.shuffle(pts, new Random(42));
      List<Point2D.Double> sorted = Calibrator.sortByRows(pts, 1000 / 40.0);
      Assert.assertEquals(expected, sorted);
   }
}
