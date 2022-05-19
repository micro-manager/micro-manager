package org.micromanager.display;

/**
 * Certain cameras (such as RGB cameras) can produce images where each pixel has multiple
 * components.  This interface determines how these components should be displayed.
 *
 * @author mark
 */
public interface ComponentDisplaySettings {
   /**
    * Interface for ComponentDisplaySettings Builder.
    */
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