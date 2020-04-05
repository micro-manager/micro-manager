/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.ndviewer.api;

/**
 * This interface is used to pass an acquisition to the viewer, so
 * that its controls for pausing, aborting, and close can be used
 * This is optional functionality, as the viewer doesn't need 
 * an acqusition to work
 * 
 * @author henrypinkard
 */
public interface ViewerAcquisitionInterface {

   public boolean isFinished();

   public void abort();

   public void togglePaused();

   public boolean isPaused();

   public void close();
   
}
