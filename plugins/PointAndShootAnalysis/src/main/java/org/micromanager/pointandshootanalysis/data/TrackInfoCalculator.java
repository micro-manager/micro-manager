
package org.micromanager.pointandshootanalysis.data;

import java.util.Map;
import java.util.Set;

/**
 * Provides access to interpretation of a (bleached) particle track
 * Calculates the average size of a particle, fits recovery of the 
 * bleach spot, as well as of the particle itself
 *
 * @author nico
 */
public class TrackInfoCalculator {
   
   public static Double avgParticleSize(PASData pasData) {
      double sum = 0.0;
      int counter = 0;
      Set<Map.Entry<Integer, ParticleData>> entrySet = pasData.particleDataTrack().entrySet();
      for (Map.Entry<Integer, ParticleData> e : entrySet) {
         if (e != null && e.getValue() != null && e.getValue().getMaskIncludingBleach() != null) {
            sum += e.getValue().getMaskIncludingBleach().size();
            counter++;
         }
      }
      return sum / counter;
   }
   
}
