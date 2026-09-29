package org.micromanager.tileddataviewer.overlay;


import java.awt.Graphics;
import java.awt.Image;

/**
 * An overlay element that draws a raster image (e.g. a snapped microscope thumbnail) at a fixed
 * rectangle. Like the other overlay {@link Roi} classes, its x/y/width/height are in display
 * (screen) pixel coordinates; the canvas applies no transform to overlay ROIs.
 */
public class ImageRoi extends Roi {

   private final Image img_;

   /**
    * Creates an ImageRoi.
    *
    * @param x      left edge in display pixels
    * @param y      top edge in display pixels
    * @param width  width in display pixels
    * @param height height in display pixels
    * @param img    the image to draw (scaled to width x height); may be null
    */
   public ImageRoi(int x, int y, int width, int height, Image img) {
      super(x, y, width, height);
      img_ = img;
   }

   @Override
   public void draw(Graphics g) {
      if (img_ != null) {
         g.drawImage(img_, x, y, width, height, null);
      }
   }
}
