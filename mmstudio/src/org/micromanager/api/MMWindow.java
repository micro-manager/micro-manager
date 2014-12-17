///////////////////////////////////////////////////////////////////////////////
//FILE:          MMWindow.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman, nico@cmp.ucsf.edu, 2010
//
// COPYRIGHT:    University of California, San Francisco, 2010
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




package org.micromanager.api;

import ij.ImagePlus;
import ij.process.ImageProcessor;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.imagedisplay.AcquisitionVirtualStack;
import org.micromanager.imagedisplay.VirtualAcquisitionDisplay;
import org.micromanager.utils.MMScriptException;
import org.micromanager.utils.ReportingUtils;

/*
 * The goal of this class is to give easy programmatic access to pixels and
 * metadata shown in a Micro-Manager Image viewer.
 */
public class MMWindow {
   private final VirtualAcquisitionDisplay virtAcq_;
   private final ImagePlus imp_;

   /*
    * Create an MMWindow reference to a particular Micro-Manager image window,
    * as represented by its ImagePlus reference (from ImageJ).
    */
   public MMWindow(ImagePlus imp) {
      AcquisitionVirtualStack acqStack;
      imp_ = imp;
      virtAcq_ = VirtualAcquisitionDisplay.getDisplay(imp);
   }

   /*
    * Determines whether we have a good reference to a Micro-Manager
    * image window (and not some other window).
    */
   public boolean isMMWindow() {
      return virtAcq_ != null;
   }

   /*
    * Returns the number of positions on the MM image window, as shown
    * by the "p" slider.
    */
   public int getNumberOfPositions() {
      if (virtAcq_ == null)
         return 0;
      int nrPositions = 1;
      try {
         nrPositions = virtAcq_.getNumPositions();
      } catch (JSONException jex) {
         ReportingUtils.logError(jex, "Error in MMWindow.getNumberOfPositions");
      }
      return nrPositions;
   }

   /*
    * Returns the number of channels on the MM image window, as shown by
    * the "c" slider.
    */
   public int getNumberOfChannels() {
      if (virtAcq_ == null)
         return 0;
      return virtAcq_.getNumGrayChannels();
   }

   /*
    * Returns the number of slices on the MM image window, as shown by
    * the "z" slider.
    */
   public int getNumberOfSlices() {
      if (virtAcq_ == null)
         return 0;
      return virtAcq_.getImagePlus().getNSlices();
   }

   /*
    * Returns the number of frames on the MM image window, as shown by
    * the "t" slider.
    */
   public int getNumberOfFrames() {
      if (virtAcq_ == null)
         return 0;
      return virtAcq_.getImagePlus().getNFrames();
   }

    /**
    * Sets the display to the given position
    * Position are 1-based
    * @param position
    * @throws MMScriptException
    */
   public void setPosition(int position) throws MMScriptException {
      if (position < 1 || position > getNumberOfPositions())
         throw new MMScriptException ("Invalid position requested");
      if (virtAcq_ != null)
         virtAcq_.setPosition(position - 1);
   }

   /**
    * Returns the current position of the image viewer
    * Positions are 1-based
    * @return The current position of the image viewer
    * @throws MMScriptException
    */
   public int getPosition() throws MMScriptException{
      if (virtAcq_ != null)
         return virtAcq_. getCurrentPosition() + 1;
      throw new MMScriptException("This is not a MMWindow");
   }

   /**
    * Returns an ImageJ ImagePlus for a given position
    * Does not update the display
    * @param position
    * @return ImageJ ImagePlus
    */
   public ImagePlus getImagePlus(int position) {
      return virtAcq_.getImagePlus(position);
   }

   /*
    * Returns the ImageJ ImageProcessor object associated with a particular
    * channel, slice, frame, position index.
    */
   public ImageProcessor getImageProcessor(int channel, int slice, int frame, int position)
      throws MMScriptException {
      setPosition(position);
      if (channel >= getNumberOfChannels() || slice >= getNumberOfSlices() ||
              frame >= getNumberOfFrames())
         throw new MMScriptException ("Parameters out of bounds");
      if (virtAcq_ == null)
         return null;
      ImagePlus hyperImage = virtAcq_.getImagePlus();
      return hyperImage.getImageStack().getProcessor(hyperImage.getStackIndex(channel + 1, slice, frame));
   }
   
   /**
    * Note: this function is preliminary.  It is likely to be removed/replaced soon.  Do not use.
    * 
    * @return Summary metadata associated with underling virtual acquistion 
    */
   public JSONObject getSummaryMetaData() {
      return virtAcq_.getSummaryMetadata();
   }
   
   /**
    * Note: this function is preliminary.  It is likely to be removed/replaced soon.  Do not use.
    *
    * Returns image metadata of specified image
    * @param channel
    * @param slice
    * @param frame
    * @param position
    * @return metadata in JSON format
    */
   public JSONObject getImageMetadata(int channel, int slice, int frame, int position) {
      return virtAcq_.getImageCache().getImageTags(channel, slice, frame, position);
   }


}
