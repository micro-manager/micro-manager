///////////////////////////////////////////////////////////////////////////////
//FILE:          TaggedImageStorageMultipageTiff.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Henry Pinkard, henry.pinkard@gmail.com, 2012
//
// COPYRIGHT:    University of California, San Francisco, 2012
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
package org.micromanager.acquisition;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteOrder;
import java.util.*;
import loci.common.services.ServiceFactory;
import loci.formats.MetadataTools;
import loci.formats.meta.IMetadata;
import loci.formats.services.OMEXMLService;
import mmcorej.TaggedImage;
import ome.xml.model.primitives.Color;
import ome.xml.model.primitives.NonNegativeInteger;
import ome.xml.model.primitives.PositiveFloat;
import ome.xml.model.primitives.PositiveInteger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.MMStudioMainFrame;
import org.micromanager.api.TaggedImageStorage;
import org.micromanager.utils.*;




public final class TaggedImageStorageMultipageTiff implements TaggedImageStorage {

   private JSONObject summaryMetadata_;
   private JSONObject displayAndComments_;
   private boolean newDataSet_;
   private int lastFrameOpenedDataSet_ = -1;
   private String directory_;
   private Thread shutdownHook_;
   private int numPositions_;
   private CachedImages cached_;
   final public boolean omeTiff_;
   final private boolean seperateMetadataFile_;
   private IMetadata omeMD_;
   private boolean finished_ = false;
   private boolean expectedImageOrder_ = true;
   private int numChannels_, numSlices_;
  
   //used for estimating total length of ome xml
   private int totalNumImagePlanes_ = 0;
   private int omeXMLBaseLength_ = -1;
   private int omeXMLImageLength_ = -1;
      
   //map of position indices to objects associated with each
   private HashMap<Integer, Position> positions_;
   
   //Map of image labels to file 
   private TreeMap<String, MultipageTiffReader> tiffReadersByLabel_;
  
   public TaggedImageStorageMultipageTiff(String dir, Boolean newDataSet, JSONObject summaryMetadata) throws IOException {            
      this(dir, newDataSet, summaryMetadata, MMStudioMainFrame.getInstance().getOMETiffEnabled(),
              MMStudioMainFrame.getInstance().getMetadataFileWithMultipageTiff());
   }
   
   /*
    * Constructor that doesnt make reference to MMStudioMainFrame so it can be used independently of MM GUI
    */
   public TaggedImageStorageMultipageTiff(String dir, boolean newDataSet, JSONObject summaryMetadata, 
           boolean omeTiff, boolean seperateMDFile) throws IOException {
      omeTiff_ = omeTiff;
      seperateMetadataFile_ = seperateMDFile;

      newDataSet_ = newDataSet;
      directory_ = dir;
      tiffReadersByLabel_ = new TreeMap<String, MultipageTiffReader>(new ImageLabelComparator());
      cached_ = new CachedImages();
      setSummaryMetadata(summaryMetadata);

      // TODO: throw error if no existing dataset
      if (!newDataSet_) {
         openExistingDataSet();
      }    
      
      //add shutdown hook --> thread to be run when JVM shuts down
      shutdownHook_ = new Thread() {
         @Override
         public void run() {
            finished();
            writeDisplaySettings();
         }
      };    
      Runtime.getRuntime().addShutdownHook(this.shutdownHook_); 
   }
   
   private void processSummaryMD() {
      displayAndComments_ = VirtualAcquisitionDisplay.getDisplaySettingsFromSummary(summaryMetadata_);
      try {
         numPositions_ = MDUtils.getNumPositions(summaryMetadata_);
         if (numPositions_ <= 0) {
            numPositions_ = 1;
         }
      } catch (JSONException ex) {
         ReportingUtils.logError(ex);
         numPositions_ = 1;
      }
      try {
         //Estimate of max number of image planes
         numChannels_ = MDUtils.getNumChannels(summaryMetadata_);
         numSlices_ = MDUtils.getNumSlices(summaryMetadata_);
         totalNumImagePlanes_ = numChannels_ * MDUtils.getNumFrames(summaryMetadata_)
                 * numPositions_ * numSlices_;
      } catch (Exception ex) {
         ReportingUtils.logError("Error estimating total number of image planes");
         totalNumImagePlanes_ = 1;
      }
   }
   
