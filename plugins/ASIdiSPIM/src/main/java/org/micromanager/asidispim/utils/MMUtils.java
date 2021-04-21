package org.micromanager.asidispim.utils;

import org.micromanager.Studio;
import org.micromanager.data.Coordinates;
import org.micromanager.data.Coords.CoordsBuilder;

/**
 * Static functions to facilitate working with verbose MM 2.0 stuff
 *
 * @author nico
 */
public class MMUtils {

  /**
   * Needed to work around non-zeroed Coords builder that MM generates but that results in problems
   * when not used with all axes zeroed
   *
   * @param gui Studio object
   * @return Zeroed CoordsBuilder
   */
  public static CoordsBuilder zeroedCoordsBuilder(Studio gui) {
    return Coordinates.builder().channel(0).stagePosition(0).t(0).z(0);
  }
}
