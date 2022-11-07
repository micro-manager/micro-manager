package org.micromanager.acquisition.internal.acqengjcompat;

import mmcorej.TaggedImage;
import org.micromanager.acqj.api.TaggedImageProcessor;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * Simple processor class for diverting images away from AcqEngJ and into an alternate processing/saving system
 */
public class TaggedImageDiverter implements TaggedImageProcessor {

    private BlockingQueue<TaggedImage> diverter_;

    @Override
    public void setDequeues(LinkedBlockingDeque<TaggedImage> source, LinkedBlockingDeque<TaggedImage> sink) {
        diverter_ = source;
    }

    public BlockingQueue<TaggedImage> getQueue() {
        return diverter_;
    }
}