   boolean slicesFirst() {
      return ((ImageLabelComparator) tiffReadersByLabel_.comparator()).getSlicesFirst();
   }
   
   boolean timeFirst() {
      return ((ImageLabelComparator) tiffReadersByLabel_.comparator()).getTimeFirst();
   }

   private void openExistingDataSet() throws IOException {
      //Need to throw error if file not found

      MultipageTiffReader reader = null;
      File dir = new File(directory_);
      for (File f : dir.listFiles()) {
         if (f.getName().endsWith(".tif")) {
            reader = new MultipageTiffReader(f);
            Set<String> labels = reader.getIndexKeys();
            for (String label : labels) {
               tiffReadersByLabel_.put(label, reader);
               int frameIndex = Integer.parseInt(label.split("_")[2]);
               lastFrameOpenedDataSet_ = Math.max(frameIndex, lastFrameOpenedDataSet_);
            }
         }
      }

      try {
         setSummaryMetadata(reader.getSummaryMetadata());
         numPositions_ = MDUtils.getNumPositions(summaryMetadata_);
         displayAndComments_ = reader.getDisplayAndComments();
      } catch (JSONException ex) {
         ReportingUtils.logError(ex);
      } 
   }
   
   @Override
   public TaggedImage getImage(int channelIndex, int sliceIndex, int frameIndex, int positionIndex) {
      String label = MDUtils.generateLabel(channelIndex, sliceIndex, frameIndex, positionIndex);
      TaggedImage img = cached_.get(label);
      if (img != null) {
         return img;
      }
      if (!tiffReadersByLabel_.containsKey(label)) {
         return null;
      }
      return tiffReadersByLabel_.get(label).readImage(label);   
   }

   @Override
   public JSONObject getImageTags(int channelIndex, int sliceIndex, int frameIndex, int positionIndex) {
      String label = MDUtils.generateLabel(channelIndex, sliceIndex, frameIndex, positionIndex);
      TaggedImage img = cached_.get(label);
      if (img != null) {
         return img.tags;
      }
      if (!tiffReadersByLabel_.containsKey(label)) {
         return null;
      }
      return tiffReadersByLabel_.get(label).readImage(label).tags;   
   }

   @Override
   public void putImage(TaggedImage taggedImage) throws MMException {
      if (!newDataSet_) {
         throw new MMException("This ImageFileManager is read-only.");
      }
      int positionIndex = 0;
      try {
         positionIndex = MDUtils.getPositionIndex(taggedImage.tags);
      } catch (JSONException ex) {
         ReportingUtils.logError(ex);
      }
      String label = MDUtils.getLabel(taggedImage.tags);
      if (positions_ == null) {
         try {
            positions_ = new HashMap<Integer,Position>();       
            JavaUtils.createDirectory(directory_);
         } catch (Exception ex) {
            ReportingUtils.logError(ex);
         }
      }
      
      if (omeTiff_) {
         if (omeMD_ == null) {
            omeMD_ = MetadataTools.createOMEXMLMetadata();
         }
      }
      
      if (positions_.get(positionIndex) == null) {
         //Create new position, which handles all reading and writing for a given position
         positions_.put(positionIndex, new Position(positionIndex, taggedImage.tags, this));
      }
      Position pos = positions_.get(positionIndex);
      try {
         pos.writeImage(taggedImage);
      } catch (IOException ex) {
        ReportingUtils.showError("problem writing image to file");
      }
      tiffReadersByLabel_.put(label, pos.getCurrentReader());
         
      int frame;
      try {
         frame = MDUtils.getFrameIndex(taggedImage.tags);
      } catch (JSONException ex) {
         frame = 0;
      }
      lastFrameOpenedDataSet_ = Math.max(frame, lastFrameOpenedDataSet_);
      cached_.add(taggedImage, label);
   }

