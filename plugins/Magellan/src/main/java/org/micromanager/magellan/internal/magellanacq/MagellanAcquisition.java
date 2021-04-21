package org.micromanager.magellan.internal.magellanacq;

import org.micromanager.acqj.api.AcquisitionAPI;
import org.micromanager.magellan.internal.channels.ChannelGroupSettings;
import org.micromanager.ndviewer.api.ViewerAcquisitionInterface;

/**
 * Functions shared by magellan acquistions
 *
 * @author henrypinkard
 */
public interface MagellanAcquisition extends ViewerAcquisitionInterface, AcquisitionAPI {

  /**
   * Get z coordinate corresponding to z index of 0
   *
   * @return
   */
  public double getZOrigin();

  public double getZStep();

  public ChannelGroupSettings getChannels();

  public MagellanGenericAcquisitionSettings getAcquisitionSettings();
}
