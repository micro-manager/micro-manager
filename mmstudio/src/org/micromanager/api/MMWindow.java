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


/*
 * Goal of this class is to give easy programmatic access to pixels and metadata
 * shown in a Micro-Manager Image viewer
 */

package org.micromanager.api;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ImageProcessor;
import org.micromanager.acquisition.AcquisitionVirtualStack;
import org.micromanager.acquisition.VirtualAcquisitionDisplay;
import org.micromanager.utils.MMScriptException;


public class MMWindow {
   private final VirtualAcquisitionDisplay virtAcq_;
   private final ImagePlus imp_;
   
   public MMWindow(ImagePlus imp) {
      AcquisitionVirtualStack acqStack;
      imp_ = imp;
      virtAcq_ = VirtualAcquisitionDisplay.getDisplay(imp);
   }

   public boolean isMMWindow() {
      return virtAcq_ != null;
   }
   
   public int getNumberOfPositions() {
      if (virtAcq_ == null)
         return 0;
      return virtAcq_.getNumPositions();
   }

   public int getNumberOfChannels() {
      if (virtAcq_ == null)
         return 0;
      return virtAcq_.getNumGrayChannels();
   }

   public int getNumberOfSlices() {
      if (virtAcq_ == null)
         return 0;
      return virtAcq_.getImagePlus().getNSlices();
   }

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
    * Positions are 1-
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
    * @return
    */
   public ImagePlus getImagePlus(int position) {
      return virtAcq_.getImagePlus(position);
   }

   public ImageProcessor getImageProcessor(int channel, int slice, int frame, int position)
      throws MMScriptException {
      setPosition(position);
      if ( channel >= getNumberOfChannels() || slice >= getNumberOfSlices() || 
              frame >= getNumberOfFrames() )
         throw new MMScriptException ("Parameters out of bounds");
      if (virtAcq_ == null)
         return null;
      ImagePlus hyperImage = virtAcq_.getImagePlus();
      return hyperImage.getImageStack().getProcessor(hyperImage.getStackIndex(channel + 1, slice, frame));
   }


}
