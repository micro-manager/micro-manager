/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.display;

import org.micromanager.data.Coords;

/** @author Mark A. Tsuchida */
public interface DisplayPositionChangedEvent {
  Coords getDisplayPosition();

  Coords getPreviousDisplayPosition();

  DataViewer getDataViewer();
}
