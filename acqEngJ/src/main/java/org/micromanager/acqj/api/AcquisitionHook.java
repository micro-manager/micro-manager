package org.micromanager.acqj.api;

import mmcorej.TaggedImage;
import org.micromanager.acqj.api.AcquisitionEvent;

/**
 *
 * @author henrypinkard
 */
public interface AcquisitionHook {

 
   /**
    * Called for before/after hardware acquisition hooks
    * 
    * @param event 
    * @return the same event if you don't want to modfify anything, otherwise null
    * if you want to delete it
    */
   public AcquisitionEvent run(AcquisitionEvent event);
   
   /**
    * Shut down and release all resources
    */
   public void close();
   
}
