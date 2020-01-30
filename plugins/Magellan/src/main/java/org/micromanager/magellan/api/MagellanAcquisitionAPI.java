package org.micromanager.magellan.api;

/**
 *
 * @author henrypinkard
 */
public interface MagellanAcquisitionAPI {
   
   public MagellanAcquisitionSettingsAPI getAcquisitionSettings();
   
   public void start();
   
   public boolean waitForCompletion();
   
   public void abort();
   
   
   
}
