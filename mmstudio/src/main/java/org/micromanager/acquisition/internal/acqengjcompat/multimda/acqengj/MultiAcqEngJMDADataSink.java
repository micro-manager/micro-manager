package org.micromanager.acquisition.internal.acqengjcompat.multimda.acqengj;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import mmcorej.TaggedImage;
import mmcorej.org.json.JSONException;
import mmcorej.org.json.JSONObject;
import org.micromanager.acqj.api.AcqEngJDataSink;
import org.micromanager.acqj.internal.Engine;
import org.micromanager.acqj.main.AcqEngMetadata;
import org.micromanager.acqj.main.Acquisition;
import org.micromanager.acquisition.internal.DefaultAcquisitionEndedEvent;
import org.micromanager.data.Coords;
import org.micromanager.data.Datastore;
import org.micromanager.data.Pipeline;
import org.micromanager.data.internal.DefaultImage;
import org.micromanager.events.EventManager;
import org.micromanager.internal.utils.ReportingUtils;

/**
 * This object spawns a new thread that pulls images from the Acquistion Engine's output queue
 * and adds them to a Pipeline (which in turns send them to the Datastore). It's also
 * responsible for posting the AcquisitionEndedEvent, which it recognizes when
 * it receives the TaggedImageQueue.POISON object.
 *
 * <p>This class is a analagous to DefaultTaggedImageSink, which serves the same
 * function for the Clojure engine.
 */
public final class MultiAcqEngJMDADataSink implements AcqEngJDataSink {

   private List<Datastore> stores_ = new ArrayList<>();
   private List<Pipeline> pipelines_ = new ArrayList<>();
   private final EventManager studioEvents_;
   private boolean somethingAcquired_ = false;
   private boolean finished_ = false;

   public MultiAcqEngJMDADataSink(EventManager studioEvents) {
      studioEvents_ = studioEvents;
   }

   public void add(Datastore store, Pipeline pipeLine) {
      stores_.add(store);
      pipelines_.add(pipeLine);
   }

   // Never called from EDT
   private void handleOutOfMemory(final OutOfMemoryError e) {
      ReportingUtils.logError(e);

      SwingUtilities.invokeLater(new Runnable() {
         @Override
         public void run() {
            JOptionPane.showMessageDialog(null,
                  "Out of memory to store images: " + e.getMessage(),
                  "Out of image storage memory", JOptionPane.ERROR_MESSAGE);
         }
      });
   }


   @Override
   public void initialize(Acquisition acq, JSONObject summaryMetadata) {

   }

   @Override
   public void finish() {
      for (int i = 0; i < pipelines_.size(); i++) {
         pipelines_.get(i).halt();
         studioEvents_.post(
               new DefaultAcquisitionEndedEvent(stores_.get(i), Engine.getInstance()));
      }
      finished_ = true;
   }

   @Override
   public boolean isFinished() {
      return finished_;
   }

   @Override
   public void putImage(TaggedImage tagged) {
      somethingAcquired_ = true;
      int acqIndex = -1;
      try {
         if (tagged.tags.has("tags")) {
            JSONObject tags = tagged.tags.getJSONObject("tags");
            if (tags.has(MultiAcqEngJAdapter.ACQ_IDENTIFIER)) {
               acqIndex = Integer.parseInt((String) tags.get(MultiAcqEngJAdapter.ACQ_IDENTIFIER));
            }
         } else {
            // TODO: log
            return;
         }
         MultiAcqEngJAdapter.addMMImageMetadata(tagged.tags);
         DefaultImage image = new DefaultImage(tagged);

         // Add any non-standard (ptzc) coords
         List<String> nonStandardAxisNames = AcqEngMetadata.getAxes(tagged.tags).keySet()
               .stream().filter(new Predicate<String>() {
                  @Override
                  public boolean test(String s) {
                     String[] standardAxes = new String[] {AcqEngMetadata.TIME_AXIS,
                           AcqEngMetadata.Z_AXIS, AcqEngMetadata.CHANNEL_AXIS, "position"};
                     return !Arrays.asList(standardAxes).contains(s);
                  }
               }).collect(Collectors.toList());

         Coords.CoordsBuilder cb = image.getCoords().copyBuilder();
         for (String axisName : nonStandardAxisNames) {
            cb.index(axisName, (Integer) AcqEngMetadata.getAxes(tagged.tags).get(axisName));
         }
         image = (DefaultImage) image.copyAtCoords(cb.build());

         // TODO: How do we know which pipeline this image should go into???
         int pipelineNr = acqIndex;
         try {
            pipelines_.get(pipelineNr).insertImage(image);
         } catch (Exception e) {
            // These TODOs inherited from DefaultTaggedImageSink
            // TODO: make showing the dialog optional.
            // TODO: allow user to cancel acquisition from
            // here.
            ReportingUtils.showError(e,
                  "There was an error in processing images.");
            pipelines_.get(pipelineNr).clearExceptions();
         }
      } catch (OutOfMemoryError e) {
         handleOutOfMemory(e);
         throw new RuntimeException(e);
      } catch (JSONException je) {
         throw new RuntimeException(je);
      }
   }

   @Override
   public boolean anythingAcquired() {
      return somethingAcquired_;
   }
}
