package org.micromanager.tileddataviewer;

/**
 * Optional capability a {@link TiledDataViewerDataSource} may implement to expose
 * live-explore acquisition controls to the viewer's Inspector panel.
 *
 * <p>A live explore data source (e.g. the Explorer or Deskew Explore plugins) implements
 * this so the shared "Explorer Controls" Inspector panel can offer an Interrupt button
 * without the library depending on the plugins. Data sources that do not support
 * interrupting (e.g. a read-only viewer opened from a saved dataset) simply do not
 * implement it, and the panel keeps the Interrupt button visible but disabled.</p>
 */
public interface TiledDataViewerExploreControls {

   /**
    * Stop all queued tile acquisitions after the current tile finishes.
    */
   void interruptAcquisition();

   /**
    * @return true if tiles are currently being acquired.
    */
   boolean isAcquisitionInProgress();

   /**
    * Register a listener notified when the acquisition-in-progress state changes.
    *
    * @param l the listener to add
    */
   void addAcquisitionStateListener(AcquisitionStateListener l);

   /**
    * Unregister a previously added acquisition-state listener.
    *
    * @param l the listener to remove
    */
   void removeAcquisitionStateListener(AcquisitionStateListener l);

   /**
    * Notified when the acquisition-in-progress state changes. May be called from any
    * thread; listeners that touch Swing must marshal to the EDT themselves.
    */
   interface AcquisitionStateListener {
      void acquisitionInProgressChanged(boolean inProgress);
   }
}