   @Override
   public Set<String> imageKeys() {
      return tiffReadersByLabel_.keySet();
   }

   /**
    * Call this function when no more images are expected
    * Finishes writing the metadata file and closes it.
    * After calling this function, the imagestorage is read-only
    */
   @Override
   public synchronized void finished() {
      if (finished_) {
         return;
      }
      newDataSet_ = false;
      try {
         if (positions_ != null) {
            for (Position p : positions_.values()) {
               if (expectedImageOrder_) {
                  p.finishAbortedAcqIfNeeded();
               }
            }
            for (Position p : positions_.values()) {
               p.finished();
            }
         }                 
      } catch (IOException ex) {
         ReportingUtils.logError(ex);
      }
      finished_ = true;
   }

   @Override
   public boolean isFinished() {
      return !newDataSet_;
   }

   @Override
   public void setSummaryMetadata(JSONObject md) {
      summaryMetadata_ = md;
      if (summaryMetadata_ != null) {
         try {
            boolean slicesFirst = summaryMetadata_.getBoolean("SlicesFirst");
            boolean timeFirst = summaryMetadata_.getBoolean("TimeFirst");
            TreeMap<String, MultipageTiffReader> oldImageMap = tiffReadersByLabel_;
            tiffReadersByLabel_ = new TreeMap<String, MultipageTiffReader>(new ImageLabelComparator(slicesFirst, timeFirst));
            tiffReadersByLabel_.putAll(oldImageMap);
         } catch (JSONException ex) {
            ReportingUtils.logError("Couldn't find SlicesFirst or TimeFirst in summary metadata");
         }
         if (summaryMetadata_ != null && summaryMetadata_.length() > 0) {
            processSummaryMD();
         }
      }
   }

   @Override
   public JSONObject getSummaryMetadata() {
      return summaryMetadata_;
   }
   
   @Override
   public JSONObject getDisplayAndComments() {
      return displayAndComments_;
   }

   @Override
   public void setDisplayAndComments(JSONObject settings) {
      displayAndComments_ = settings;
   }
          
   @Override   
   public void writeDisplaySettings() {
      for (MultipageTiffReader r : new HashSet<MultipageTiffReader>(tiffReadersByLabel_.values())) {
         try {
            r.rewriteDisplaySettings(displayAndComments_.getJSONArray("Channels"));
            r.rewriteComments(displayAndComments_.getJSONObject("Comments"));
         } catch (JSONException ex) {
            ReportingUtils.logError("Error writing display settings");
         } catch (IOException ex) {
            ReportingUtils.logError(ex);
         }
      }
   }
   
   /**
    * Disposes of the tagged images in the imagestorage
    */
   @Override
   public void close() {
      shutdownHook_.run();
      Runtime.getRuntime().removeShutdownHook(shutdownHook_);
      for (MultipageTiffReader r : new HashSet<MultipageTiffReader>(tiffReadersByLabel_.values())) {
         try {
            r.close();
         } catch (IOException ex) {
            ReportingUtils.logError(ex);
         }
      }              
   }

   @Override
   public String getDiskLocation() {
      return directory_;
   }

   @Override
   public int lastAcquiredFrame() {
      if (newDataSet_) {
         int lastFrame = 0;
         for (Position p : positions_.values()) {
            lastFrame = Math.max(lastFrame, p.getLastFrame());
         }
         return lastFrame;
      } else {
         return lastFrameOpenedDataSet_;
      }
   }

   public long getDataSetSize() {
      File dir = new File (directory_);
      LinkedList<File> list = new LinkedList<File>();
      for (File f : dir.listFiles()) {
         if (f.isDirectory()) {
            for (File fi : f.listFiles()) {
               list.add(f);
            }
         } else {
            list.add(f);
         }
      }
      long size = 0;
      for (File f : list) {
         size += f.length();
      }
      return size;
   }

