/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.acquisition;

import ij.ImagePlus;

/**
 *
 * @author arthur
 */
public class MMImagePlus extends ImagePlus {

   public MMImagePlus(String dir_, AcquisitionVirtualStack virtualStack_) {
      super(dir_, virtualStack_);
   }

   public void expandFrames(int n) {
      this.nFrames = n;
   }

   public void expandChannels(int n) {
      this.nChannels = n;
   }

   public void expandSlices(int n) {
      this.nSlices = n;
   }

   public void expand(int nChannels, int nSlices, int nFrames) {
      this.nChannels = nChannels;
      this.nSlices = nSlices;
      this.nFrames = nFrames;
   }
}

