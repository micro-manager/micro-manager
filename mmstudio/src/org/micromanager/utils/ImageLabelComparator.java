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
      this(false,false);
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
         int position = indices1[3].compareTo(indices2[3]);
         if (position != 0) {
            return position;
         }
         int time = indices1[2].compareTo(indices2[2]);
         if (time != 0) {
            return time;
         }
      } else {
         int time = indices1[2].compareTo(indices2[2]);
         if (time != 0) {
            return time;
         }
         int position = indices1[3].compareTo(indices2[3]);
         if (position != 0) {
            return position;
         }
      }
      if (slicesFirst_) {
         int channel = indices1[0].compareTo(indices2[0]);
         if (channel != 0) {
            return channel;
         }
         int slice = indices1[1].compareTo(indices2[1]);
         if (slice != 0) {
            return slice;
         }
      } else {
         int slice = indices1[1].compareTo(indices2[1]);
         if (slice != 0) {
            return slice;
         }
         int channel = indices1[0].compareTo(indices2[0]);
         if (channel != 0) {
            return channel;
         }
      }
      return 0;
   }
   
 
   
}
