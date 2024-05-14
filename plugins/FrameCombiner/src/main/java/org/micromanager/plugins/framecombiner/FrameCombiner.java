package org.micromanager.plugins.framecombiner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.micromanager.LogManager;
import org.micromanager.Studio;
import org.micromanager.data.Coords;
import org.micromanager.data.Image;
import org.micromanager.data.Processor;
import org.micromanager.data.ProcessorContext;
import org.micromanager.data.SummaryMetadata;

public class FrameCombiner implements Processor {
   private final Studio studio_;
   private final LogManager log_;

   private final String processorAlgo_;
   private final String processorDimension_;
   private final boolean useWholeStack_;
   private int numberOfImagesToProcess_;
   private final List<Integer> channelsToAvoid_;
   private boolean imageNotProcessedFirstTime_ = true;
   private boolean imageCanBeProcessed_ = true;

   private HashMap<Coords, SingleCombinationProcessor> singleAquisitions_;

   public FrameCombiner(Studio studio, String processorDimension, String processorAlgo,
                        boolean useWholeStack, int numberOfImagesToProcess,
                        String channelsToAvoidString) {

      studio_ = studio;
      log_ = studio_.logs();

      processorAlgo_ = processorAlgo;
      useWholeStack_ = useWholeStack;
      processorDimension_ = processorDimension;
      numberOfImagesToProcess_ = numberOfImagesToProcess;
      if (useWholeStack_) {
         numberOfImagesToProcess_ = studio_.acquisitions().getAcquisitionSettings().slices().size();
      }

      // Check whether channelsToAvoidString is correctly formatted
      if (!channelsToAvoidString.isEmpty() && !isValidIntRangeInput(channelsToAvoidString)) {
         log_.showError("\"Channels to avoid\" settings is not valid and will be ignored : "
               + channelsToAvoidString);
         channelsToAvoid_ = Collections.emptyList();
      } else {
         channelsToAvoid_ = convertToList(channelsToAvoidString);
      }

      // log_.logMessage("FrameCombiner : Algorithm applied on stack image is " + processorAlgo_);
      //      log_.logMessage("FrameCombiner : Number of frames to process "
      //            + Integer.toString(numerOfImagesToProcess));
      //      log_.logMessage("FrameCombiner : Channels avoided are "
      //      + channelsToAvoid_.toString() + " (during MDA)");
      // Initialize a hashmap of all combinations of the different acquisitions
      // Each index will be a combination of Z, Channel and StagePosition
      singleAquisitions_ = new HashMap<>();

   }

   @Override
   public void processImage(Image image, ProcessorContext context) {

      if (!imageGoodToProcess(image)) {
         context.outputImage(image);
         return;
      }
      // when live mode is on and user selected to do z project => do nothing
      if (studio_.live().isLiveModeOn()
            && processorDimension_.equals(FrameCombinerPlugin.PROCESSOR_DIMENSION_Z)) {
         context.outputImage(image);
         return;
      }
      // when running MDA without z stack and user want FrameCombiner
      // to combine z frames => do nothing
      if (studio_.getAcquisitionManager().getAcquisitionSettings().slices().isEmpty()
            && processorDimension_.equals(FrameCombinerPlugin.PROCESSOR_DIMENSION_Z)) {
         context.outputImage(image);
         return;
      }

      Coords.CoordsBuilder builder = image.getCoords().copyBuilder();

      if (processorDimension_.equals(FrameCombinerPlugin.PROCESSOR_DIMENSION_TIME)) {
         // Get coords without time (set it to 0)
         builder.time(0);
      } else if (processorDimension_.equals(FrameCombinerPlugin.PROCESSOR_DIMENSION_Z)) {
         // Get coords without z (set it to 0)
         builder.z(0);
      }
      Coords coords = builder.build();

      // If this coordinates index does not exist in singleAquisitions hasmap, create it
      SingleCombinationProcessor singleAcquProc;
      if (!singleAquisitions_.containsKey(coords)) {

         // Check whether this combination of coords are allowed to be processed
         boolean processCombinations =
                 !channelsToAvoid_.contains(coords.getChannel())
                 || studio_.live().isLiveModeOn();

         singleAcquProc = new SingleCombinationProcessor(coords, studio_,
               processorAlgo_, processorDimension_, numberOfImagesToProcess_,
               processCombinations, !channelsToAvoid_.isEmpty());
         singleAquisitions_.put(coords, singleAcquProc);
      } else {
         singleAcquProc = singleAquisitions_.get(coords);
      }

      // This method will output the processed image if needed
      singleAcquProc.addImage(image, context);
   }

