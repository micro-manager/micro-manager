package org.micromanager.acqj.api;

import org.micromanager.acqj.internal.acqengj.AcquisitionBase;
import mmcorej.TaggedImage;

/**
 * Simple data structure for holding a TagggedImage and the Acquisition it
 * Corresponds to
 * 
 * @author henrypinkard
 */
public class ImageAcqTuple {

   public final TaggedImage img_;
   public final AcquisitionBase acq_;

   public ImageAcqTuple(TaggedImage img, AcquisitionBase acq) {
      this.img_ = img;
      this.acq_ = acq;
   }

}