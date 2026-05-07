package org.micromanager.tileddataviewer;

/**
 * This interface is used to pass an acquisition to the viewer, so
 * that its controls for pausing, aborting, and close can be used
 * This is optional functionality, as the viewer doesn't need
 * an acquisition to work.
 *
 * @author henrypinkard
 */
public interface TiledDataViewerAcqInterface {

   boolean isFinished();

   /**
    * Called on the EDT when the user requests to close the viewer window,
    * before any close action is taken.  Return {@code false} to veto the close
    * (e.g. to show a save-data dialog and leave the window open if the user
    * clicks Cancel).  The default implementation returns {@code true} so that
    * existing callers are unaffected.
    *
    * @return true to allow the close to proceed, false to cancel it.
    */
   default boolean requestToClose() {
      return true;
   }

   void abort();

   void setPaused(boolean paused);

   boolean isPaused();

   void waitForCompletion();

}
