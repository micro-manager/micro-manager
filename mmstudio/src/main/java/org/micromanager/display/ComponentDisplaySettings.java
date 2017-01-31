/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.display;

/**
 *
 * @author mark
 */
public interface ComponentDisplaySettings {
   public interface Builder {
      Builder scalingMinimum(long minIntensity);
      Builder scalingMaximum(long maxIntensity);
      Builder scalingRange(long minIntensity, long maxIntensity);
      Builder scalingGamma(double gamma);

      ComponentDisplaySettings build();
   }

   long getScalingMinimum();
   long getScalingMaximum();
   double getScalingGamma();

   Builder copyBuilder();

   // TODO Add static builder() in Java 8
}