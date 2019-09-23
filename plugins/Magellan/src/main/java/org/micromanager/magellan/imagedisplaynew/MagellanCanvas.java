package org.micromanager.magellan.imagedisplaynew;

import com.google.common.eventbus.Subscribe;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import javax.swing.JPanel;
import org.micromanager.magellan.imagedisplaynew.events.CanvasResizeEvent;
import org.micromanager.magellan.imagedisplaynew.events.DisplayClosingEvent;

class MagellanCanvas {

   private volatile BufferedImage currentImage_;
   private int width_, height_;
   private double scale_;
   private MagellanDisplayController display_;
   private JPanel canvas_;

   public MagellanCanvas(MagellanDisplayController display) {
      canvas_ = createCanvas();
      //TODO: update size on 
      display.registerForEvents(this);
      display_ = display;

      //For recreating/resizing compositie image on window size change
      canvas_.addComponentListener(new ComponentAdapter() {
         @Override
         public void componentResized(ComponentEvent e) {
            display_.postEvent(new CanvasResizeEvent(canvas_.getWidth(), canvas_.getHeight()));
         }
      });
   }

   @Subscribe
   public void onDisplayClose(DisplayClosingEvent e) {
      for (ComponentListener l : canvas_.getComponentListeners()) {
         canvas_.removeComponentListener(l);
      }
      canvas_ = null;
      display_.unregisterForEvents(this);
      display_ = null;
   }

   private void computeScale() {
      if (currentImage_ == null) {
         return;
      }
      double wScale = width_ / (double) currentImage_.getWidth();
      double hScale = height_ / (double) currentImage_.getHeight();
      //do the bigger scaling so image fills the whole canvas
      scale_ = Math.max(wScale, hScale);
   }

   /**
    * Set the size of the image displayed on screen, which is not neccesarily
    * the same as the image pixels read to create it
    *
    * @param w
    * @param h
    */
   @Subscribe
   public void onCanvasResize(final CanvasResizeEvent e) {
      width_ = e.w;
      height_ = e.h;
      computeScale();
   }

   void updateDisplayImage(BufferedImage img) {
      currentImage_ = img;
   }
   
   public JPanel getCanvas() {
      return canvas_;
   }
   
   private JPanel createCanvas() {
      return new JPanel() {
         @Override
         public void paint(Graphics g) {

            Graphics2D g2 = (Graphics2D) g;

            AffineTransform xform = new AffineTransform(scale_, 0, 0, scale_, 0, 0);
            g2.drawImage(currentImage_, xform, null);
            //TODO implement double buffering to stop flickering
            
//      if (volatileImg_.validate(getGraphicsConfiguration()) == VolatileImage.IMAGE_INCOMPATIBLE)
//          {
//              // old vImg doesn't work with new GraphicsConfig; re-create it
//              volatileImg_ = createVolatileImage(this.getWidth(), this.getHeight());
//          }
//      
//      // Main rendering loop. Volatile images may lose their contents. 
//      // This loop will continually render to (and produce if neccessary) volatile images
//      // until the rendering was completed successfully.
//      do {
//
//         // Validate the volatile image for the graphics configuration of this 
//         // component. If the volatile image doesn't apply for this graphics configuration 
//         // (in other words, the hardware acceleration doesn't apply for the new device)
//         // then we need to re-create it.
//         GraphicsConfiguration gc = this.getGraphicsConfiguration();
//         int valCode = volatileImg_.validate(gc);
//
//         // This means the device doesn't match up to this hardware accelerated image.
//         if (valCode == VolatileImage.IMAGE_INCOMPATIBLE) {
//            createBackBuffer(); // recreate the hardware accelerated image.
//         }
//
//         Graphics offscreenGraphics = volatileImg_.getGraphics();
//         offscreenGraphics.drawImage(currentImage_, 0, 0, null);
//         //TODO:other rendering
//         
//
//         // paint back buffer to main graphics
//         g.drawImage(volatileImg_, 0, 0, this);
//         // Test if content is lost   
//      } while (volatileImg_.contentsLost());
         }
      };
   }

}
