package org.micromanager.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.micromanager.api.data.Coords;
import org.micromanager.utils.ReportingUtils;

/**
 * DefaultCoords indicate the position of a given image within a dataset.
 */
public class DefaultCoords implements Coords {
   // Maps axis labels to our position along those axes.
   private HashMap<String, Integer> axisToPos_;
   // Axes for which we are the last image along that axis.
   private Set<String> terminalAxes_;
   // Convenience/optimization: we maintain a sorted list of our axes.
   private ArrayList<String> sortedAxes_;

   public DefaultCoords() {
      initMembers();
   }

   /**
    * Generate a new DefaultCoords that's a copy of the provided Coords. 
    * It may be null, in which case this is functionally identical to the
    * default constructor.
    */
   public DefaultCoords(Coords alt) {
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
      axisToPos_ = new HashMap<String, Integer>();
      terminalAxes_ = new HashSet<String>();
      sortedAxes_ = new ArrayList<String>();
   }

   public int getPositionAt(String axis) {
      if (axisToPos_.containsKey(axis)) {
         return axisToPos_.get(axis);
      }
      return -1;
   }
   
   public void setPosition(String axis, int position) {
      axisToPos_.put(axis, new Integer(position));
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

   public void setIsAxisEndFor(String axis) {
      terminalAxes_.add(axis);
   }

   public Set<String> getTerminalAxes() {
      return new HashSet<String>(terminalAxes_);
   }

   public List<String> getAxes() {
      return new ArrayList<String>(sortedAxes_);
   }

   public Coords makeOffsetCopy(HashMap<String, Integer> offsets) {
      Coords result = new DefaultCoords(this);
      for (String axis : offsets.keySet()) {
         result.setPosition(axis, 
               result.getPositionAt(axis) + offsets.get(axis));
      }
      return result;
   }
}
