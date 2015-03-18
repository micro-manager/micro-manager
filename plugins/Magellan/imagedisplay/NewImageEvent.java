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
