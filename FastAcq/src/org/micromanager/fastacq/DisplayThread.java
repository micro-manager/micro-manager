package org.micromanager.fastacq;
import ij.ImagePlus;
import ij.gui.ImageWindow;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

import java.awt.Color;

import mmcorej.CMMCore;

public class DisplayThread extends Thread {

   CMMCore core_;
   boolean stop_ = false;
   private ImageWindow imageWin_;
   
   public DisplayThread(CMMCore core) {
      core_ = core;
   }

   public void run() {
      // obtain camera name
      String camera = core_.getCameraDevice();
      openImageWindow();

      try {
         while (core_.deviceBusy(camera) && !stop_) {
            //System.out.println("Displaying current image, " + core_.getRemainingImageCount() + " images waiting in que.");
            //Thread.sleep(110);
            updateImage();
         }
      } catch (InterruptedException e) {
         // TODO Auto-generated catch block
         e.printStackTrace();
      } catch (Exception e) {
         // TODO Auto-generated catch block
         e.printStackTrace();
      }
   }
   
   synchronized void terminate() {
      stop_ = true;
   }
   
   public void updateImage() {
      try {

         // update image window
         core_.snapImage();
         Object img = core_.getLastImage();

         imageWin_.getImagePlus().getProcessor().setPixels(img);
         imageWin_.getImagePlus().updateAndDraw();
         imageWin_.getCanvas().paint(imageWin_.getCanvas().getGraphics());
                  
//         // update coordinate and pixel info in imageJ by simulating mouse move
//         Point pt = imageWin_.getCanvas().getCursorLoc();
//         imageWin_.getImagePlus().mouseMoved(pt.x, pt.y);
//         
      } catch (Exception e){
         // TODO Auto-generated catch block
         e.printStackTrace();
      }
   }
   
   private boolean openImageWindow(){
      try {
         ImageProcessor ip;
         long byteDepth = core_.getBytesPerPixel();
         if (byteDepth == 1){
            ip = new ByteProcessor((int)core_.getImageWidth(), (int)core_.getImageHeight());
         } else if (byteDepth == 2) {
            ip = new ShortProcessor((int)core_.getImageWidth(), (int)core_.getImageHeight());
         }
         else if (byteDepth == 0) {
            return false;
         }
         else {
            return false;
         }
         ip.setColor(Color.black);
         ip.fill();
         ImagePlus imp = new ImagePlus("FastAcq window", ip);
        
         imageWin_ = new ImageWindow(imp);                  
         
      } catch (Exception e){
         // TODO Auto-generated catch block
         e.printStackTrace();
         return false;
      }
      return true;
   }

}


