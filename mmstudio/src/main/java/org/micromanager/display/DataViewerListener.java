package org.micromanager.display;

/**
 * Simple interface dedicated to the question if a dataviewer can be closed or not
 *
 * @author nico
 */
public abstract class DataViewerListener {

  /**
   * @param viewer - DataViewer asking if it can be closed
   * @return if true, this delegate has no qualms about closing this dataViewer
   */
  public abstract boolean canCloseViewer(DataViewer viewer);
}
