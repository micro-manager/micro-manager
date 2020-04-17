///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Multipage TIFF
//-----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger, cweisiger@msg.ucsf.edu
//
// COPYRIGHT:    University of California, San Francisco, 2012-2015
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
package org.micromanager.data.internal.multipagetiff;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeSet;
import java.util.UUID;
import org.micromanager.PropertyMap;
import org.micromanager.data.Coords;
import org.micromanager.data.Image;
import org.micromanager.data.Metadata;
import org.micromanager.data.internal.DefaultCoords;
import org.micromanager.data.internal.DefaultMetadata;
import org.micromanager.data.internal.DefaultSummaryMetadata;
import org.micromanager.internal.propertymap.NonPropertyMapJSONFormats;
import org.micromanager.internal.utils.ReportingUtils;


/**
 * Class encapsulating a single File (or series of files)
 * Default is one file series per xy posititon
 */
class FileSet {
   private static final int SPACE_FOR_PARTIAL_OME_MD = 2000; //this should be more than enough

   private final LinkedList<MultipageTiffWriter> tiffWriters_;
   private FileWriter mdWriter_;
   private final OMEMetadata omeMetadata_;
   private final String baseFilename_;
   private String currentTiffFilename_;
   private String currentTiffUUID_;;
   private String metadataFileFullPath_;
   private boolean finished_ = false;
   private boolean separateMetadataFile_ = false;
   private boolean splitByXYPosition_ = false;
   private boolean expectedImageOrder_ = true;
   private int ifdCount_ = 0;
   private final StorageMultipageTiff masterStorage_;
   int nextExpectedChannel_ = 0, nextExpectedSlice_ = 0, nextExpectedFrame_ = 0;
   int currentFrame_ = 0;

   
   public FileSet(Image firstImage, StorageMultipageTiff masterStorage,
         OMEMetadata omeMetadata,
         boolean splitByXYPosition, boolean separateMetadataFile)
      throws IOException {
      tiffWriters_ = new LinkedList<>();  
      masterStorage_ = masterStorage;
      omeMetadata_ = omeMetadata;
      splitByXYPosition_ = splitByXYPosition;
      separateMetadataFile_ = separateMetadataFile;

      //get file path and name
      baseFilename_ = createBaseFilename(firstImage);
      currentTiffFilename_ = baseFilename_ + ".ome.tif";
      currentTiffUUID_ = "urn:uuid:" + UUID.randomUUID().toString();
      //make first writer
      tiffWriters_.add(new MultipageTiffWriter(masterStorage_,
            firstImage, currentTiffFilename_));

      if (separateMetadataFile_) {
         startMetadataFile();
      }
   }

   public String getCurrentUUID() {
      return currentTiffUUID_;
   }
   
   public String getCurrentFilename() {
      return currentTiffFilename_;
   }
   
   public boolean hasSpaceForFullOMEXML(int mdLength) {
      return tiffWriters_.getLast().hasSpaceForFullOMEMetadata(mdLength);
   }
   
   public void finished(String omeXML, String ijDescription) throws IOException {
      if (finished_) {
         return;
      }

      if (separateMetadataFile_) {
         finishMetadataFile();
      }

      //only need to finish last one here because previous ones in set are finished as they fill up with images
      tiffWriters_.getLast().finish();
      //close all
      for (MultipageTiffWriter w : tiffWriters_) {
         w.close(omeXML, ijDescription);
      }
      finished_ = true;
   }

   public void closeFileDescriptors() {
      for (MultipageTiffWriter writer : tiffWriters_) {
         try {
            writer.getReader().close();
         }
         catch (IOException e) {
            ReportingUtils.logError(e, "Error closing file descriptor");
         }
      }
   }

   public MultipageTiffReader getCurrentReader() {
      return tiffWriters_.getLast().getReader();
   }

   public int getCurrentFrame() {
      return currentFrame_;
   }

