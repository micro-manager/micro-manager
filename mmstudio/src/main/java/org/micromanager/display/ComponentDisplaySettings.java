
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
   
   // NS 2019-08015: I know this goes against the ideas behind this design,
   // but we need to set these values everytime and autostretch is executed
   // and it seems inefficient to create these objects (and its parents) everytime
   // that happens.
   // It is OK to do this differently, but we need this functionality
   void setScalingMinimum(long min);
   void setScalingMaximum(long max);
   

   Builder copyBuilder();

   // TODO Add static builder() in Java 8
}