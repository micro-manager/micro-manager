/**
 * SubRange
 * 
 * Utility class for DataCollection Form
 * 
* @author - Nico Stuurman,  2013
 * 
 * 
Copyright (c) 2013-2017, Regents of the University of California
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.
2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

The views and conclusions contained in the software and documentation are those
of the authors and should not be interpreted as representing official policies,
either expressed or implied, of the FreeBSD Project.
 */


package edu.valelab.gaussianfit.utils;

import edu.valelab.gaussianfit.data.RowData;
import edu.valelab.gaussianfit.data.SpotData;
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
           ArrayList<Long> desiredFrames) {
      
      RowData output = new RowData(input);
      output.spotList_.clear();
      
      List<SpotData> spots =  input.spotList_;

      boolean endReached = false;
      int i = 0;
      int j = 0;
      while (!endReached) {
         while (j < spots.size() && i < desiredFrames.size()  && 
                spots.get(j).getFrame() != desiredFrames.get(i) ) {
            j++;
         }
         if ( j < spots.size() && i < desiredFrames.size() ) {
            output.spotList_.add(new SpotData(spots.get(j)) );
            i++;
         } else {
            endReached = true;
         }
      }
      output.maxNrSpots_ = output.spotList_.size();
      
      return output;
   }
   
}
