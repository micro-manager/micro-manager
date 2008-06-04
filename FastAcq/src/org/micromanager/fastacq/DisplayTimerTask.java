package org.micromanager.fastacq;
import ij.ImagePlus;
import ij.gui.ImageWindow;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

import java.awt.Color;
import java.util.TimerTask;

import mmcorej.CMMCore;

public class DisplayTimerTask extends TimerTask {

   private CMMCore core_;
   private ImageWindow imageWin_;
   private String cameraName_;
   
   public DisplayTimerTask(CMMCore core, ImageWindow imgWin) {
      core_ = core;
      imageWin_ = imgWin;
      // obtain camera name
      cameraName_ = core_.getCameraDevice();
   }

   public void run() {

      try {
         if (core_.deviceBusy(cameraName_)) {
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
   
   public void updateImage() {
      try {
         // update image window
         if (imageWin_ == null || imageWin_.isClosed())
            return;
         
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
}