   //Class encapsulating a single XY stage position and everything associated with it
   private class Position {
      private LinkedList<MultipageTiffWriter> tiffWriters_;
      private FileWriter mdWriter_;
      private int index_;
      private String baseFilename_;
      private String currentTiffFilename_;
      private String metadataFileFullPath_;
      private boolean finished_ = false;
      private int ifdCount_ = 0;
      private int planeIndex_ = 0;
      private int tiffDataIndex_ = -1;
      private int tiffDataPlaneCount_ = 0;
      private int nextExpectedChannel_ = 0, nextExpectedSlice_ = 0, nextExpectedFrame_ = 0;
      private String positionName_;
      private TaggedImageStorageMultipageTiff mpTiff_;
      private int lastFrame_ = -1;
      
      public Position(int index, JSONObject firstImageTags, TaggedImageStorageMultipageTiff mpt) {
         index_ = index;
         tiffWriters_ = new LinkedList<MultipageTiffWriter>();  
         mpTiff_ = mpt;
         try {
            positionName_ = MDUtils.getPositionName(firstImageTags);
         } catch (JSONException ex) {
            ReportingUtils.logError("Couldn't find position name in image metadata");
            positionName_ = "pos" + index_;
         }
         
         //get file path and name
         baseFilename_ = createBaseFilename();
         currentTiffFilename_ = baseFilename_ + (omeTiff_ ? ".ome.tif" : ".tif");
         //make first writer
         tiffWriters_.add(new MultipageTiffWriter(directory_, currentTiffFilename_, summaryMetadata_, mpt));
   
         if (omeTiff_) {
            try {
               startOMEXML();
            } catch (Exception ex) {
               ReportingUtils.logError("Problem writing OME XML");
            }
         }
         try {
            if (seperateMetadataFile_) {
               startMetadataFile();
            }
         } catch (JSONException ex) {
            ReportingUtils.showError("Problem with summary metadata");
         }
      }

      public void finished() throws IOException {
         if (finished_) {
            return;
         }
         
         try {
            if (seperateMetadataFile_) {
               finishMetadataFile();
            }
         } catch (JSONException ex) {
            ReportingUtils.logError("Problem finishing metadata.txt");
         }
         for (MultipageTiffWriter w : tiffWriters_) {
            String omeXML = "";
            try {
               OMEXMLService service = new ServiceFactory().getInstance(OMEXMLService.class);
               omeXML = service.getOMEXML(omeMD_);
            } catch (Exception ex) {
               ReportingUtils.logError(ex);
            }
            omeXML += " ";
            w.close(omeXML);
         }
         finished_ = true;
      }

      public MultipageTiffReader getCurrentReader() {
         return tiffWriters_.getLast().getReader();
      }
      
      public void writeImage(TaggedImage img) throws IOException {
         //check if current writer is out of space, if so, make a new one
         if (!tiffWriters_.getLast().hasSpaceToWrite(img, omeTiff_ ? estimateOMEMDSize(): 0  )) {
            currentTiffFilename_ = baseFilename_ + "_" + tiffWriters_.size() + (omeTiff_ ? ".ome.tif" : ".tif");
            ifdCount_ = 0;
            tiffWriters_.add(new MultipageTiffWriter(directory_ ,currentTiffFilename_, summaryMetadata_, mpTiff_));
         }      

         //Add filename to image tags
         try {
            img.tags.put("FileName", currentTiffFilename_);
         } catch (JSONException ex) {
            ReportingUtils.logError("Error adding filename to metadata");
         }

         //write image
         tiffWriters_.getLast().writeImage(img);  

         //write metadata
         if (omeTiff_) {
            addToOMEMetadata(img.tags);  
         }
         
         checkForExpectedImageOrder(img.tags);        
         
         try {
            int frame = MDUtils.getFrameIndex(img.tags);
            lastFrame_ = Math.max(frame, lastFrame_);
         } catch (JSONException ex) {
            ReportingUtils.showError("Couldn't find frame index in image tags");
         }       
         
         try {
            if (seperateMetadataFile_) {
               writeToMetadataFile(img.tags);
            }
         } catch (JSONException ex) {
            ReportingUtils.logError("Problem with image metadata");
         }
      }
   
