package org.micromanager.magellan.internal.magellanacq;

import org.micromanager.acqj.api.AcquisitionAPI;
import org.micromanager.acqj.api.XYTiledAcquisitionAPI;
import org.micromanager.acqj.main.XYTiledAcquisition;
import org.micromanager.acqj.util.xytiling.PixelStageTranslator;
import org.micromanager.magellan.internal.channels.ChannelGroupSettings;
import org.micromanager.ndviewer.api.ViewerAcquisitionInterface;

/**
 * Functions shared by magellan acquistions
 *
 * @author henrypinkard
 */
public interface MagellanAcquisition extends ViewerAcquisitionInterface, XYTiledAcquisitionAPI  {

   /**
    * Get z coordinate corresponding to z index of 0.
    *
    * @return z coordinate with z index of 0
    */
   double getZOrigin();

   double getZStep();

   // Called by pycromanager
   public XYTiledAcquisitionAPI getAcquisition();

   ChannelGroupSettings getChannels();
   
   MagellanGenericAcquisitionSettings getAcquisitionSettings();

   PixelStageTranslator getPixelStageTranslator();

}
