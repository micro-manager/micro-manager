/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package coordinates;

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;

/**
 *
 * @author Henry
 */
public class StageCoordinates {
   
   public final String label;
   public final Point2D.Double center;
   public final Point2D.Double[] corners;
   
   public StageCoordinates(String name, Point2D.Double stagePosCenter, int tileWidth, int tileHeight) {
      label = name;
      center = stagePosCenter;
      AffineTransform transform = AffineUtils.getAffineTransform(center.x, center.y);
      corners = new Point2D.Double[4];
      corners[0] = new Point2D.Double();
      corners[1] = new Point2D.Double();
      corners[2] = new Point2D.Double();
      corners[3] = new Point2D.Double();
      transform.transform(new Point2D.Double(-tileWidth / 2, -tileHeight / 2), corners[0]);
      transform.transform(new Point2D.Double(-tileWidth / 2, tileHeight / 2), corners[1]);
      transform.transform(new Point2D.Double(tileWidth / 2, tileHeight / 2), corners[2]);
      transform.transform(new Point2D.Double(tileWidth / 2, -tileHeight / 2), corners[3]);
   }
 
}
