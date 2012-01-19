/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.projector;

/**
 *
 * @author arthur
 */
public interface ProjectionDevice {
   public void displaySpot(double x, double y);
   public int getWidth();
   public int getHeight();
   public void turnOn();
   public void turnOff();
}
