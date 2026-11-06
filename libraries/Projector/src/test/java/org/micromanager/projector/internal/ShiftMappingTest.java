package org.micromanager.projector.internal;

import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;
import org.micromanager.projector.Mapping;
import org.micromanager.projector.ProjectorActions;

/**
 * Offset correction: a shifted mapping aims at p - delta where the original aimed at p.
 */
public class ShiftMappingTest {

   @Test
   public void shiftThenUnshiftRestores() {
      Polygon cell = new Polygon(new int[] {0, 0, 500, 500}, new int[] {0, 500, 500, 0}, 4);
      AffineTransform t = new AffineTransform(0.9, 0.05, -0.04, 1.1, 12.5, -7.0);
      Map<Polygon, AffineTransform> map = new HashMap<>();
      map.put(cell, t);
      Rectangle roi = new Rectangle(0, 0, 512, 512);
      Mapping m = new Mapping.Builder().setMap(map).setROI(roi).setBinning(1).build();

      Point2D.Double delta = new Point2D.Double(-1.0, 0.25);
      Mapping shifted = Calibrator.shiftMapping(m, delta);
      Point2D.Double p = new Point2D.Double(200, 300);
      Point2D.Double got = ProjectorActions.transformPoint(shifted,
            new Point2D.Double(p.x, p.y), roi, 1);
      Point2D.Double want = (Point2D.Double) t.transform(
            new Point2D.Double(p.x - delta.x, p.y - delta.y), null);
      Assert.assertEquals(want.x, got.x, 1e-9);
      Assert.assertEquals(want.y, got.y, 1e-9);

      Mapping back = Calibrator.shiftMapping(shifted, new Point2D.Double(-delta.x, -delta.y));
      Point2D.Double orig = ProjectorActions.transformPoint(back,
            new Point2D.Double(p.x, p.y), roi, 1);
      Point2D.Double ref = (Point2D.Double) t.transform(p, null);
      Assert.assertEquals(ref.x, orig.x, 1e-9);
      Assert.assertEquals(ref.y, orig.y, 1e-9);
   }
}
