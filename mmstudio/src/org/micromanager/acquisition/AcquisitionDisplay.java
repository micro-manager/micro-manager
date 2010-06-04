/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.acquisition;

import java.util.ArrayList;
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
   boolean ramOnly_ = false;

   AcquisitionDisplay(CMMCore core) {
      core_ = core;
      i5dVector_ = new ArrayList<Image5D>();
   }

   public void setRamOnly(boolean ramOnly) {
      ramOnly_ = ramOnly;
   }

   public void run() {
      if (ramOnly_) {
         runInRam();
      } else {
         runFromDisk();
      }
   }

   public void runFromDisk() {
      try {
         Metadata mdCopy = new Metadata();
         Metadata lastMD;
         int images;
         int lastImages = -1;
         while (true) {
            images = core_.getRemainingImageCount();
            if (lastImages == -1 || (images - lastImages) == 0) {
               lastImages = images;
               Thread.sleep(30);
            } else {
               break;
            }
         }

         do  {
            lastMD = mdCopy;
            Object img = core_.getLastImageMD(0, 0, mdCopy);
            if (sameFrame(lastMD, mdCopy)) {
               Thread.sleep(30);
            } else {
               displayImage(img, mdCopy);
            }
         } while (!core_.acquisitionIsFinished());
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
         ex.printStackTrace();
      }

   }

   public void runInRam() {
      long t1 = System.currentTimeMillis();
      try {
         do {
            while (core_.getRemainingImageCount() > 0) {
               imgCount_++;
               try {
                  Metadata mdCopy = new Metadata();
                  Object img = core_.popNextImageMD(0, 0, mdCopy);
                  displayImage(img, mdCopy);
                  //    ReportingUtils.logMessage("time=" + mdCopy.getFrameData("Frame") + ", position=" +
                  //            mdCopy.getPositionIndex() + ", channel=" + mdCopy.getChannelIndex() +
                  //            ", slice=" + mdCopy.getSliceIndex()
                  //            + ", remaining images =" + core_.getRemainingImageCount());
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
      int posIndex = getMetadataIndex(m, "Position");
      int channelIndex = getMetadataIndex(m, "ChannelIndex");
      int sliceIndex = getMetadataIndex(m, "Slice");
      int frameIndex = getMetadataIndex(m, "Frame");
      updateImage5Ds(posIndex, channelIndex, sliceIndex, frameIndex);

      System.err.println("posIndex: "+posIndex);

      Image5D i5d = i5dVector_.get(posIndex);
      i5d.setPixels(img, 1 + channelIndex, 1 + sliceIndex, 1 + frameIndex);
      if (i5d.getCurrentPosition()[4] == i5d.getNFrames() - 2) {
         i5d.setCurrentPosition(4, i5d.getNFrames() - 1);
      }
      if (i5d.getCurrentPosition()[4] == i5d.getNFrames() - 1) {
         i5d.setCurrentPosition(2, channelIndex);
         i5d.setCurrentPosition(3, sliceIndex);
      }

      i5d.updateImage();
   }

   private void updateImage5Ds(int posIndex, int channelIndex, int sliceIndex, int frameIndex) {
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
      if (m.getFrameData().has_key(key)) {
         String val = m.getFrameData(key);
         if (val == null || val.length() == 0)
            return -1;
         else
            System.out.println("frameData[\"" + key + "\"] = \"" + val + "\"");
            int result;
            try {
               result = Integer.parseInt(val);
            } catch (Exception e) {
               e.printStackTrace();
               System.out.println("error. now val = \"" + val + "\"");
               result = -1;
            }
            return result;
      } else {
         return -1;
      }
   }

   private void addImage5D(String title) {
      Image5D i5d = new Image5D(title, 0, 512, 512, 1, 1, 1, true);
      i5dVector_.add(i5d);
      i5d.show();
   }

   private boolean sameFrame(Metadata lastMD, Metadata mdCopy) {
      if (lastMD == null || mdCopy == null) {
         return false;
      }
      boolean same = true;
      same = same && (getMetadataIndex(lastMD, "Position") == getMetadataIndex(mdCopy, "Position"));
      same = same && (getMetadataIndex(lastMD, "ChannelIndex") == getMetadataIndex(mdCopy, "ChannelIndex"));
      same = same && (getMetadataIndex(lastMD, "Slice") == getMetadataIndex(mdCopy, "Slice"));
      same = same && (getMetadataIndex(lastMD, "Frame") == getMetadataIndex(mdCopy, "Frame"));
      return same;
   }
}
