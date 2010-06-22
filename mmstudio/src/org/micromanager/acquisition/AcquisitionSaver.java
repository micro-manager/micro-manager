/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.acquisition;

import ij.ImagePlus;
import ij.io.FileSaver;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import java.util.logging.Level;
import java.util.logging.Logger;
import mmcorej.CMMCore;
import mmcorej.Metadata;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author arthur
 */
public class AcquisitionSaver extends Thread {
   private final CMMCore core_;
   private String root_;
   private String prefix_;

   public void run() {
      Metadata md = null;
      try {
         Object img = core_.popNextImageMD(0, 0, md);
         int width = Integer.parseInt(md.getFrameData("Width"));
         int height = Integer.parseInt(md.getFrameData("Height"));
         
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
      }

   }

   AcquisitionSaver(CMMCore core)
   {
      core_ = core;
   }

   void SetPaths(String root, String prefix)
   {
      root_ = root;
      prefix_ = prefix;

   }

   public boolean saveImageFile(String fname, Object img, int width, int height) {
      ImageProcessor ip;
      if (img instanceof byte[]) {
         ip = new ByteProcessor(width, height);
         ip.setPixels((byte[]) img);
      } else if (img instanceof short[]) {
         ip = new ShortProcessor(width, height);
         ip.setPixels((short[]) img);
      } else {
         return false;
      }

      ImagePlus imp = new ImagePlus(fname, ip);
      FileSaver fs = new FileSaver(imp);
      return fs.saveAsTiff(fname);
   }

}
