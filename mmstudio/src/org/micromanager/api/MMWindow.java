/*
 * Goal of this class is to give easy programmatic access to pixels and metadata
 * shown in a Micro-Manager Image viewer
 */

package org.micromanager.api;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ImageProcessor;
import org.micromanager.acquisition.AcquisitionVirtualStack;
import org.micromanager.acquisition.MMVirtualAcquisitionDisplay;
import org.micromanager.utils.MMScriptException;

/**
 *
 * @author nico
 */
public class MMWindow {
   MMVirtualAcquisitionDisplay virtAcq_ = null;
   
   public MMWindow(ImagePlus imp) {
      AcquisitionVirtualStack acqStack;
      ImageStack stack = imp.getStack();
      if (stack instanceof AcquisitionVirtualStack) {
         acqStack = (AcquisitionVirtualStack) stack;
         virtAcq_ = acqStack.getVirtualAcquisition();
      }
   }
   
   public int getNumberOfPositions() {
      if (virtAcq_ == null)
         return 0;
      return virtAcq_.getNumPositions();
   }

   public int getNumberOfChannels() {
      if (virtAcq_ == null)
         return 0;
      return virtAcq_.getNumChannels();
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

   public void setPosition(int position) throws MMScriptException {
      if (position < 0 || position > getNumberOfPositions())
         throw new MMScriptException ("Invalid position requested");
      if (virtAcq_ != null)
         virtAcq_.setPosition(position);
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
