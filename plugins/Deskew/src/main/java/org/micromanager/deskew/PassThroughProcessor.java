package org.micromanager.deskew;

import org.micromanager.data.Image;
import org.micromanager.data.Processor;
import org.micromanager.data.ProcessorContext;
import org.micromanager.data.SummaryMetadata;

/**
 * Simple pass-through processor for Explore mode.
 * Does not modify images, just passes them downstream.
 */
public class PassThroughProcessor implements Processor {

   @Override
   public SummaryMetadata processSummaryMetadata(SummaryMetadata summaryMetadata) {
      return summaryMetadata;
   }

   @Override
   public void processImage(Image image, ProcessorContext context) {
      context.outputImage(image);
   }
}
