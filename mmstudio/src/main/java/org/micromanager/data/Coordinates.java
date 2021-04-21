/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.data;

import java.io.IOException;
import org.micromanager.PropertyMap;
import org.micromanager.data.internal.DefaultCoords;

/** @author Mark A. Tsuchida */
public final class Coordinates {
  private Coordinates() {}

  public static Coords.Builder builder() {
    return new DefaultCoords.Builder();
  }

  public static Coords fromPropertyMap(PropertyMap pmap) throws IOException {
    Coords.Builder b = builder();
    for (String axis : pmap.keySet()) {
      b.index(axis, pmap.getInteger(axis, -1));
    }
    return b.build();
  }
}
