/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.acquisition;

import java.util.ArrayList;
import mmcorej.AcquisitionSettings;
import mmcorej.CMMCore;
import mmcorej.Metadata;
import org.micromanager.api.ScriptInterface;
import org.micromanager.utils.ChannelSpec;
import org.micromanager.utils.MMScriptException;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author arthur
 */
public class AcquisitionDisplay extends Thread {

   protected final CMMCore core_;
   protected int imgCount_;
   private final ScriptInterface gui_;
   AcquisitionSettings acqSettings_;

   AcquisitionDisplay(ScriptInterface gui, CMMCore core, AcquisitionSettings acqSettings, ArrayList<ChannelSpec> channels) {
      gui_ = gui;
      core_ = core;
      acqSettings_ = acqSettings;
      
      int nPositions = Math.max(1, (int) acqSettings.getPositionList().size());
      int nTimes = Math.max(1, (int) acqSettings.getTimeSeries().size());
      int nChannels = Math.max(1, (int) acqSettings.getChannelList().size());
      int nSlices = Math.max(1, (int) acqSettings.getZStack().size());

      String posName;
      for (int posIndex = 0; posIndex < nPositions; ++posIndex) {
         posName = getPosName(posIndex);

         try {
            gui_.openAcquisition(posName, acqSettings.getRoot(), 1, nChannels, nSlices);
            for (int i=0; i<channels.size(); ++i) {
               gui_.setChannelColor(posName, i, channels.get(i).color_);
               gui_.setChannelName(posName, i, channels.get(i).config_);
            }
            gui_.initializeAcquisition(posName, 512, 512, 1);


         } catch (MMScriptException ex) {
            ReportingUtils.logError(ex);
         }
      }
   }

   private String getPosName(int posIndex) {
      String posName;
      if (acqSettings_.getPositionList().isEmpty()) {
         posName = "acq";
      } else {
         posName = acqSettings_.getPositionList().get(posIndex).getName();
      }
      return posName;
   }

   public void run() {
      long t1 = System.currentTimeMillis();
      try {
         do {
            while (core_.getRemainingImageCount() > 0) {
               ++imgCount_;
               try {
                  Metadata mdCopy = new Metadata();
                  Object img = core_.popNextImageMD(0, 0, mdCopy);
                  displayImage(img, mdCopy);
                  //    ReportingUtils.logMessage("time=" + mdCopy.getFrame() + ", position=" +
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

      cleanup();
   }
   
   protected void displayImage(Object img, Metadata m) {
      int posIndex = m.getPositionIndex();
      int channelIndex = getMetadataIndex(m, "ChannelIndex");
      int sliceIndex = m.getSlice();
      int frameIndex = m.getFrame();

      try {
         gui_.addImage(getPosName(posIndex), img, frameIndex, channelIndex, sliceIndex);
         for (String key : m.getFrameKeys()) {
            gui_.setImageProperty(getPosName(posIndex), frameIndex, channelIndex, sliceIndex, key, m.get(key));
         }
      } catch (MMScriptException ex) {
         ReportingUtils.logError(ex);
      }
   }

   private int getMetadataIndex(Metadata m, String key) {
      if (m.getFrameData().has_key(key)) {
         String val = m.get(key);
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

   protected boolean sameFrame(Metadata lastMD, Metadata mdCopy) {
      if (lastMD == null || mdCopy == null) {
         return false;
      }
      boolean same =
            (getMetadataIndex(lastMD, "Position") == getMetadataIndex(mdCopy, "Position"))
         && (getMetadataIndex(lastMD, "ChannelIndex") == getMetadataIndex(mdCopy, "ChannelIndex"))
         && (getMetadataIndex(lastMD, "Slice") == getMetadataIndex(mdCopy, "Slice"))
         && (getMetadataIndex(lastMD, "Frame") == getMetadataIndex(mdCopy, "Frame"));
      return same;
   }

   protected void cleanup() {
      try {
         gui_.closeAllAcquisitions();
      } catch (Exception e) {
         ReportingUtils.logError(e);
      }
   }
}
