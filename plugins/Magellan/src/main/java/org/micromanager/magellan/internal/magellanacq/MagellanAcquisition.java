package org.micromanager.magellan.internal.magellanacq;

import org.micromanager.acqj.api.Acquisition;

/**
 * Functions shared by magellan acquistions
 * 
 * @author henrypinkard
 */
public interface MagellanAcquisition extends Acquisition {
   
   public double getZCoordinateOfDisplaySlice(int displaySliceIndex);
   
   public int getDisplaySliceIndexFromZCoordinate(double d);
   

}
