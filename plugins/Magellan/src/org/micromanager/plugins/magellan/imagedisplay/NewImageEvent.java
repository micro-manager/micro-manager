///////////////////////////////////////////////////////////////////////////////
// AUTHOR:       Henry Pinkard, henry.pinkard@gmail.com
//
// COPYRIGHT:    University of California, San Francisco, 2015
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
package imagedisplay;

import java.util.HashMap;

/**
 * This class signifies when a new image arrives.
 */
public class NewImageEvent {

   private HashMap<String, Integer> axisToPosition_;
   public NewImageEvent(HashMap<String, Integer> axisToPosition) {
      axisToPosition_ = axisToPosition;
   }
   /**
    * Return the "position" this image has along the specified axis, or 0 if
    * we don't know.
    */
   public int getPositionForAxis(String axis) {
      if (!axisToPosition_.containsKey(axis)) {
         return 0;
      }
      return axisToPosition_.get(axis);
   }
}

