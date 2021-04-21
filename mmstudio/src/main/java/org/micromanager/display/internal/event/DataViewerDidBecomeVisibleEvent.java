/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.display.internal.event;

import org.micromanager.display.DataViewer;

/** @author Mark A. Tsuchida */
public final class DataViewerDidBecomeVisibleEvent {
  private final DataViewer viewer_;

  public static DataViewerDidBecomeVisibleEvent create(DataViewer viewer) {
    return new DataViewerDidBecomeVisibleEvent(viewer);
  }

  private DataViewerDidBecomeVisibleEvent(DataViewer viewer) {
    viewer_ = viewer;
  }

  public DataViewer getDataViewer() {
    return viewer_;
  }
}
