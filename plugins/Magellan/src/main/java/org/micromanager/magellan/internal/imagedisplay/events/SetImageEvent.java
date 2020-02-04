/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.magellan.internal.imagedisplay.events;

import java.util.HashMap;
import org.json.JSONObject;

   /**
    * This class signifies that the currently-displayed image needs to be
    * updated.
    */
    public class SetImageEvent {

      // Maps axis labels to their positions. 
      private HashMap<String, Integer> axisToPosition_;
      public final boolean fromHuman_;

      public SetImageEvent(HashMap<String, Integer> axisToPosition, boolean fromHuman) {
         axisToPosition_ = axisToPosition;
         fromHuman_ = fromHuman;
      }

      /**
       * Retrieve the desired position along the specified axis, or 0 if we
       * don't have a marker for that axis.
       */
      public Integer getPositionForAxis(String axis) {
         if (axisToPosition_.containsKey(axis)) {
            return axisToPosition_.get(axis);
         }
         return 0;
      }
   }
