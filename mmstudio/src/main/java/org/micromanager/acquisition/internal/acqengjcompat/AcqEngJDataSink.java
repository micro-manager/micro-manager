package org.micromanager.acquisition.internal.acqengjcompat;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import mmcorej.TaggedImage;
import mmcorej.org.json.JSONException;
import mmcorej.org.json.JSONObject;
import org.micromanager.acqj.main.AcqEngMetadata;
import org.micromanager.acquisition.internal.AcquisitionEngine;
import org.micromanager.acquisition.internal.DefaultAcquisitionEndedEvent;
import org.micromanager.acquisition.internal.TaggedImageQueue;
import org.micromanager.data.Datastore;
import org.micromanager.data.Metadata;
import org.micromanager.data.Pipeline;
import org.micromanager.data.PipelineErrorException;
import org.micromanager.data.internal.DefaultImage;
import org.micromanager.data.internal.DefaultMetadata;
import org.micromanager.data.internal.PropertyKey;
import org.micromanager.events.EventManager;
import org.micromanager.internal.utils.ReportingUtils;

/**
 * This object spawns a new thread that pulls images from the Acquistion Engine's output queue
 * and adds them to a Pipeline (which in turns send them to the Datastore). It's also
 * responsible for posting the AcquisitionEndedEvent, which it recognizes when
 * it receives the TaggedImageQueue.POISON object.
 *
 * This class is a analagous to DefaultTaggedImageSink, which serves the same
 * function for the Clojure engine
 *
 *
 */
public final class AcqEngJDataSink {

    private final BlockingQueue<TaggedImage> imageProducingQueue_;
    private final Datastore store_;
    private final Pipeline pipeline_;
    private final AcquisitionEngine engine_;
    private final EventManager studioEvents_;

    public AcqEngJDataSink(BlockingQueue<TaggedImage> queue,
                                  Pipeline pipeline,
                                  Datastore store,
                                  AcquisitionEngine engine,
                                  EventManager studioEvents) {
        imageProducingQueue_ = queue;
        pipeline_ = pipeline;
        store_ = store;
        engine_ = engine;
        studioEvents_ = studioEvents;
    }

    public void start() {
        start(null);
    }

    // sinkFullCallback is a way to stop production of images when/if the sink
    // can no longer accept images.
    public void start(final Runnable sinkFullCallback) {
        Thread savingThread = new Thread("TaggedImage sink thread") {

            @Override
            public void run() {
                long t1 = System.currentTimeMillis();
                int imageCount = 0;
                try {
                    while (true) {
                        TaggedImage tagged = imageProducingQueue_.poll(1, TimeUnit.SECONDS);
                        if (tagged != null) {
                            if (TaggedImageQueue.isPoison(tagged)) {
                                // Acquisition has ended. Clean up under "finally"
                                break;
                            }
                            try {
                                ++imageCount;
                                AcqEngJAdapter.addMMImageMetadata(tagged.tags);
                                DefaultImage image = new DefaultImage(tagged);
                                try {
                                    pipeline_.insertImage(image);
                                } catch (PipelineErrorException e) {
                                    // These TODOs inherited from DefaultTaggedImageSink
                                    // TODO: make showing the dialog optional.
                                    // TODO: allow user to cancel acquisition from
                                    // here.
                                    ReportingUtils.showError(e,
                                            "There was an error in processing images.");
                                    pipeline_.clearExceptions();
                                }
                            } catch (OutOfMemoryError e) {
                                handleOutOfMemory(e, sinkFullCallback);
                                break;
                            }
                        }
                    }
                } catch (Exception ex2) {
                    ReportingUtils.logError(ex2);
                } finally {
                    pipeline_.halt();
                    studioEvents_.post(
                            new DefaultAcquisitionEndedEvent(store_, engine_));
                }
                long t2 = System.currentTimeMillis();
                ReportingUtils.logMessage(imageCount + " images stored in " + (t2 - t1) + " ms.");
            }
        };
        savingThread.start();
    }

    // Never called from EDT
    private void handleOutOfMemory(final OutOfMemoryError e,
                                   Runnable sinkFullCallback) {
        ReportingUtils.logError(e);
        if (sinkFullCallback != null) {
            sinkFullCallback.run();
        }

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                JOptionPane.showMessageDialog(null,
                        "Out of memory to store images: " + e.getMessage(),
                        "Out of image storage memory", JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}