   public void writeImage(final Image imgIn) throws IOException {
      //Add filename to image tags - needed by hasSpaceToWrite function
      Image img = imgIn.copyWithMetadata(imgIn.getMetadata().
            copyBuilderPreservingUUID().fileName(currentTiffFilename_).
            build());
      //check if current writer is out of space, if so, make a new one
      if (!tiffWriters_.getLast().hasSpaceToWrite(img, SPACE_FOR_PARTIAL_OME_MD)) {
         //write index map here but still need to call close() at end of acq
         tiffWriters_.getLast().finish();          

         currentTiffFilename_ = baseFilename_ + "_" + tiffWriters_.size() + ".ome.tif";
         currentTiffUUID_ = "urn:uuid:" + UUID.randomUUID().toString();
         ifdCount_ = 0;
         tiffWriters_.add(new MultipageTiffWriter(masterStorage_,
               img, currentTiffFilename_));
         
         //Add new filename to image tags
         img = img.copyWithMetadata(img.getMetadata().
               copyBuilderPreservingUUID().fileName(currentTiffFilename_).
               build());
      }      

      //write image
      tiffWriters_.getLast().writeImage(img);

      if (expectedImageOrder_) {
         if (splitByXYPosition_) {
            checkForExpectedImageOrder(img.getCoords());
         } else {
            expectedImageOrder_ = false;
         }
      }

      //write metadata
      try {
         //Check if missing planes need to be added OME metadata
         int frame = img.getCoords().getTimePoint();
         int position = img.getCoords().getStagePosition();
         if (frame > currentFrame_) {
            //check previous frame for missing IFD's in OME metadata
            omeMetadata_.fillInMissingTiffDatas(currentFrame_, position);
         }
         //reset in case acquisitin order is position then time and all files not split by position
         currentFrame_ = frame;

         omeMetadata_.addImageTagsToOME(img.getCoords(), img.getMetadata(),
               ifdCount_, baseFilename_, currentTiffFilename_, currentTiffUUID_);
      } catch (Exception ex) {
         ReportingUtils.logError(ex, "Problem writing OME metadata");
      }

      int frame = img.getCoords().getTimePoint();
      masterStorage_.updateLastFrame(frame);
      int pos = img.getCoords().getStagePosition();
      masterStorage_.updateLastPosition(pos);

      if (separateMetadataFile_) {
         writeToMetadataFile(img.getCoords(), img.getMetadata());
      }
      ifdCount_++;
   }

   private void writeToMetadataFile(Coords coords, Metadata md) {
      try {
         mdWriter_.write(",\n\"FrameKey-" + coords.getTimePoint() +
               "-" + coords.getChannel() + "-" + coords.getZSlice() + "\": ");
         PropertyMap pmap = ((DefaultMetadata) md).toPropertyMap();
         mdWriter_.write(NonPropertyMapJSONFormats.metadata().toJSON(pmap));
      } catch (IOException ex) {
         ReportingUtils.logError("Problem writing to metadata.txt file");
      }
   }

   private void startMetadataFile() {
      metadataFileFullPath_ = masterStorage_.getDiskLocation() + "/" +
            baseFilename_ + "_metadata.txt";
      PropertyMap summaryPmap = ((DefaultSummaryMetadata) masterStorage_.
            getSummaryMetadata()).toPropertyMap();
      String summaryJSON = NonPropertyMapJSONFormats.summaryMetadata().toJSON(
            summaryPmap);
      try {
         mdWriter_ = new FileWriter(metadataFileFullPath_);
         mdWriter_.write("{" + "\n");
         mdWriter_.write("\"Summary\": ");
         mdWriter_.write(summaryJSON);
      }
      catch (IOException ex) {
         ReportingUtils.logError("Problem creating metadata.txt file");
      }
   }

   private void finishMetadataFile() {
      try {
         mdWriter_.write("\n}\n");
         mdWriter_.close();
      } catch (IOException ex) {
         ReportingUtils.logError("Problem creating metadata.txt file");
      }
   }

   private String createBaseFilename(Image firstImage) {
      String baseFilename;
      String prefix = masterStorage_.getSummaryMetadata().getPrefix();
      if (prefix == null || prefix.length() == 0) {
         File dir = new File (masterStorage_.getDiskLocation());
         prefix = dir.getName();
      }
      if (prefix == null || prefix.length() == 0) {
         baseFilename = "MMStack";
      } else {
         baseFilename = prefix + "_MMStack";
      }

      if (splitByXYPosition_) {
         String posName = firstImage.getMetadata().getPositionName("");
         int posIndex = firstImage.getCoords().getStagePosition();
         if (posIndex == -1) {
            posIndex = 0;
         }
         if (posName != null && !posName.isEmpty()) {
            baseFilename += "_" + posName;
         }
         else {
            baseFilename += "_" + "Pos" + posIndex;
         }
      }
      return baseFilename;
   }

