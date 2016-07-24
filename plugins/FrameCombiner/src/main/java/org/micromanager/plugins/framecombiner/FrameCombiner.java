package org.micromanager.plugins.framecombiner;

import java.util.ArrayList;
import java.util.Arrays;
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

public class FrameCombiner extends Processor {

   private final Studio studio_;
   private final LogManager log_;

   private final String processorAlgo_;
   private final int numerOfImagesToProcess_;
   private final List<Integer> channelsToAvoid_;

   private boolean imageNotProcessedFirstTime_ = true;
   private boolean imageCanBeProcessed_ = true;

   private int newIntendedTime_;

   private HashMap<Coords, SingleCombinationProcessor> singleAquisitions_;

   public FrameCombiner(Studio studio, String processorAlgo,
           int numerOfImagesToProcess, String channelsToAvoidString) {

      studio_ = studio;
      log_ = studio_.logs();

      processorAlgo_ = processorAlgo;
      numerOfImagesToProcess_ = numerOfImagesToProcess;

      // Check whether channelsToAvoidString is correctly formated
      if (!channelsToAvoidString.isEmpty() && !isValidIntRangeInput(channelsToAvoidString)) {
         log_.showError("\"Channels to avoid\" settings is not valid and will be ignored : " + channelsToAvoidString);
         channelsToAvoid_ = Arrays.asList(new Integer[0]);
      } else {
         channelsToAvoid_ = convertToList(channelsToAvoidString);
      }

//      log_.logMessage("FrameCombiner : Algorithm applied on stack image is " + processorAlgo_);
//      log_.logMessage("FrameCombiner : Number of frames to process " + Integer.toString(numerOfImagesToProcess));
//      log_.logMessage("FrameCombiner : Channels avoided are " + channelsToAvoid_.toString() + " (during MDA)");
      // Initialize a hashmap of all combinations of the different acquisitions
      // Each index will be a combination of Z, Channel and StagePosition
      singleAquisitions_ = new HashMap();

   }

   @Override
   public void processImage(Image image, ProcessorContext context) {

      if (!imageGoodToProcess(image)) {
         context.outputImage(image);
         return;
      }

      // Get coords without time (set it to 0)
      Coords coords = image.getCoords().copy().time(0).build();

      // If this coordinates index does not exist in singleAquisitions hasmap, create it
      SingleCombinationProcessor singleAcquProc;
      if (!singleAquisitions_.containsKey(coords)) {

         // Check whether this combinations of coords are allowed to be processed
         boolean processCombinations = true;
         if (channelsToAvoid_.contains(coords.getChannel()) && !studio_.live().getIsLiveModeOn()) {
            processCombinations = false;
         }

         singleAcquProc = new SingleCombinationProcessor(coords, studio_, processorAlgo_,
                 numerOfImagesToProcess_, processCombinations, !channelsToAvoid_.isEmpty());
         singleAquisitions_.put(coords, singleAcquProc);
      } else {
         singleAcquProc = singleAquisitions_.get(coords);
      }

      // This method will output the processed image if needed
      singleAcquProc.addImage(image, context);
   }

   @Override
   public SummaryMetadata processSummaryMetadata(SummaryMetadata summary) {

      if (summary.getIntendedDimensions() != null) {

         // Calculate new number of times
         newIntendedTime_ = (int) (summary.getIntendedDimensions().getTime() / numerOfImagesToProcess_);

         Coords.CoordsBuilder coordsBuilder = summary.getIntendedDimensions().copy();
         SummaryMetadata.SummaryMetadataBuilder builder = summary.copy();
         builder.intendedDimensions(coordsBuilder.time(newIntendedTime_).build());

         return builder.build();
      } else {
         return summary;
      }
   }

   /**
    * Check if the image can be processed or not.
    *
    * @param image the image to process.
    * @return
    */
   public boolean imageGoodToProcess(Image image) {

      if (imageNotProcessedFirstTime_ && (image.getBytesPerPixel() > 2 || image.getNumComponents() > 1)) {

         if (imageNotProcessedFirstTime_) {
            log_.showError("This type of image cannot be processed by FrameCombiner.");
            imageNotProcessedFirstTime_ = false;
            imageCanBeProcessed_ = false;
         }

         return false;
      } else return imageCanBeProcessed_;
   }

   @Override
   public void cleanup(ProcessorContext context) {

      for (Map.Entry<Coords, SingleCombinationProcessor> entry : singleAquisitions_.entrySet()) {
         entry.getValue().clear();
         singleAquisitions_.put(entry.getKey(), null);
      }
      singleAquisitions_ = null;

   }

   public static Boolean isValidIntRangeInput(String channelToAvoidString) {
      Pattern re_valid = Pattern.compile(
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
      Matcher m = re_valid.matcher(channelToAvoidString);
      if (m.matches()) {
         return true;
      } else {
         return false;
      }
   }

   public static List<Integer> convertToList(String channelToAvoidString) {
      Pattern re_next_val = Pattern.compile(
              "# extract next integers/integer range value.    \n"
              + "([0-9]+)      # $1: 1st integer (Base).         \n"
              + "(?:           # Range for value (optional).     \n"
              + "  -           # Dash separates range integer.   \n"
              + "  ([0-9]+)    # $2: 2nd integer (Range)         \n"
              + ")?            # Range for value (optional). \n"
              + "(?:,|$)       # End on comma or string end.",
              Pattern.COMMENTS);

      Matcher m = re_next_val.matcher(channelToAvoidString);

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
      channelsToAvoid = new ArrayList<Integer>(new LinkedHashSet<Integer>(channelsToAvoid));

      return channelsToAvoid;
   }

}
