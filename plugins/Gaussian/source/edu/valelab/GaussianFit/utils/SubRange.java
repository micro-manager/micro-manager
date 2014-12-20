/**
 * SubRange
 * 
 * Utility class for DataCollection Form
 * 
 * Copyright UCSF, 2013
 * 
 * Licensed under BSD version 2.0 
 *
 * 
 */


package edu.valelab.GaussianFit.utils;

import edu.valelab.GaussianFit.data.GaussianSpotData;
import java.util.ArrayList;
import java.util.List;

/**
 * SubRange
 * Extracts a subrange from a DataCollectionForm dataset, given a list of 
 * desired frames
 * 
 * 
 * @author nico, nico.stuurman@ucsf.edu
 *  
 * 
 */
public class SubRange {
   
   /**
    * 
    * @param input dataset to be subranged
    * @param desiredFrames - list with desired frames
    * @return - subranged datase
    */
   public static RowData subRange(RowData input,
           ArrayList<Integer> desiredFrames) {
      
      RowData output = new RowData(input);
      output.spotList_.clear();
      
      List<GaussianSpotData> spots =  input.spotList_;

      boolean endReached = false;
      int i = 0;
      int j = 0;
      while (!endReached) {
         while (j < spots.size() && i < desiredFrames.size()  && 
                spots.get(j).getFrame() != desiredFrames.get(i) ) {
            j++;
         }
         if ( j < spots.size() && i < desiredFrames.size() ) {
            output.spotList_.add(new GaussianSpotData(spots.get(j)) );
            i++;
         } else {
            endReached = true;
         }
      }
      output.maxNrSpots_ = output.spotList_.size();
      
      return output;
   }
   
}
