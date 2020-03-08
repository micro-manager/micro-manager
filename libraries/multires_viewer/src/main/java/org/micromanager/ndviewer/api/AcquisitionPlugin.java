/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.ndviewer.api;

/**
 *
 * @author henrypinkard
 */
public interface AcquisitionPlugin {

   public boolean isComplete();

   public void abort();

   public void togglePaused();

   public boolean isPaused();
   
}
