package org.micromanager.tileddataviewer.internal.gui;


import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.KeyListener;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelListener;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import javax.swing.JPanel;
import org.micromanager.tileddataviewer.internal.TiledDataViewer;
import org.micromanager.tileddataviewer.overlay.Overlay;
import org.micromanager.tileddataviewer.overlay.Roi;

public class ViewerCanvas {

   private volatile Image currentImage_;
   private volatile Overlay currentOverlay_ = new Overlay();
   private double scale_;
   private TiledDataViewer display_;
   private JPanel canvas_;
   // Cached BufferedImage version of the last rendered frame, for pixel lookups.
   private volatile BufferedImage renderedBuffer_;

   public ViewerCanvas(TiledDataViewer display) {
      canvas_ = createCanvas();
      display_ = display;

      //For recreating/resizing compositie image on window size change
      canvas_.addComponentListener(new ComponentAdapter() {
         @Override
         public void componentResized(ComponentEvent e) {
            display_.onCanvasResize(canvas_.getWidth(), canvas_.getHeight());
         }
      });
   }

   public void onDisplayClose() {
      for (ComponentListener l : canvas_.getComponentListeners()) {
         canvas_.removeComponentListener(l);
      }
      for (MouseListener l : canvas_.getMouseListeners()) {
         canvas_.removeMouseListener(l);
      }
      for (MouseMotionListener l : canvas_.getMouseMotionListeners()) {
         canvas_.removeMouseMotionListener(l);
      }
      for (KeyListener l : canvas_.getKeyListeners()) {
         canvas_.removeKeyListener(l);
      }
      for (MouseWheelListener l : canvas_.getMouseWheelListeners()) {
         canvas_.removeMouseWheelListener(l);
      }

      canvas_ = null;
      display_ = null;
   }

   /**
    * Set the size of the image displayed on screen, which is not neccesarily
    * the same as the image pixels read to create it.
    *
    * @param w
    * @param h
    */
   public void onCanvasResize(int w, int h) {
   }

   void updateDisplayImage(Image img, double scale) {
      currentImage_ = img;
      scale_ = scale;
      // Invalidate the cached buffer whenever a new frame arrives.
      // The BufferedImage copy is created lazily in getRenderedPixelRGB() only when needed.
      renderedBuffer_ = null;
   }

   /**
    * Returns the RGB values at the given canvas coordinates from the last rendered frame.
    * Returns null if no frame has been rendered yet or coordinates are out of bounds.
    * The array contains [R, G, B] values in the range 0–255.
    * These are the display-mapped values (post contrast/gamma), which is sufficient
    * for computing white-balance ratios since only relative R:G:B proportions matter.
    */
   public int[] getRenderedPixelRGB(int canvasX, int canvasY) {
      Image img = currentImage_;
      double scale = scale_;
      if (img == null || scale <= 0) {
         return null;
      }
      // Build or reuse the cached BufferedImage copy (created lazily on first pixel lookup).
      BufferedImage buf = renderedBuffer_;
      if (buf == null) {
         if (img instanceof BufferedImage) {
            buf = (BufferedImage) img;
         } else {
            int w = img.getWidth(null);
            int h = img.getHeight(null);
            if (w <= 0 || h <= 0) {
               return null;
            }
            buf = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics g = buf.getGraphics();
            g.drawImage(img, 0, 0, null);
            g.dispose();
         }
         renderedBuffer_ = buf;
      }
      // The AffineTransform in paint() scales the image UP by scale when drawing it.
      // canvas pixel = renderedImagePixel * scale → renderedImagePixel = canvasPixel / scale
      int px = (int) Math.floor(canvasX / scale);
      int py = (int) Math.floor(canvasY / scale);
      if (px < 0 || py < 0 || px >= buf.getWidth() || py >= buf.getHeight()) {
         return null;
      }
      int rgb = buf.getRGB(px, py);
      return new int[]{(rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF};
   }

   void updateOverlay(Overlay overlay) {
      synchronized (currentOverlay_) {
         currentOverlay_.clear();
         for (int i = 0; i < overlay.size(); i++) {
            currentOverlay_.add(overlay.get(i));
         }
      }
   }

   public JPanel getCanvas() {
      return canvas_;
   }

   private JPanel createCanvas() {
      return new JPanel() {
         @Override
         public void paint(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            AffineTransform af = new AffineTransform(scale_, 0, 0, scale_, 0, 0);
            g2.drawImage(currentImage_, af, canvas_);
            synchronized (currentOverlay_) {
               if (currentOverlay_ != null) {
                  for (int i = 0; i < currentOverlay_.size(); i++) {
                     Roi roi = currentOverlay_.get(i);
                     roi.drawOverlay(g);
                  }
               }
            }

         }

         public void update(Graphics g) {
            paint(g);
         }
      };
   }

}