      private void checkForExpectedImageOrder(JSONObject tags) {
         try {
            //Determine next expected indices
            

            int channel = MDUtils.getChannelIndex(tags), frame = MDUtils.getFrameIndex(tags),
                    slice = MDUtils.getSliceIndex(tags);
            if (slice != nextExpectedSlice_ || channel != nextExpectedChannel_ ||
                    frame != nextExpectedFrame_) {
               expectedImageOrder_ = false;
            }
            //Figure out next expected indices
            if (slicesFirst()) {
               nextExpectedSlice_ = slice + 1;
               if (nextExpectedSlice_ == numSlices_) {
                  nextExpectedSlice_ = 0;
                  nextExpectedChannel_ = channel + 1;
                  if (nextExpectedChannel_ == numChannels_) {
                     nextExpectedChannel_ = 0;
                     nextExpectedFrame_ = frame + 1;
                  }
               }
            } else {
               nextExpectedChannel_ = channel + 1;
               if (nextExpectedChannel_ == numChannels_) {
                  nextExpectedChannel_ = 0;
                  nextExpectedSlice_ = slice + 1;
                  if (nextExpectedSlice_ == numSlices_) {
                     nextExpectedSlice_ = 0;
                     nextExpectedFrame_ = frame + 1;
                  }
               }
            }
         } catch (JSONException ex) {
            ReportingUtils.logError("Couldnt find channel, slice, or frame index in Image tags");
            expectedImageOrder_ = false;
         }
      }

      private int estimateOMEMDSize() {
         return totalNumImagePlanes_ * omeXMLImageLength_ + numPositions_ * omeXMLBaseLength_;
      }

      public int getLastFrame() {
         return lastFrame_;
      }

      private void writeToMetadataFile(JSONObject md) throws JSONException {
         try {
            mdWriter_.write(",\r\n\"FrameKey-" + MDUtils.getFrameIndex(md)
                    + "-" + MDUtils.getChannelIndex(md) + "-" + MDUtils.getSliceIndex(md) + "\": ");
            mdWriter_.write(md.toString(2));
         } catch (IOException ex) {
            ReportingUtils.logError("Problem writing to metadata.txt file");
         }
      }

      private void startMetadataFile() throws JSONException {
            metadataFileFullPath_ = directory_ + "/" + baseFilename_ + "_metadata.txt";
            try {
               mdWriter_ = new FileWriter(metadataFileFullPath_);
               mdWriter_.write("{" + "\r\n");
               mdWriter_.write("\"Summary\": ");
               mdWriter_.write(summaryMetadata_.toString(2));
            } catch (IOException ex) {
               ReportingUtils.logError("Problem creating metadata.txt file");
            }
      }

      private void finishMetadataFile() throws JSONException {
         try {
            mdWriter_.write("\r\n}\r\n");
            mdWriter_.close();
         } catch (IOException ex) {
            ReportingUtils.logError("Problem creating metadata.txt file");
         }
      }

      private String createBaseFilename() {
         String baseFilename = "";
         try {
            String prefix = summaryMetadata_.getString("Prefix");
            if (prefix.length() == 0) {
               baseFilename = "MMImages";
            } else {
               baseFilename = prefix + "_MMImages";
            }
         } catch (JSONException ex) {
            ReportingUtils.logError("Can't find Prefix in summary metadata");
            baseFilename = "MMImages";
         }

         if (numPositions_ > 1) {
            baseFilename += "_" + positionName_;
         }
         return baseFilename;
      }