   @Override
   public SummaryMetadata processSummaryMetadata(SummaryMetadata summary) {
      if (summary.getIntendedDimensions() != null && channelsToAvoid_.isEmpty()) {
         Coords.CoordsBuilder coordsBuilder = summary.getIntendedDimensions().copyBuilder();
         SummaryMetadata.Builder builder = summary.copyBuilder();
         // Calculate new number of corresponding dimension number
         int newIntendedDimNumber;
         if (processorDimension_.equals(FrameCombinerPlugin.PROCESSOR_DIMENSION_TIME)) {
            newIntendedDimNumber =
                  summary.getIntendedDimensions().getT() / numberOfImagesToProcess_;
            builder.intendedDimensions(coordsBuilder.time(newIntendedDimNumber).build());
         } else if (processorDimension_.equals(FrameCombinerPlugin.PROCESSOR_DIMENSION_Z)) {
            if (useWholeStack_) {
               numberOfImagesToProcess_ = summary.getIntendedDimensions().getZ();
            }
            newIntendedDimNumber =
                  summary.getIntendedDimensions().getZ() / numberOfImagesToProcess_;
            builder.intendedDimensions(coordsBuilder.z(newIntendedDimNumber).build());
         }
         return builder.build();
      } else {
         return summary;
      }
   }

   /**
    * Check if the image can be processed or not.
    *
    * @param image the image to process.
    * @return whether the image is good to process
    */
   private boolean imageGoodToProcess(Image image) {

      if (imageNotProcessedFirstTime_
            && (image.getBytesPerPixel() > 2 || image.getNumComponents() > 1)) {

         if (imageNotProcessedFirstTime_) {
            log_.showError("This type of image cannot be processed by FrameCombiner.");
            imageNotProcessedFirstTime_ = false;
            imageCanBeProcessed_ = false;
         }

         return false;
      } else {
         return imageCanBeProcessed_;
      }
   }

   @Override
   public void cleanup(ProcessorContext context) {

      for (Map.Entry<Coords, SingleCombinationProcessor> entry : singleAquisitions_.entrySet()) {
         entry.getValue().clear();
         singleAquisitions_.put(entry.getKey(), null);
      }
      singleAquisitions_ = null;

   }

   private static Boolean isValidIntRangeInput(String channelToAvoidString) {
      Pattern reValid = Pattern.compile(
            "# Validate comma separated integers/integer ranges.\n"
                  + "^             # Anchor to start of string.         \n"
                  + "[0-9]+        # Integer of 1st value (required).   \n"
                  + "(?:           # Range for 1st value (optional).    \n"
                  + "  -           # Dash separates range integer.      \n"
                  + "  [0-9]+      # Range integer of 1st value.        \n"
                  + ")?            # Range for 1st value (optional).    \n"
                  + "(?:           # Zero or more additional values.    \n"
                  + "  ,           # Comma separates additional values. \n"
                  + "  [0-9]+      # Integer of extra value (required). \n"
                  + "  (?:         # Range for extra value (optional).  \n"
                  + "    -         # Dash separates range integer.      \n"
                  + "    [0-9]+    # Range integer of extra value.      \n"
                  + "  )?          # Range for extra value (optional).  \n"
                  + ")*            # Zero or more additional values.    \n"
                  + "$             # Anchor to end of string.           ",
            Pattern.COMMENTS);
      Matcher m = reValid.matcher(channelToAvoidString);
      return m.matches();
   }

   private static List<Integer> convertToList(String channelToAvoidString) {
      Pattern reNextVal = Pattern.compile(
            "# extract next integers/integer range value.    \n"
                  + "([0-9]+)      # $1: 1st integer (Base).         \n"
                  + "(?:           # Range for value (optional).     \n"
                  + "  -           # Dash separates range integer.   \n"
                  + "  ([0-9]+)    # $2: 2nd integer (Range)         \n"
                  + ")?            # Range for value (optional). \n"
                  + "(?:,|$)       # End on comma or string end.",
            Pattern.COMMENTS);

      Matcher m = reNextVal.matcher(channelToAvoidString);

      List<Integer> channelsToAvoid = new ArrayList<Integer>();

      while (m.find()) {
         if (m.group(2) != null) {
            int start = Integer.parseInt(m.group(1));
            int end = Integer.parseInt(m.group(2));
            for (int i = start; i <= end; i++) {
               channelsToAvoid.add(i);
            }
         } else {
            channelsToAvoid.add(Integer.parseInt(m.group(1)));
         }
      }

      // Remove duplicate entries
      channelsToAvoid = new ArrayList<>(new LinkedHashSet<>(channelsToAvoid));

      return channelsToAvoid;
   }

}
