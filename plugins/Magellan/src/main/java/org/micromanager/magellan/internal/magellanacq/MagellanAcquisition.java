package org.micromanager.magellan.internal.magellanacq;

import org.micromanager.acqj.api.Acquisition;
import org.micromanager.ndviewer.api.AcquisitionInterface;

/**
 * Functions shared by magellan acquistions
 *
 * @author henrypinkard
 */
public interface MagellanAcquisition extends Acquisition, AcquisitionInterface {

   public double getZCoordOfNonnegativeZIndex(int displaySliceIndex);

   public int getDisplaySliceIndexFromZCoordinate(double d);

   public int getOverlapX();

   public int getOverlapY();
   
   public double getZStep();

}
