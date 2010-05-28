/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.acquisition;

import java.util.ArrayList;
import javax.swing.SwingUtilities;
import mmcorej.CMMCore;
import mmcorej.Metadata;
import org.micromanager.image5d.Image5D;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author arthur
 */
public class AcquisitionDisplay extends Thread {

   private final CMMCore core_;
   private int imgCount_;
   ArrayList<Image5D> i5dVector_;

   AcquisitionDisplay(CMMCore core) {
      core_ = core;
      i5dVector_ = new ArrayList<Image5D>();
   }

   public void run() {
      long t1 = System.currentTimeMillis();
      try {
         do {
            while (core_.getRemainingImageCount() > 0) {
               imgCount_++;
               try {
                  Metadata mdCopy = new Metadata();
                  Object img = core_.popNextImageMD(0, 0, mdCopy);
                  displayImage(img, mdCopy);
                  ReportingUtils.logMessage("time=" + mdCopy.getFrameIndex() + ", position=" +
                          mdCopy.getPositionIndex() + ", channel=" + mdCopy.getChannelIndex() +
                          ", slice=" + mdCopy.getSliceIndex()
                          + ", remaining images =" + core_.getRemainingImageCount());
               } catch (Exception ex) {
                  ReportingUtils.logError(ex);
               }
            }
            Thread.sleep(30);
         } while (!core_.acquisitionIsFinished() || core_.getRemainingImageCount() > 0);
      } catch (Exception ex2) {
         ReportingUtils.logError(ex2);
      }

      long t2 = System.currentTimeMillis();
      ReportingUtils.logMessage(imgCount_ + " images in " + (t2 - t1) + " ms.");
   }

   private void displayImage(Object img, Metadata m) {
      updateImage5Ds(m);
      Image5D i5d = i5dVector_.get(m.getPositionIndex());
      i5d.setPixels(img, 1 + m.getChannelIndex(), 1 + m.getSliceIndex(), 1 + m.getFrameIndex());
      if (i5d.getCurrentPosition()[4] == i5d.getNFrames() - 2) {
         i5d.setCurrentPosition(4, i5d.getNFrames() - 1);
      }
      if (i5d.getCurrentPosition()[4] == i5d.getNFrames() - 1) {
         i5d.setCurrentPosition(2,  m.getChannelIndex());
         i5d.setCurrentPosition(3,  m.getSliceIndex());
      }
      
      i5d.updateImage();
   }

   private void updateImage5Ds(Metadata m) {
      int posIndex = getMetadataIndex(m,"Position");
      int channelIndex = getMetadataIndex(m,"ChannelIndex");
      int sliceIndex = getMetadataIndex(m,"Slice");
      int frameIndex = getMetadataIndex(m,"Frame");

      if (posIndex == i5dVector_.size()) {
         addImage5D("MDA pos " + posIndex);
      }

      Image5D i5d = i5dVector_.get(posIndex);
      if (channelIndex >= i5d.getNChannels()) {
         i5d.expandDimension(2, channelIndex + 1, true);
      }
      if (sliceIndex >= i5d.getNSlices()) {
         i5d.expandDimension(3, sliceIndex + 1, true);
      }
      if (frameIndex >= i5d.getNFrames()) {
         i5d.expandDimension(4, frameIndex + 1, true);
      }
   }

   private int getMetadataIndex(Metadata m, String key) {
      return Integer.getInteger(m.getFrameData(key));
   }

   private void addImage5D(String title) {
      Image5D i5d = new Image5D(title, 0, 512, 512, 1, 1, 1, true);
      i5dVector_.add(i5d);
      i5d.show();
   }
}
