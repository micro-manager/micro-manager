
package org.micromanager.pointandshootanalysis.display;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.micromanager.data.Image;
import org.micromanager.display.DisplaySettings;
import org.micromanager.display.overlay.AbstractOverlay;

/**
 *
 * @author nico
 */
public class Overlay extends AbstractOverlay {
   private final String TITLE = "Point and Shoot Overlay";
   private final List<Map<Integer, Point>> tracks_;
   private final Map<Integer, List<Point>> tracksIndexedByFrame_;
   private final int symbolLenght_ = 30;
   
   
   public Overlay(List<Map<Integer, Point>> tracks) {
      tracks_ = tracks;
      // index tracks by frame for quick look up when we need it
      tracksIndexedByFrame_ = new TreeMap<>();
      tracks_.forEach((Map<Integer, Point> track) -> {
         track.entrySet().forEach((entry) -> {
            List<Point> pointsInFrame = tracksIndexedByFrame_.get(entry.getKey());
            if (pointsInFrame == null) {
               pointsInFrame = new ArrayList<>();
            }
            pointsInFrame.add(entry.getValue());
            tracksIndexedByFrame_.put(entry.getKey(), pointsInFrame);
         });
      });
      super.setVisible(true);
   }
   
   @Override
   public String getTitle() {
      return TITLE;
   }
   
   /**
    * {@inheritDoc}
    * <p>
    * 
    */
   @Override
   public void paintOverlay(Graphics2D g, Rectangle screenRect,
         DisplaySettings displaySettings,
         List<Image> images, Image primaryImage,
         Rectangle2D.Float imageViewPort)
   {
      // TODO: make sure this is our dataviewer
      Integer frame = primaryImage.getCoords().getTimePoint();
      if (tracksIndexedByFrame_.get(frame) != null) {
         g.setColor(Color.RED);
         final double zoomRatio = imageViewPort.width / screenRect.width;
         final int halfLength = symbolLenght_ / 2;

         // Draw the pattern in image pixel coordinates by applying a transform
         Graphics2D gTfm = (Graphics2D) g.create();
         gTfm.transform(AffineTransform.
                 getScaleInstance(1.0 / zoomRatio, 1.0 / zoomRatio));
         gTfm.transform(AffineTransform.
                 getTranslateInstance(-imageViewPort.x, -imageViewPort.y));
         // Stroke width should be 1.0 in screen coordinates
         gTfm.setStroke(new BasicStroke((float) zoomRatio));

         tracksIndexedByFrame_.get(frame).forEach((p) -> {
            drawMarker1(gTfm, p, halfLength, halfLength / 2);
         });
      }
   }
   
   
   /**
    * Draws:
    *      |
    *    -   -
    *      |
    * 
    * @param g - graphics environment to draw on
    * @param p - 
    * @param width1
    * @param width2 
    */
   
   private void drawMarker1(Graphics2D g, Point p, int width1, int width2) {
      g.draw(new Line2D.Float(p.x, p.y - width1, p.x, p.y - width1 + width2));
      g.draw(new Line2D.Float(p.x, p.y + width1, p.x, p.y + width1 - width2));
      g.draw(new Line2D.Float(p.x - width1, p.y, p.x - width1 + width2, p.y));
      g.draw(new Line2D.Float(p.x + width1, p.y, p.x + width1 - width2, p.y));
   }
}