   /**
    * Generate all expected labels for the last frame, and write dummy images
    * for ones that weren't written. Modify ImageJ and OME max number of frames
    * as appropriate.  This method only works if xy positions are split across
    * separate files
    */
   public void finishAbortedAcqIfNeeded() {
      if (expectedImageOrder_ && splitByXYPosition_ && !masterStorage_.timeFirst()) {
         try {
            //One position may be on the next frame compared to others. Complete each position
            //with blank images to fill this frame
            completeFrameWithBlankImages(masterStorage_.lastAcquiredFrame());
         } catch (Exception e) {
            ReportingUtils.logError("Problem finishing aborted acq with blank images");
         }
      }
   }

   /*
    * Completes the current time point of an aborted acquisition with blank
    * images, so that it can be opened correctly by ImageJ/BioForamts
    */
   private void completeFrameWithBlankImages(int frame) {
      int numFrames = masterStorage_.getIntendedSize(Coords.T);
      int numSlices = masterStorage_.getIntendedSize(Coords.Z);
      int numChannels = masterStorage_.getIntendedSize(Coords.CHANNEL);
      if (numFrames > frame + 1 ) {
         HashSet<Coords> writtenImages = new HashSet<>();
         for (MultipageTiffWriter w : tiffWriters_) {
            writtenImages.addAll(w.getIndexMap().keySet());
            w.setAbortedNumFrames(frame + 1);
         }
         Iterator<Coords> iterator = writtenImages.iterator();
         int positionIndex = 0;
         if (iterator.hasNext()) {
            positionIndex = iterator.next().getStagePosition();
         }
         omeMetadata_.setNumFrames(positionIndex, frame + 1);
         TreeSet<Coords> lastFrameCoords = new TreeSet<>();
         DefaultCoords.Builder builder = new DefaultCoords.Builder();
         builder.t(frame);
         builder.stagePosition(positionIndex);
         for (int c = 0; c < numChannels; c++) {
            builder.channel(c);
            for (int z = 0; z < numSlices; z++) {
               builder.z(z);
               lastFrameCoords.add(builder.build());
            }
         }
         lastFrameCoords.removeAll(writtenImages);
         try {
            for (Coords coords : lastFrameCoords) {
               tiffWriters_.getLast().writeBlankImage();
               Metadata dummyMD = new DefaultMetadata.Builder().build();
               omeMetadata_.addImageTagsToOME(coords, dummyMD, ifdCount_,
                     baseFilename_, currentTiffFilename_, currentTiffUUID_);
            }
         } catch (IOException ex) {
            ReportingUtils.logError("problem writing dummy image");
         }
      }
   }
   
   void checkForExpectedImageOrder(Coords coords) {
      //Determine next expected indices
      int channel = coords.getChannel();
      int frame = coords.getTimePoint();
      int slice = coords.getZSlice();
      if (slice != nextExpectedSlice_ || channel != nextExpectedChannel_ ||
              frame != nextExpectedFrame_) {
         expectedImageOrder_ = false;
      }
      //Figure out next expected indices
      boolean areSlicesFirst = true;
      List<String> order = masterStorage_.getSummaryMetadata().getOrderedAxes();
      if (order != null) {
         areSlicesFirst = order.indexOf(Coords.Z) < order.indexOf(Coords.CHANNEL);
      }
      if (areSlicesFirst) {
         nextExpectedSlice_ = slice + 1;
         if (nextExpectedSlice_ == masterStorage_.getIntendedSize(Coords.Z)) {
            nextExpectedSlice_ = 0;
            nextExpectedChannel_ = channel + 1;
            if (nextExpectedChannel_ == masterStorage_.getIntendedSize(Coords.CHANNEL)) {
               nextExpectedChannel_ = 0;
               nextExpectedFrame_ = frame + 1;
            }
         }
      } else {
         nextExpectedChannel_ = channel + 1;
         if (nextExpectedChannel_ == masterStorage_.getIntendedSize(Coords.CHANNEL)) {
            nextExpectedChannel_ = 0;
            nextExpectedSlice_ = slice + 1;
            if (nextExpectedSlice_ == masterStorage_.getIntendedSize(Coords.Z)) {
               nextExpectedSlice_ = 0;
               nextExpectedFrame_ = frame + 1;
            }
         }
      }
   }
}