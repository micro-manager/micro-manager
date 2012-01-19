/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.projector;

import java.util.logging.Level;
import java.util.logging.Logger;
import mmcorej.CMMCore;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author arthur
 */
public class Galvo implements ProjectionDevice {
   String galvo_;
   CMMCore mmc_;
   
   public Galvo(CMMCore mmc) {
      mmc_ = mmc;
      galvo_ = mmc_.getGalvoDevice();
   }

   public void displaySpot(double x, double y) {
      try {
         mmc_.setGalvoPosition(galvo_, x, y);
      } catch (Exception ex) {
         ReportingUtils.showError(ex);
      }
   }

   public int getWidth() {
      return 500;
   }

   public int getHeight() {
      return 500;
   }

   public void turnOn() {
      try {
         mmc_.setProperty(galvo_, "CalibrationMode", 1);
      } catch (Exception ex) {
         ReportingUtils.showError(ex);
      }
   }

   public void turnOff() {
      try {
         mmc_.setProperty(galvo_, "CalibrationMode", 0);
      } catch (Exception ex) {
         ReportingUtils.showError(ex);
      }
   }
}
