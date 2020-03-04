package org.micromanager.acqj.api;

import java.util.Iterator;
import java.util.concurrent.Future;
import org.micromanager.acqj.internal.acqengj.AcquisitionBase;

/**
 * Public interface for Acqusition Engine
 *
 * Features: 1) Lazy evaluation of acquisition events so that acquisition
 * settings can be changed during acquisition, and the acquisition will adapt.
 *
 * 2) The ability to monitor the progress of certain parts of acq event (as its
 * acquired, when it reaches disk, if there was an exception along the way)
 *
 * 3) black box optimization of acquisition events, so sequence acquisitions can
 * run super fast without caller having to worry about the details
 *
 */
public interface AcqEngineJ {

   public static final int BEFORE_HARDWARE_HOOK = 0;
   public static final int AFTER_HARDWARE_HOOK = 1;
   public static final int AFTER_SAVE_HOOK = 2;

   /**
    * Used for injecting arbitrary code (i.e. an AcquisitionHook) to run at various 
    * points in the acquisition cycle. Can be run before or after hardware has 
    * been updated for each image/sequence, or after the data has been saved 
    * (Assuming that an ImageProcessor hasn't been implemented to save data in 
    * a custom way). To modify images in a custom way, use addImageProcessor instead
    * 
    * @param type One of AcqEngJ.BEFORE_HARDWARE_HOOK, AcqEngJ.AFTER_HARDWARE_HOOK,
    * or AcqEngJ.AFTER_SAVE_HOOK
    * @param hook the code to be run 
    */
   public void addAcquisitionHook(int type, AcquisitionHook hook);

   /**
    * Remove all AcquisitionHooks that had been added
    */
   public void clearAcquisitionHooks();

   /**
    * Add a TaggedImageProcessor to the pipeline for after images are acquired.
    * This can be used both to modify/delete/insert images before they are
    * saved, and to divert Images to alternative saving destinations
    *
    * @param p
    */
   public void addImageProcessor(TaggedImageProcessor p);

   /**
    * Remove any image processors
    */
   public void clearImageProcessors();

//   /**
//    * Finish acquisition and block until everything cleaned up
//    *
//    * @param acq the acquisition to finish
//    * @return Future that can be gotten when all resources cleaned up
//    */
//   public Future finishAcquisition(AcquisitionBase acq);
//
//   /**
//    * Submit a stream of events which will get lazily processed and combined
//    * into sequence events as needed. Block until all events executed
//    *
//    * @param eventIterator Iterator of acquisition events instructing what to
//    * acquire
//    * @param acq the acquisition
//    * @return a Future that can be gotten when the event iteration is finished,
//    */
//   public Future submitEventIterator(Iterator<AcquisitionEvent> eventIterator, AcquisitionBase acq);

}
