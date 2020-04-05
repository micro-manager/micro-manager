package org.micromanager.magellan.internal.magellanacq;

import org.micromanager.acqj.api.AcquisitionInterface;
import org.micromanager.magellan.internal.channels.ChannelGroupSettings;
import org.micromanager.ndviewer.api.ViewerAcquisitionInterface;

/**
 * Functions shared by magellan acquistions
 *
 * @author henrypinkard
 */
public interface MagellanAcquisition extends ViewerAcquisitionInterface, AcquisitionInterface {

   public double getZCoordOfNonnegativeZIndex(int displaySliceIndex);

   public int getDisplaySliceIndexFromZCoordinate(double d);

   public int getOverlapX();

   public int getOverlapY();
   
   public double getZStep();
   
   public ChannelGroupSettings getChannels();
   
   public MagellanGenericAcquisitionSettings getAcquisitionSettings();

}