      /**
       * Generate all expected labels for the last frame, and write dummy images for ones 
       * that weren't written. Modify ImageJ and OME max number of frames as appropriate
       */
      private void finishAbortedAcqIfNeeded() {
         try {
            if (timeFirst()) {
               //some positions may have complete time lapse, the one on which this
               //method is called is incomplete. There may be other positions that were never 
               //even run
               completeFrameWithBlankImages(lastFrame_);
            } else {
               //One position may be on the next frame compared to others. Complete each position
               //with blank images to fill this frame
               completeFrameWithBlankImages(lastAcquiredFrame());
            }
         } catch (JSONException e) {
            ReportingUtils.logError("Problem finishing aborted acq with blank images");
         }
      }

      private void completeFrameWithBlankImages(int frame) throws JSONException {
         int numFrames = MDUtils.getNumFrames(summaryMetadata_);
         int numSlices = MDUtils.getNumSlices(summaryMetadata_);
         int numChannels = MDUtils.getNumChannels(summaryMetadata_);
         if (numFrames > frame + 1 && lastFrame_ != -1) {
            if (omeTiff_) {
               omeMD_.setPixelsSizeT(new PositiveInteger(frame + 1), index_);
            }
            TreeSet<String> writtenImages = new TreeSet<String>();
            for (MultipageTiffWriter w : tiffWriters_) {
               writtenImages.addAll(w.getIndexMap().keySet());
               w.setAbortedNumFrames(frame + 1);
            }
            TreeSet<String> lastFrameLabels = new TreeSet<String>();
            for (int c = 0; c < numChannels; c++) {
               for (int z = 0; z < numSlices; z++) {
                  lastFrameLabels.add(MDUtils.generateLabel(c, z, frame, index_));
               }
            }
            lastFrameLabels.removeAll(writtenImages);
            try {
               for (String label : lastFrameLabels) {
                  tiffWriters_.getLast().writeBlankImage(label);
                  if (omeTiff_) {
                     JSONObject dummyTags = new JSONObject();
                     int channel = Integer.parseInt(label.split("_")[0]);
                     int slice = Integer.parseInt(label.split("_")[1]);
                     MDUtils.setChannelIndex(dummyTags, channel);
                     MDUtils.setFrameIndex(dummyTags, frame);
                     MDUtils.setSliceIndex(dummyTags, slice);
                     addToOMEMetadata(dummyTags);
                  }
               }
            } catch (IOException ex) {
               ReportingUtils.logError("problem writing dummy image");
            }
         }
      }

