package org.micromanager.acqj.api;

/**
 * Simple class to enable handling of exceptions asynchrnously in external code
 * @author henrypinkard
 */
public abstract class ExceptionCallback {
   
   public abstract void run(Exception e);
   
}