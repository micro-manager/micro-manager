/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.display.internal.event;

import org.micromanager.display.DataViewer;

/** @author Mark A. Tsuchida */
public class DataViewerDidBecomeInvisibleEvent {
  private final DataViewer viewer_;

  public static DataViewerDidBecomeInvisibleEvent create(DataViewer viewer) {
    return new DataViewerDidBecomeInvisibleEvent(viewer);
  }

  private DataViewerDidBecomeInvisibleEvent(DataViewer viewer) {
    viewer_ = viewer;
  }

  public DataViewer getDataViewer() {
    return viewer_;
  }
}
