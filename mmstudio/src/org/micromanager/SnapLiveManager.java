///////////////////////////////////////////////////////////////////////////////
//FILE:          SnapLiveManager.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       
//
// COPYRIGHT:    University of California, San Francisco, 2014
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//

package org.micromanager;

import ij.gui.ImageWindow;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import mmcorej.CMMCore;
import mmcorej.TaggedImage;
import org.json.JSONException;

import org.json.JSONObject;

import org.micromanager.acquisition.LiveModeTimer;
import org.micromanager.acquisition.MMAcquisition;
import org.micromanager.api.ImageCache;
import org.micromanager.imagedisplay.VirtualAcquisitionDisplay;
import org.micromanager.internalinterfaces.LiveModeListener;
import org.micromanager.utils.ImageUtils;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.MMException;
import org.micromanager.utils.MMScriptException;
import org.micromanager.utils.ReportingUtils;
import org.micromanager.utils.WaitDialog;

/*
 * Handles logic specific to the Snap/Live window.
 */
public class SnapLiveManager {
   private final MMStudio studio_;
   private final CMMCore core_;
   private LiveModeTimer liveModeTimer_;
   private final List<LiveModeListener> liveModeListeners_
         = Collections.synchronizedList(new ArrayList<LiveModeListener>());
   private static VirtualAcquisitionDisplay display_;
   public static final String SIMPLE_ACQ = "Snap/Live Window";
   private final Color[] multiCameraColors_ = {Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW, Color.CYAN};

   public SnapLiveManager(MMStudio studio, CMMCore core) {
      studio_ = studio;
      core_ = core;
   }

   /**
    * Safely set the core exposure time, reseting live-mode as needed.
    * @param exposureTime - desired camera exposure time (in ms) 
    */
   public void safeSetCoreExposure(double exposureTime) {
      boolean isOn = getIsLiveModeOn();
      if (isOn) {
         setLiveMode(false);
      }
      try {
         core_.setExposure(exposureTime);
      }
      catch (Exception e) {
         ReportingUtils.logError(e, "Failed to set core exposure time.");
      }
      if (isOn) {
         setLiveMode(true);
      }
   }

   public boolean getIsLiveModeOn() {
      return (liveModeTimer_ != null && liveModeTimer_.isRunning());
   }

   public void setLiveMode(boolean isOn) {
      if (isOn == getIsLiveModeOn()) {
         // No need to do anything.
         return;
      }
      if (isOn && liveModeTimer_ == null) {
         // Need to create the timer first.
         if (core_.getCameraDevice().length() == 0) {
            ReportingUtils.showError("No camera configured.");
         }
         liveModeTimer_ = new LiveModeTimer();
      }
      if (liveModeTimer_ != null) {
         if (isOn) {
            WaitDialog waitDlg = new WaitDialog("Starting Live...");
            waitDlg.setAlwaysOnTop(true);
            waitDlg.showDialog();
            try {

               liveModeTimer_.begin();
               callLiveModeListeners(true);
            }
            catch (Exception e) {
               waitDlg.closeDialog();
               ReportingUtils.showError(e, "Couldn't start live mode.");

               waitDlg = new WaitDialog("Stopping Live...");
               waitDlg.setAlwaysOnTop(true);
               waitDlg.showDialog();
               liveModeTimer_.stop();
               callLiveModeListeners(false);
            }
            finally {
               waitDlg.closeDialog();
            }
         }
         else {
            WaitDialog waitDlg = new WaitDialog("Stopping Live...");
            waitDlg.setAlwaysOnTop(true);
            waitDlg.showDialog();
            liveModeTimer_.stop();
            callLiveModeListeners(false);
            waitDlg.closeDialog();
         }
      }
   }

   public final void addLiveModeListener (LiveModeListener listener) {
      if (liveModeListeners_.contains(listener)) {
         return;
      }
      liveModeListeners_.add(listener);
   }

   public void removeLiveModeListener(LiveModeListener listener) {
      liveModeListeners_.remove(listener);
   }

   public void callLiveModeListeners(boolean enable) {
      for (LiveModeListener listener : liveModeListeners_) {
         listener.liveModeEnabled(enable);
      }
   }

   public void createSnapLiveDisplay(String name, ImageCache cache) {
      try {
         display_ = new VirtualAcquisitionDisplay(cache, name);
      }
      catch (MMScriptException e) {
         ReportingUtils.logError(e, "Failed to create Snap/Live display");
      }
   }

   public VirtualAcquisitionDisplay getSnapLiveDisplay() {
      return display_;
   }

   public ImageWindow getSnapLiveWindow() {
      // The check for getHyperImage() protects us against a rare null-pointer
      // exception where the display exists, but has not yet finished
      // initializing. This is possibly caused when the display is unusually
      // large (e.g. for 2500x2000 displays).
      if (display_ != null && display_.getHyperImage() != null) {
         return display_.getHyperImage().getWindow();
      }
      return null;
   }

   public void moveDisplayToFront() {
      ImageWindow window = getSnapLiveWindow();
      if (window != null) {
         window.toFront();
      }
   }

