package org.micromanager.acqj.api;

import mmcorej.TaggedImage;
import org.micromanager.acqj.api.AcquisitionEvent;
import org.micromanager.acqj.internal.acqengj.AcquisitionBase;

/**
 *
 * @author henrypinkard
 */
public abstract class AcquisitionHook {
   
   private AcquisitionBase acq_;
   private TaggedImage img_;
   private AcquisitionEvent event_;
   
   /**
    * Constructor for before/after hardware hooks
    * TODO: split this into two classes?
    * 
    * @param event 
    */
   public AcquisitionHook(AcquisitionEvent event) {
      event_ = event;
   }
   
   /**
    * Constructor for after saving hooks
    * @param acq
    * @param img 
    */
   public AcquisitionHook(AcquisitionBase acq, TaggedImage img) {
      acq_ = acq;
      img_ = img;
   }
   
   /**
    * Override this method for before/after acquisition hooks
    * 
    * TODO: check that deleting acquisiton event by returning null works
    * @param event 
    * @return the same event if you don't want to modfify anything, otherwise null
    * if you want to delete it
    */
   public AcquisitionEvent run(AcquisitionEvent event) { return event; }
   
   /**
    * Override this method for after saving acquisition hooks
    * 
    * @param acq
    * @param img 
    */
   public void run(AcquisitionBase acq, TaggedImage img) {}
   
}
