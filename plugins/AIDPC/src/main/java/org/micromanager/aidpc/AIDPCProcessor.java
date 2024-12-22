package org.micromanager.aidpc;

import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import java.util.ArrayList;
import java.util.List;
import org.micromanager.PropertyMap;
import org.micromanager.Studio;
import org.micromanager.data.Coords;
import org.micromanager.data.Image;
import org.micromanager.data.Processor;
import org.micromanager.data.ProcessorContext;
import org.micromanager.data.SummaryMetadata;


class AIDPCProcessor implements Processor {
   private final Studio studio_;
   private final PropertyMap settings_;
   private final List<Image> images_;
   private final boolean includeAvg_;
   private boolean process_;
   private int ch1Index_;
   private int ch2Index_;
   private int aidpcIndex_;
   private int avgIndex_;

   public AIDPCProcessor(PropertyMap settings, Studio studio) {
      studio_ = studio;
      settings_ = settings;
      images_ = new ArrayList<>();
      includeAvg_ = settings.getBoolean(AIDPCProcessorPlugin.INCLUDE_AVG, true);
      process_ = false;
   }

   @Override
   public SummaryMetadata processSummaryMetadata(SummaryMetadata summary) {
      // Update channel names in summary metadata.
      List<String> chNames = summary.getChannelNameList();
      if (chNames == null || chNames.isEmpty() || chNames.size() < 2) {
         // Can't do anything as we don't know how many names there'll be.
         return summary;
      }

      process_ = true;

      ch1Index_ = -1;
      ch2Index_ = -1;
      final String channel1 = settings_.getString(AIDPCProcessorPlugin.CHANNEL1, "");
      final String channel2_ = settings_.getString(AIDPCProcessorPlugin.CHANNEL2, "");
      for (int i = 0; i < chNames.size(); i++) {
         if (chNames.get(i).equals(channel1)) {
            ch1Index_ = i;
         }
         if (chNames.get(i).equals(channel2_)) {
            ch2Index_ = i;
         }
      }
      if (ch1Index_ < 0 || ch2Index_ < 0) {
         process_ = false;
         return summary;
      }

      int addChannels = 1;
      if (includeAvg_) {
         addChannels++;
      }
      String[] newNames = new String[chNames.size() + addChannels];
      for (int i = 0; i < chNames.size(); i++) {
         newNames[i] = chNames.get(i);
      }
      newNames[chNames.size()] = "AIDPC-" + channel1 + "-" + channel2_;
      aidpcIndex_ = chNames.size();
      if (includeAvg_) {
         newNames[chNames.size() + 1] = "AVG-" + channel1 + "-" + channel2_;
         avgIndex_ = chNames.size() + 1;
      }

      Coords newDimensions = summary.getIntendedDimensions().copyBuilder()
            .c(newNames.length).build();

      return summary.copyBuilder().channelNames(newNames)
            .intendedDimensions(newDimensions).build();
   }

   @Override
   public void processImage(Image newImage, ProcessorContext context) {
      context.outputImage(newImage);

      if (newImage.getNumComponents() > 1) {
         return;
      }
      if (! (newImage.getBytesPerPixel() == 1 || newImage.getBytesPerPixel() == 2)) {
         return;
      }

      if (!process_) {
         return;
      }

      Coords newCoords = newImage.getCoords();
      int c = newImage.getCoords().getC();
      if (!(c == ch1Index_ || c == ch2Index_)) {
         return;
      }

      for (Image oldImage : images_) {
         Coords oldCoords = oldImage.getCoords();
         if (newCoords.copyRemovingAxes(Coords.C).equals(oldCoords.copyRemovingAxes(Coords.C))) {
            if (newCoords.getC() == ch1Index_ && oldCoords.getC() == ch2Index_) {
               process(newImage, oldImage, context);
               images_.remove(oldImage);
               return;
            }

            if (oldCoords.getC() == ch1Index_ && newCoords.getC() == ch2Index_) {
               process(oldImage, newImage, context);
               images_.remove(oldImage);
               return;
            }
         }
      }

      // if we are still here, there was no match, so add this image to our list
      images_.add(newImage);
   }


   private void process(Image image1, Image image2, ProcessorContext context) {
      // Get pixel data
      short[] pixels1 = (short[]) image1.getRawPixels();
      short[] pixels2 = (short[]) image2.getRawPixels();
      int width = image1.getWidth();
      int height = image1.getHeight();

      // Create DPC image
      short[] dpcPixels = new short[pixels1.length];
      short[] avgPixels = includeAvg_ ? new short[pixels1.length] : null;

      for (int i = 0; i < pixels1.length; i++) {
         float i1 = pixels1[i] & 0xFFFF;  // Convert unsigned short to float
         float i2 = pixels2[i] & 0xFFFF;

         // Calculate DPC: 2*(I_R - I_L)/(I_R + I_L)
         float sum = i1 + i2;
         float dpc = 0;
         if (sum > 0) {
            dpc = 2.0f * (i1 - i2) / sum;
         }

         // Scale from [-2,2] to [0,65535]
         dpc = (dpc + 2.0f) * 16383.75f;  // 65535/4 = 16383.75
         dpcPixels[i] = (short) Math.max(0, Math.min(65535, Math.round(dpc)));

         // Calculate average if needed
         if (includeAvg_) {
            avgPixels[i] = (short) (sum / 2);
         }
      }

      // Create and emit DPC image
      ImageProcessor aidpcIP = new ShortProcessor(width, height, dpcPixels, null);
      final Coords aidpcCoords = image1.getCoords().copyBuilder().c(aidpcIndex_).build();
      Image aidpcImage = studio_.data().ij().createImage(aidpcIP, aidpcCoords,
            image1.getMetadata().copyBuilderWithNewUUID()
                  .build());
      context.outputImage(aidpcImage);

      if (includeAvg_) {
         ImageProcessor avgIP = new ShortProcessor(width, height, avgPixels, null);
         final Coords avgCoords = image1.getCoords().copyBuilder().c(avgIndex_).build();
         Image avgImage = studio_.data().ij().createImage(avgIP, avgCoords,
               image1.getMetadata().copyBuilderWithNewUUID()
                     .build());
         context.outputImage(avgImage);
      }
   }

}