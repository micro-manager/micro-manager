package org.micromanager.internal.jacque;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mock core for testing without device adapters.
 * Default constructor: all sequencing queries return false/zero.
 * Constructor taking MockCoreJson: configurable per-test behavior.
 */
public class HelperMockCore implements CoreOps {

   private final String focusDevice;
   private final boolean stageSequenceable;
   private final int stageSequenceMaxLength;
   private final Map<List<String>, Boolean> propSequenceable;
   private final Map<List<String>, Integer> propMaxLength;

   public HelperMockCore() {
      this.focusDevice = "";
      this.stageSequenceable = false;
      this.stageSequenceMaxLength = 0;
      this.propSequenceable = Collections.emptyMap();
      this.propMaxLength = Collections.emptyMap();
   }

   HelperMockCore(HelperGoldenFileIO.MockCoreJson mcj) {
      this.focusDevice = mcj.focusDevice != null
            ? mcj.focusDevice : "";
      this.stageSequenceable = mcj.stageSequenceable != null
            && mcj.stageSequenceable;
      this.stageSequenceMaxLength = mcj.stageSequenceMaxLength != null
            ? mcj.stageSequenceMaxLength : 0;

      Map<List<String>, Boolean> ps = new HashMap<>();
      Map<List<String>, Integer> pm = new HashMap<>();
      if (mcj.propertySequencing != null) {
         for (Map.Entry<String, Map<String, HelperGoldenFileIO.PropertySeqJson>>
               deviceEntry : mcj.propertySequencing.entrySet()) {
            String device = deviceEntry.getKey();
            for (Map.Entry<String, HelperGoldenFileIO.PropertySeqJson>
                  propEntry : deviceEntry.getValue().entrySet()) {
               List<String> key = Arrays.asList(device,
                     propEntry.getKey());
               HelperGoldenFileIO.PropertySeqJson psj =
                     propEntry.getValue();
               ps.put(key, psj.sequenceable != null && psj.sequenceable);
               pm.put(key, psj.maxLength != null ? psj.maxLength : 0);
            }
         }
      }
      this.propSequenceable = Collections.unmodifiableMap(ps);
      this.propMaxLength = Collections.unmodifiableMap(pm);
   }

   @Override
   public boolean isPropertySequenceable(String device, String property)
         throws Exception {
      List<String> key = Arrays.asList(device, property);
      Boolean val = propSequenceable.get(key);
      return val != null && val;
   }

   @Override
   public int getPropertySequenceMaxLength(String device, String property)
         throws Exception {
      List<String> key = Arrays.asList(device, property);
      Integer val = propMaxLength.get(key);
      return val != null ? val : 0;
   }

   @Override
   public String getFocusDevice() {
      return focusDevice;
   }

   @Override
   public boolean isStageSequenceable(String device) throws Exception {
      return stageSequenceable;
   }

   @Override
   public int getStageSequenceMaxLength(String device) throws Exception {
      return stageSequenceMaxLength;
   }

   // Called by Clojure's send-to-debug-log (mm.clj)
   public void logMessage(String msg, boolean debugOnly) {
   }
}
