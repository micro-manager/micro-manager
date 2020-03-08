package org.micromanager.magellan.internal.magellanacq;

import org.micromanager.acqj.api.Acquisition;
import org.micromanager.ndviewer.api.AcquisitionPlugin;

/**
 * Functions shared by magellan acquistions
 *
 * @author henrypinkard
 */
public interface MagellanAcquisition extends Acquisition, AcquisitionPlugin {

   public double getZCoordinateOfDisplaySlice(int displaySliceIndex);

   public int getDisplaySliceIndexFromZCoordinate(double d);

   public int getOverlapX();

   public int getOverlapY();

}
