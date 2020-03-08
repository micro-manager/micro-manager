package org.micromanager.ndviewer.internal.gui;


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
import org.micromanager.multiresviewer.NDViewer;
import org.micromanager.ndviewer.overlay.Overlay;
import org.micromanager.ndviewer.overlay.Roi;

public class ViewerCanvas {

   private volatile Image currentImage_;
   private volatile Overlay currentOverlay_ = new Overlay();
   private int width_, height_;
   private double scale_;
   private NDViewer display_;
   private JPanel canvas_;

   public ViewerCanvas(NDViewer display) {
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

//   private void computeScale() {
//      if (currentImage_ == null) {
//         return;
//      }
//      double wScale = width_ / (double) currentImage_.getWidth();
//      double hScale = height_ / (double) currentImage_.getHeight();
//      //do the bigger scaling so image fills the whole canvas
//      scale_ = Math.max(wScale, hScale);
//   }
   
   /**
    * Set the size of the image displayed on screen, which is not neccesarily
    * the same as the image pixels read to create it
    *
    * @param w
    * @param h
    */
   public void onCanvasResize(int w, int h) {
      width_ = w;
      height_ = h;
//      computeScale();
   }

   void updateDisplayImage(Image img, double scale) {
      currentImage_ = img;
      scale_ = scale;
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
