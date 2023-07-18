package org.micromanager.deskew;

import java.util.ArrayList;
import java.util.List;
import org.micromanager.data.Image;
import org.micromanager.data.Processor;
import org.micromanager.data.ProcessorContext;
import org.micromanager.data.SummaryMetadata;
import org.micromanager.lightsheet.StackResampler;

public class DeskewProcessor implements Processor {
   private SummaryMetadata inputSummaryMetadata_;
   private List<Image> imageList_ = new ArrayList<>();

   @Override
   public SummaryMetadata processSummaryMetadata(SummaryMetadata summaryMetadata) {
      inputSummaryMetadata_ = summaryMetadata;
      return inputSummaryMetadata_;
   }

   @Override
   public void processImage(Image image, ProcessorContext context) {
      if (image.getCoords().getZ() == 0) {
         imageList_.clear();
      }
      imageList_.add(image);

      if (image.getCoords().getZ() == inputSummaryMetadata_.getIntendedDimensions().getZ()) {
         // do the deskew here
         StackResampler resampler = new StackResampler(StackResampler.FULL_VOLUME, false, 15,
                  image.getMetadata().getPixelSizeUm(), inputSummaryMetadata_.getZStepUm(),
                  imageList_.size(), image.getWidth(), image.getHeight());
      }

      context.outputImage(image);

   }

   @Override
   public void cleanup(ProcessorContext context) {
   }
}
