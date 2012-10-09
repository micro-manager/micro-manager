///////////////////////////////////////////////////////////////////////////////
//FILE:          ImageLabelComparator.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Henry Pinkard, henry.pinkard@gmail.com, 2012
//
// COPYRIGHT:    University of California, San Francisco, 2012
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
package org.micromanager.utils;

import java.util.Comparator;

public class ImageLabelComparator implements Comparator<String> {

   private final boolean slicesFirst_;
   private final boolean timeFirst_;

   public ImageLabelComparator() {
      this(false, false);
   }

   public ImageLabelComparator(boolean slicesFirst, boolean timeFirst) {
      super();
      slicesFirst_ = slicesFirst;
      timeFirst_ = timeFirst;
   }

   public boolean getSlicesFirst() {
      return slicesFirst_;
   }

   public boolean getTimeFirst() {
      return timeFirst_;
   }

   @Override
   public int compare(String s1, String s2) {
      //c_s_f_p
      String[] indices1 = s1.split("_");
      String[] indices2 = s2.split("_");
      if (timeFirst_) {
         int position1 = Integer.parseInt(indices1[3]), position2 = Integer.parseInt(indices2[3]);
         if (position1 != position2) {
            return position1 - position2;
         }
         int frame1 = Integer.parseInt(indices1[2]), frame2 = Integer.parseInt(indices2[2]);
         if (frame1 != frame2) {
            return frame1 - frame2;
         }
      } else {
         int frame1 = Integer.parseInt(indices1[2]), frame2 = Integer.parseInt(indices2[2]);
         if (frame1 != frame2) {
            return frame1 - frame2;
         }
         int position1 = Integer.parseInt(indices1[3]), position2 = Integer.parseInt(indices2[3]);
         if (position1 != position2) {
            return position1 - position2;
         }
      }
      if (slicesFirst_) {
         int channel1 = Integer.parseInt(indices1[0]), channel2 = Integer.parseInt(indices2[0]);
         if (channel1 != channel2) {
            return channel1 - channel2;
         }
         return Integer.parseInt(indices1[1]) - Integer.parseInt(indices2[1]);
      } else {
         int slice1 = Integer.parseInt(indices1[1]), slice2 = Integer.parseInt(indices2[1]);
         if (slice1 != slice2) {
            return slice1 - slice2;
         }
         return Integer.parseInt(indices1[0]) - Integer.parseInt(indices2[0]);
      }
   }
}
