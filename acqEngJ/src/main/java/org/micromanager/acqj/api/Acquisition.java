/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.acqj.api;

import org.json.JSONObject;
import org.micromanager.acqj.api.ChannelGroupSettings;
import org.micromanager.acqj.internal.acqengj.MinimalAcquisitionSettings;
import org.micromanager.acqj.internal.acqengj.MinimalAcquisitionSettings;

/**
 * General interface for acquisitions
 * 
 * @author henrypinkard
 */
public interface Acquisition {
   
   /**
    * Commence acquisition or ready it to reviece externally generated events as applicable
    */
   public void start();
   
   /**
    * Cancels any pending events. Does not block. Use waitForCompletion if blocking until
    * all resources are freed is needed.
    * @param cancel if true, cancel any pending events, otherwise wait for them to complete
    */
   public void abort();
   
   /**
    * Block until acquisition finished and all resources complete. This should
    * always be called at the end of an acquisition to ensure that any exceptions
    * in the saving and processing during shutdown get cleared
    */
   public void close();
   
   /**
    * returns true if all data has been collected that will be collected
    * @return 
    */
   public boolean isComplete();
   
   /**
    * Get the settings for this acquisition. Will likely be a subsclass of
    * MinimalAcquisitionSettings
    * @return 
    */
   public MinimalAcquisitionSettings getAcquisitionSettings();
   
   /**
    * Get the channel group settings
    * @return 
    */
   public ChannelGroupSettings getChannels();
   
   /**
    * return if acquisition is paused (i.e. not acquiring new data but not finished)
    * @return 
    */
   public boolean isPaused();
   
   /**
    * Pause or unpause
    */
   public void togglePaused();
   
   /**
    * Get the summary metadata for this acquisition
    * @return 
    */
   public JSONObject getSummaryMetadata();
   
   /**
    * Returns true once any data has been acquired
    * @return 
    */
   public boolean anythingAcquired();
   
   /**
    * TODO: delete this one?
    * @return 
    */
   public boolean saveToDisk();
}
