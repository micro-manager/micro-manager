/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.acqj.api;

import org.json.JSONObject;
import org.micromanager.acqj.internal.acqengj.MinimalAcquisitionSettings;

/**
 * TODO: fill out this interface. What about metadata stuff?
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
    * Block until acquisition finished and all resources complete
    */
   public void waitForCompletion();
   
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
    * Should be called by the data sink when it closes
    */
   public void onDataSinkClosing();

   /**
    * TODO: delete this one?
    * @return 
    */
   public boolean saveToDisk();
}
