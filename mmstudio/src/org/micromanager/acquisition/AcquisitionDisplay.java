/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.acquisition;

import java.awt.Color;
import java.util.ArrayList;
import mmcorej.AcquisitionSettings;
import mmcorej.CMMCore;
import mmcorej.Metadata;
import org.micromanager.api.ScriptInterface;
import org.micromanager.image5d.Image5D;
import org.micromanager.utils.MMScriptException;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author arthur
 */
public abstract class AcquisitionDisplay extends Thread {

   protected final CMMCore core_;
   protected int imgCount_;
   protected   ArrayList<Image5D> i5dVector_;
   private final ScriptInterface gui_;

   AcquisitionDisplay(ScriptInterface gui, CMMCore core, AcquisitionSettings acqSettings) {
      gui_ = gui;
      core_ = core;
      i5dVector_ = new ArrayList<Image5D>();

      int nTimes = Math.max(1, (int) acqSettings.getTimeSeries().size());
      int nChannels = Math.max(1, (int) acqSettings.getChannelList().size());
      int nSlices = Math.max(1, (int) acqSettings.getZStack().size());


      try {
         gui_.openAcquisition("x", acqSettings.getRoot(), 1, nChannels, nSlices);
         gui_.initializeAcquisition("x", 512, 512, 1);
      } catch (MMScriptException ex) {
         ReportingUtils.logError(ex);
      }
   }

   public abstract void run();
   

   protected void displayImage(Object img, Metadata m) {
      int posIndex = getMetadataIndex(m, "Position");
      int channelIndex = getMetadataIndex(m, "ChannelIndex");
      int sliceIndex = getMetadataIndex(m, "Slice");
      int frameIndex = getMetadataIndex(m, "Frame");

      try {
         gui_.addImage("x", img, frameIndex, channelIndex, sliceIndex);
      } catch (MMScriptException ex) {
         ReportingUtils.logError(ex);
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


   protected boolean sameFrame(Metadata lastMD, Metadata mdCopy) {
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

   protected void cleanup() {
      try {
         gui_.closeAcquisition("x");
      } catch (Exception e) {
         ReportingUtils.logError(e);
      }
   }
}