   /**
    * Verify that the current acquisition settings for the Snap/Live window
    * match the provided settings. Recreate the display if they do not match.
    * @param width in pixels of the current image
    * @param height in pixels of the current image
    * @param depth in bytes of the current image
    * @param bitDepth in bytes of the current image
    * @param numCamChannels 
    */
   public void validateDisplayAndAcquisition(int width, int height, int depth, int bitDepth,
         int numCamChannels) {
      try {
         if (studio_.acquisitionExists(SIMPLE_ACQ)) {
            if ((studio_.getAcquisitionImageWidth(SIMPLE_ACQ) != width) ||
                  (studio_.getAcquisitionImageHeight(SIMPLE_ACQ) != height) ||
                  (studio_.getAcquisitionImageByteDepth(SIMPLE_ACQ) != depth) ||
                  (studio_.getAcquisitionImageBitDepth(SIMPLE_ACQ) != bitDepth) ||
                  (studio_.getAcquisitionMultiCamNumChannels(SIMPLE_ACQ) != numCamChannels)) {
               //Need to close and reopen simple window
               studio_.closeAcquisitionWindow(SIMPLE_ACQ);
            }
         }
         if (! studio_.acquisitionExists(SIMPLE_ACQ)) { // Time to create the acquisition.
            studio_.openAcquisition(SIMPLE_ACQ, "", 1, numCamChannels, 1, true);
            if (numCamChannels > 1) {
               for (long i = 0; i < numCamChannels; i++) {
                  String chName = core_.getCameraChannelName(i);
                  int defaultColor = multiCameraColors_[(int) i % multiCameraColors_.length].getRGB();
                  studio_.setChannelColor(SIMPLE_ACQ, 
                        (int) i, studio_.getChannelColor(chName, defaultColor));
                  studio_.setChannelName(SIMPLE_ACQ, (int) i, chName);
               }
            }
            initializeAcquisition(width, height, depth, bitDepth, numCamChannels);
            studio_.getAcquisition(SIMPLE_ACQ).promptToSave(false);
            display_ = studio_.getAcquisition(SIMPLE_ACQ).getAcquisitionWindow();
            getSnapLiveWindow().toFront();
            studio_.updateCenterAndDragListener();
         }
      } catch (MMScriptException ex) {
         ReportingUtils.showError(ex);
      }
   }

   public void validateDisplayAndAcquisition(TaggedImage image) {
      JSONObject tags = image.tags;
      try {
         int width = MDUtils.getWidth(tags);
         int height = MDUtils.getHeight(tags);
         int depth = MDUtils.getDepth(tags);
         int bitDepth = MDUtils.getBitDepth(tags);
         int numCamChannels = (int) core_.getNumberOfCameraChannels();

         validateDisplayAndAcquisition(width, height, depth, bitDepth, numCamChannels);
      }
      catch (JSONException ex) {
         ReportingUtils.showError("Error extracting image info in validateDisplayAndAcquisition: " + ex);
      } catch (MMScriptException ex) {
         ReportingUtils.showError("Error extracting image info in validateDisplayAndAcquisition: " + ex);
      }
   }

   public void validateDisplayAndAcquisition() {
      if (core_.getCameraDevice().length() == 0) {
         ReportingUtils.showError("No camera configured");
         return;
      }
      int width = (int) core_.getImageWidth();
      int height = (int) core_.getImageHeight();
      int depth = (int) core_.getBytesPerPixel();
      int bitDepth = (int) core_.getImageBitDepth();
      int numCamChannels = (int) core_.getNumberOfCameraChannels();
      validateDisplayAndAcquisition(width, height, depth, bitDepth, numCamChannels);
   }

   private void initializeAcquisition(int width, int height, int byteDepth,
         int bitDepth, int numMultiCamChannels) throws MMScriptException {
      MMAcquisition acq = studio_.getAcquisitionWithName(SIMPLE_ACQ);
      acq.setImagePhysicalDimensions(width, height, byteDepth, bitDepth, numMultiCamChannels);
      acq.initializeSimpleAcq();
   }

   public boolean displayImage(Object pixels) {
      validateDisplayAndAcquisition();
      try {
         MMAcquisition acquisition = studio_.getAcquisition(SIMPLE_ACQ);
         int width = acquisition.getWidth();
         int height = acquisition.getHeight();
         int byteDepth = acquisition.getByteDepth();
         TaggedImage ti = ImageUtils.makeTaggedImage(pixels, 0, 0, 0,0, 
               width, height, byteDepth);
         try {
            display_.getImageCache().putImage(ti);
         }
         catch (java.io.IOException e) {
            ReportingUtils.logError(e, "This should never happen!");
         }
         display_.imageReceived(ti);
         return true;
      } catch (MMScriptException ex) {
         ReportingUtils.showError(ex);
         return false;
      } catch (MMException ex) {
         ReportingUtils.showError(ex);
         return false;
      }
   }

   public void displayTaggedImage(TaggedImage image) {
      validateDisplayAndAcquisition(image);
   }

   public void setStatusLine(String status) {
      display_.displayStatusLine(status);
   }

   public void snapAndAddToImage5D() {
      if (core_.getCameraDevice().length() == 0) {
         ReportingUtils.showError("No camera configured");
         return;
      }
      try {
         if (getIsLiveModeOn()) {
            // Just grab the most recent image.
            ImageCache cache = display_.getImageCache();
            int channels = cache.getSummaryMetadata().getInt("Channels");
            for (int i = 0; i < channels; i++) {
               studio_.addToAlbum(cache.getImage(i, 0, 0, 0), 
                     cache.getDisplayAndComments());
            }
         } else {
            studio_.doSnap(true);
         }
      } catch (JSONException ex) {
         ReportingUtils.logError(ex);
      } catch (MMScriptException ex) {
         ReportingUtils.logError(ex);
      }
   }
}
