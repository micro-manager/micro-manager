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

   void abort();

   void setPaused(boolean paused);

   boolean isPaused();

   void waitForCompletion();

}
