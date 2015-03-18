/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package acq;

/**
 * Name says it all
 */
public interface AcquisitionEventSource {
   
   public AcquisitionEvent getNextEvent() throws InterruptedException;
   
}
