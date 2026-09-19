package org.micromanager.internal.jacque;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class TriggerSequence {
   public Map<List<String>, List<String>> properties;
   public List<Double> slices;

   public TriggerSequence() {
      this.properties = new HashMap<>();
      this.slices = null;
   }
}
