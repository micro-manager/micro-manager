package edu.ucsf.valelab.gaussianfit.datasetdisplay;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import org.micromanager.data.Image;
import org.micromanager.display.DisplaySettings;
import org.micromanager.display.overlay.AbstractOverlay;

/**
 * @author nico
 */
public class SpotOverlay extends AbstractOverlay {

   List<Square> squares_;

   private class Square {

      int x_, y_, width_;

      public Square(int x, int y, int width) {
         x_ = x;
         y_ = y;
         width_ = width;
      }

      public int getX() {
         return x_;
      }

      public int getY() {
         return y_;
      }

      public int getWidth() {
         return width_;
      }
   }

   public SpotOverlay() {
      squares_ = new ArrayList<Square>();
   }

   @Override
   public String getTitle() {
      return "SpotOverlay";
   }

   /**
    * {@inheritDoc}
    * <p>
    * This default implementation draws nothing. Override to draw the overlay graphics.
    */
   @Override
   public void paintOverlay(final Graphics2D g, final Rectangle screenRect,
         DisplaySettings displaySettings,
         List<Image> images, Image primaryImage,
         Rectangle2D.Float imageViewPort) {
      g.setColor(Color.YELLOW);

      Double umPerImagePixel = primaryImage.getMetadata().getPixelSizeUm();

      final double zoomRatio = screenRect.width / imageViewPort.width;

      for (Square s : squares_) {
         int zoomedWidth = (int) (zoomRatio * s.getWidth());
         int halfWidth = (int) (0.5 * zoomedWidth);
         int x = (int) (zoomRatio * (s.getX() - imageViewPort.x)) - halfWidth;
         int y = (int) (zoomRatio * (s.getY() - imageViewPort.y)) - halfWidth;
         g.drawRect(x, y, zoomedWidth, zoomedWidth);
      }

   }

   public void addSquare(int x, int y, int width) {
      squares_.add(new Square(x, y, width));
   }

   public void clearSquares() {
      squares_.clear();
   }

}
