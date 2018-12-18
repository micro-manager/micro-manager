
package org.micromanager.pointandshootanalysis.display;

import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
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
   
   
   public Overlay(List<Map<Integer, Point>> tracks) {
      tracks_ = tracks;
      // index tracks by frame for quick look up when we need it
      tracksIndexedByFrame_ = new TreeMap<Integer, List<Point>>();
      for (Map<Integer, Point> track : tracks_) {
         for (Entry<Integer, Point> entry : track.entrySet()) {
            List<Point> pointsInFrame = tracksIndexedByFrame_.get(entry.getKey());
            if (pointsInFrame == null) {
               pointsInFrame = new ArrayList<Point>();
            }
            pointsInFrame.add(entry.getValue());
            tracksIndexedByFrame_.put(entry.getKey(), pointsInFrame);
         }
      }
   }
   
   @Override
   public String getTitle() {
      return TITLE;
   }
   
   /**
    * {@inheritDoc}
    * <p>
    * This default implementation draws nothing. Override to draw the overlay
    * graphics.
    */
   @Override
   public void paintOverlay(Graphics2D graphicsContext, Rectangle screenRect,
         DisplaySettings displaySettings,
         List<Image> images, Image primaryImage,
         Rectangle2D.Float imageViewPort)
   {
      Integer frame = primaryImage.getCoords().getTimePoint();
      for (Point p : tracksIndexedByFrame_.get(frame)) {
         // TODO: draw indicator for point p
      }
   }
}
