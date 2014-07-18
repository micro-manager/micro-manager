package org.micromanager.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Set;

import org.micromanager.api.data.Coords;

/**
 * ImageCoords indicate the position of a given image within a dataset.
 */
public class ImageCoords implements Coords {
   // Maps axis labels to our position along those axes.
   private HashMap<String, int> axisToPos_;
   // Axes for which we are the last image along that axis.
   private Set<String> terminalAxes_;
   // Convenience/optimization: we maintain a sorted list of our axes.
   private ArrayList<String> sortedAxes_;

   public ImageCoords() {
      initMembers();
   }

   /**
    * Generate a new ImageCoords that's a copy of the provided Coords. 
    * It may be null, in which case this is functionally identical to the
    * default constructor.
    */
   public ImageCoords(Coords alt) {
      initMembers();
      if (alt != null) {
         // Copy positional information over.
         for (String axis : alt.getAxes()) {
            setPosition(axis, alt.getPositionAt(axis));
         }
      }
   }

   /** Initialize member fields. */
   private void initMembers() {
      axisToPos_ = new HashMap<String, int>();
      terminalAxes_ = new Set<String>();
      sortedAxes_ = new ArrayList<String>();
   }

   public int getPositionAt(String axis) {
      if (axisToPos_.contains(axis)) {
         return axisToPos_.get(axis);
      }
      return -1;
   }
   
   public void setPosition(String axis, int position) {
      axisToPos_.put(axis, position);
      sortedAxes_.add(axis);
      try {
         Collections.sort(sortedAxes_);
      }
      catch (UnsupportedOperationException e) {
         ReportingUtils.logError(e, "Unable to sort coordinate axes");
      }
   }

   public boolean getIsAxisEndFor(String axis) {
      return terminalAxes_.contains(axis);
   }

   public setIsAxisEndFor(String axis) {
      terminalAxes_.add(axis);
   }

   public Set<String> getTerminalAxes() {
      return new Set<String>(terminalAxes_);
   }

   public List<String> getAxes() {
      return new List<String>(sortedAxes_);
   }

   public Coords makeOffsetCopy(HashMap<String, int> offsets) {
      Coords result = new ImageCoords(this);
      for (String axis : offsets.keys()) {
         result.setPosition(axis, 
               result.getPositionAt(axis) + offsets.get(axis));
      }
      return result;
   }
}
