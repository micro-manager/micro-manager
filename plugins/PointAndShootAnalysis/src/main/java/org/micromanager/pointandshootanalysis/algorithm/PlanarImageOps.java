
package org.micromanager.pointandshootanalysis.algorithm;


import boofcv.struct.image.ImageGray;
import boofcv.struct.image.Planar;

/**
 *
 * @author Nico
 */
public class PlanarImageOps {
   public enum Method {
      AVERAGE,
      SUM,
      MIN,
      MAX
   }
   
   public static void checkStartAndEnd(Planar input, Integer startIn, Integer endIn) {
      int start, end;
      if (startIn == null) {
         start = 0;
      } else {
         start = startIn;
      }
      if (endIn == null) {
         end = input.getNumBands();
      } else {
         end = endIn;
      }
      if (start < 0 || start > input.getNumBands()) {
         throw new IllegalArgumentException("The startpoint is invalid");
      }
      if (end > input.getNumBands() || end < start) {
			throw new IllegalArgumentException("The endpoint is invalid");
      } 
      startIn = start;
      endIn = end;
   }
   
   public static <T extends ImageGray<T>> void project (Planar<T> input, 
           T output, Method method) {
      project(input, output, method, 0, input.getNumBands());
   }
   
   public static <T extends ImageGray<T>> void project (Planar<T> input, 
           T output, Method method, Integer startIn, Integer endIn) {
      switch (method) {
         case AVERAGE : 
            averageProject(input, output, startIn, endIn);
            break;
         case MIN :
            minimumProject(input, output, startIn, endIn);
            break;
         case MAX : 
            maximumProject(input, output, startIn, endIn);
            break;
         case SUM:
            throw new IllegalArgumentException("Not implemented yet");
            
      }
            
         
   }
   

   
   public static <T extends ImageGray<T>> void averageProject(Planar<T> input, T output, 
           Integer startIn, Integer endIn) {
      checkStartAndEnd(input, startIn, endIn);
      int[] bands = new int[endIn - startIn];
      for (int i = 0; i < endIn - startIn; i++) {
         bands[i] = startIn + i;
      }
      Planar tmp = input.partialSpectrum(bands);
      boofcv.alg.misc.GPixelMath.averageBand(tmp, output);
   }
   
   public static <T extends ImageGray<T>> void minimumProject(Planar<T> input, T output, 
           Integer startIn, Integer endIn) {
      checkStartAndEnd(input, startIn, endIn);
      int[] bands = new int[endIn - startIn];
      for (int i = 0; i < endIn - startIn; i++) {
         bands[i] = startIn + i;
      }
      Planar tmp = input.partialSpectrum(bands);
      GPixelMath.minimumBand(tmp, output);
   }
   
   public static <T extends ImageGray<T>> void maximumProject(Planar<T> input, T output, 
           Integer startIn, Integer endIn) {
      checkStartAndEnd(input, startIn, endIn);
      int[] bands = new int[endIn - startIn];
      for (int i = 0; i < endIn - startIn; i++) {
         bands[i] = startIn + i;
      }
      Planar tmp = input.partialSpectrum(bands);
      GPixelMath.maximumBand(tmp, output);
   }
   
   
   
}