      private void startOMEXML() throws JSONException, MMScriptException {
         //Last one is samples per pixel
         MetadataTools.populateMetadata(omeMD_, index_, baseFilename_, MultipageTiffWriter.BYTE_ORDER.equals(ByteOrder.LITTLE_ENDIAN),
                 slicesFirst() ? "XYZCT" : "XYCZT", "uint" + (MDUtils.isGRAY8(summaryMetadata_) ? "8" : "16"),
                 MDUtils.getWidth(summaryMetadata_), MDUtils.getHeight(summaryMetadata_),
                 MDUtils.getNumSlices(summaryMetadata_), MDUtils.getNumChannels(summaryMetadata_),
                 MDUtils.getNumFrames(summaryMetadata_), 1);

         //Display settings
         JSONArray channels = displayAndComments_.getJSONArray("Channels");
         for (int c = 0; c < channels.length(); c++) {
            JSONObject channel = channels.getJSONObject(c);
            omeMD_.setChannelColor(new Color(channel.getInt("Color")), index_, c);
            omeMD_.setChannelName(channel.getString("Name"), index_, c);
         }

         if (summaryMetadata_.has("PixelSize_um") && !summaryMetadata_.isNull("PixelSize_um")) {
            double pixelSize = summaryMetadata_.getDouble("PixelSize_um");
            if (pixelSize > 0) {
               omeMD_.setPixelsPhysicalSizeX(new PositiveFloat(pixelSize), index_);
               omeMD_.setPixelsPhysicalSizeY(new PositiveFloat(pixelSize), index_);
            }
         }
         if (summaryMetadata_.has("z-step_um") && !summaryMetadata_.isNull("z-step_um")) {
            double zStep = summaryMetadata_.getDouble("z-step_um");
            if (zStep > 0) {
               omeMD_.setPixelsPhysicalSizeZ(new PositiveFloat(zStep), index_);
            }
         }

         omeMD_.setStageLabelName(positionName_, index_);
            
         
         String instrumentID = MetadataTools.createLSID("Microscope");
         omeMD_.setInstrumentID(instrumentID, 0);
         // link Instrument and Image
         omeMD_.setImageInstrumentRef(instrumentID, index_);


//        String date = DateTools.formatDate(p.time, DATE_FORMAT);
//        omeMD_.setImageAcquisitionDate(new Timestamp(date), i);
//
//        omeMD_.setImageDescription(p.comment, i);
//
//        int nextStamp = 0;
//        for (int q=0; q<getImageCount(); q++) {
//          omeMD_.setPlaneExposureTime(p.exposureTime, i, q);
//          String tiff = positions.get(getSeries()).getFile(q);
//          if (tiff != null && new Location(tiff).exists() &&
//            nextStamp < p.timestamps.length)
//          {
//            omeMD_.setPlaneDeltaT(p.timestamps[nextStamp++], i, q);
//          }
//        }
//
//        String serialNumber = p.detectorID;
//        p.detectorID = MetadataTools.createLSID("Detector", 0, i);
//
//        for (int c=0; c<p.channels.length; c++) {
//          omeMD_.setDetectorSettingsBinning(getBinning(p.binning), i, c);
//          omeMD_.setDetectorSettingsGain(new Double(p.gain), i, c);
//          if (c < p.voltage.size()) {
//            omeMD_.setDetectorSettingsVoltage(p.voltage.get(c), i, c);
//          }
//          omeMD_.setDetectorSettingsID(p.detectorID, i, c);
//        }
//
//        omeMD_.setDetectorID(p.detectorID, 0, i);
//        if (p.detectorModel != null) {
//          omeMD_.setDetectorModel(p.detectorModel, 0, i);
//        }
//
//        if (serialNumber != null) {
//          omeMD_.setDetectorSerialNumber(serialNumber, 0, i);
//        }
//
//        if (p.detectorManufacturer != null) {
//          omeMD_.setDetectorManufacturer(p.detectorManufacturer, 0, i);
//        }

//        if (p.cameraMode == null) p.cameraMode = "Other";
//        omeMD_.setDetectorType(getDetectorType(p.cameraMode), 0, i);
//        omeMD_.setImagingEnvironmentTemperature(p.temperature, i);

      }
      
