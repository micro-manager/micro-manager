
package org.micromanager.display;

/**
 *
 * @author mark
 */
public interface ComponentDisplaySettings {
   interface Builder {
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