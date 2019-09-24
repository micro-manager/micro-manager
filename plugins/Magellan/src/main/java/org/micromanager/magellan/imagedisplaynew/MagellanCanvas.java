package org.micromanager.magellan.imagedisplaynew;

import com.google.common.eventbus.Subscribe;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import javax.swing.JPanel;
import org.micromanager.magellan.imagedisplaynew.events.CanvasResizeEvent;
import org.micromanager.magellan.imagedisplaynew.events.DisplayClosingEvent;

class MagellanCanvas {

   
   // The object we will use to write with instead of the standard screen graphics
     Graphics bufferGraphics;
     // The image that will contain everything that has been drawn on
     // bufferGraphics.
     Image offscreen; 
          // To get the width and height of the applet.
     Dimension dim; 
     
     
   
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

//      bufferGraphics = currentImage_.getGraphics();
   }

   public JPanel getCanvas() {
      return canvas_;
   }

   private JPanel createCanvas() {
      return new JPanel() {
         @Override
         public void paint(Graphics g) {
//            Graphics2D g2 = (Graphics2D) currentImage_.getGraphics();
            

//            bufferGraphics.clearRect(0,0,this.getWidth(), this.getHeight());
//            bufferGraphics.drawImage(currentImage_, 0, 0, canvas_);


            Graphics2D g2 = (Graphics2D) g;
            g2.drawImage(currentImage_, 0, 0, canvas_);

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

         public void update(Graphics g) {
            paint(g);
         }
      };
   }

}