      private void addToOMEMetadata(JSONObject tags) {
         //Required tags: Channel, slice, and frame index
         try {
            int slice = MDUtils.getSliceIndex(tags);
            int frame = MDUtils.getFrameIndex(tags);
            int channel = MDUtils.getChannelIndex(tags);
                 
            //New tiff data if unexpected index, or new file
            boolean newTiffData = slice != nextExpectedSlice_ || channel != nextExpectedChannel_ ||
                    frame != nextExpectedFrame_ || ifdCount_ == 0; //ifdCount is 0 when a new file started
            if (newTiffData) {   //create new tiff data element
               tiffDataIndex_++;
               omeMD_.setTiffDataFirstZ(new NonNegativeInteger(slice), index_, tiffDataIndex_);
               omeMD_.setTiffDataFirstC(new NonNegativeInteger(channel), index_, tiffDataIndex_);
               omeMD_.setTiffDataFirstT(new NonNegativeInteger(frame), index_, tiffDataIndex_);
               omeMD_.setTiffDataIFD(new NonNegativeInteger(ifdCount_), index_, tiffDataIndex_);
               omeMD_.setUUIDFileName(currentTiffFilename_, index_, tiffDataIndex_);
               tiffDataPlaneCount_ = 1;
            } else {   //continue adding to previous tiffdata element
               tiffDataPlaneCount_++;
            }
            omeMD_.setTiffDataPlaneCount(new NonNegativeInteger(tiffDataPlaneCount_), index_, tiffDataIndex_);
            

            omeMD_.setPlaneTheZ(new NonNegativeInteger(slice), index_, planeIndex_);
            omeMD_.setPlaneTheC(new NonNegativeInteger(channel), index_, planeIndex_);
            omeMD_.setPlaneTheT(new NonNegativeInteger(frame), index_, planeIndex_);
         } catch (JSONException ex) {
            ReportingUtils.showError("Image Metadata missing ChannelIndex, SliceIndex, or FrameIndex");
         }

         //Optional tags
         try {
            
            if (tags.has("Exposure-ms") && !tags.isNull("Exposure-ms")) {
               omeMD_.setPlaneExposureTime(tags.getDouble("Exposure-ms") / 1000.0, index_, planeIndex_);
            }
            if (tags.has("XPositionUm") && !tags.isNull("XPositionUm")) {
               omeMD_.setPlanePositionX(tags.getDouble("XPositionUm"), index_, planeIndex_);
               if (planeIndex_ == 0) { //should be set at start, but dont have position coordinates then
                  omeMD_.setStageLabelX(tags.getDouble("XPositionUm"), index_);
               }
            }
            if (tags.has("YPositionUm") && !tags.isNull("YPositionUm")) {
               omeMD_.setPlanePositionY(tags.getDouble("YPositionUm"), index_, planeIndex_);
               if (planeIndex_ == 0) {
                  omeMD_.setStageLabelY(tags.getDouble("YPositionUm"), index_);
               }
            }
            if (tags.has("ZPositionUm") && !tags.isNull("ZPositionUm")) {
               omeMD_.setPlanePositionZ(tags.getDouble("ZPositionUm"), index_, planeIndex_);
            }
            //TODO add in delta T????
            

            if (planeIndex_ == 0) {
               if (tags.has("Core-Camera") && !tags.isNull("Core-Camera")) {
                  String camera = tags.getString("Core-Camera");
                  if (tags.has(camera + "-Physical Camera 1" )) {       //Multicam mode
                     for (int i = 0; i < 3; i++) {
                        
                     }
                                     
                  } else { //Single camera mode
                     String id = MetadataTools.createLSID(camera);
                     //Instrument index, detector index
                     omeMD_.setDetectorID(camera, 0, 0);
                  }

               }
            }


            
            
            
         } catch (JSONException e) {
            ReportingUtils.logError("Problem adding tags to OME Metadata");
         }

         ifdCount_++;
         planeIndex_++;
         
         
         //This code is used is estimating the length of OME XML to be added in, so
         //images arent written into file space reserved for it
         if (omeXMLBaseLength_ == -1) {
            try {
               OMEXMLService service = new ServiceFactory().getInstance(OMEXMLService.class);
               omeXMLBaseLength_ = service.getOMEXML(omeMD_).length();
            } catch (Exception ex) {
               ReportingUtils.logError("Unable to calculate OME XML Base length");
            }
         } else if (omeXMLImageLength_ == -1) {
            //This is the first image plane to be written, so calculate the change in length from the base OME
            //XML length to estimate the approximate memory needed per an image plane
            try {
               OMEXMLService service = new ServiceFactory().getInstance(OMEXMLService.class);
               omeXMLImageLength_ = (int) (1.1*(service.getOMEXML(omeMD_).length() - omeXMLBaseLength_));
            } catch (Exception ex) {
               ReportingUtils.logError("Unable to calculate OME XML Image length");
            }
         }
      }
   }
   
   
   private class CachedImages {
      private static final int NUM_TO_CACHE = 10;
      
      private LinkedList<TaggedImage> images;
      private LinkedList<String> labels;
      
      public CachedImages() {
         images = new LinkedList<TaggedImage>();
         labels = new LinkedList<String>();
      }
      
      public void add(TaggedImage img, String label) {
         images.addFirst(img);
         labels.addFirst(label);
         while (images.size() > NUM_TO_CACHE) {
            images.removeLast();
            labels.removeLast();
         }
      }

      public TaggedImage get(String label) {
         int i = labels.indexOf(label);
         return i == -1 ? null : images.get(i);
      }
      
   }
    
}