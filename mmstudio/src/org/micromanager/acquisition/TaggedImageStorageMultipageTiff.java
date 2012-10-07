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
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.MMStudioMainFrame;
import org.micromanager.api.TaggedImageStorage;
import org.micromanager.utils.*;




public class TaggedImageStorageMultipageTiff implements TaggedImageStorage {

   private JSONObject summaryMetadata_;
   private JSONObject displayAndComments_;
   private boolean newDataSet_;
   private String directory_;
   private Thread shutdownHook_;
   private int lastFrame_ = -1;
   private int numPositions_;
   private CachedImages cached_;
   final public boolean omeTiff_;
   final private boolean seperateMetadataFile_;
   private IMetadata omeMD_;
  
   //used for estimating total length of ome xml
   private int totalNumImagePlanes_ = 0;
   private int omeXMLBaseLength_ = -1;
   private int omeXMLImageLength_ = -1;
      
   //map of position indices to objects associated with each
   private HashMap<Integer, Position> positions_;
   
   //Map of image labels to file 
   private TreeMap<String, MultipageTiffReader> tiffReadersByLabel_;
  
   public TaggedImageStorageMultipageTiff(String dir, Boolean newDataSet, JSONObject summaryMetadata) throws IOException {            
      omeTiff_ = MMStudioMainFrame.getInstance().getOMETiffEnabled();
      seperateMetadataFile_ = MMStudioMainFrame.getInstance().getMetadataFileWithMultipageTiff();

      newDataSet_ = newDataSet;
      directory_ = dir;
      tiffReadersByLabel_ = new TreeMap<String, MultipageTiffReader>(new ImageLabelComparator());
      cached_ = new CachedImages();
      setSummaryMetadata(summaryMetadata);
      

      if (summaryMetadata_ != null) {  
         processSummaryMD();
      }

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
         totalNumImagePlanes_ = MDUtils.getNumChannels(summaryMetadata_) * MDUtils.getNumFrames(summaryMetadata_)
                 * numPositions_ * MDUtils.getNumSlices(summaryMetadata_);
      } catch (Exception ex) {
         ReportingUtils.logError("Error estimating total number of image planes");
         totalNumImagePlanes_ = 1;
      }
   }
   
   boolean slicesFirst() {
      return ((ImageLabelComparator) tiffReadersByLabel_.comparator()).getSlicesFirst();
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
               lastFrame_ = Math.max(frameIndex, lastFrame_);
            }
         }
      }


      try {
         summaryMetadata_ = reader.getSummaryMetadata();
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
      
      pos.writeImage(taggedImage);          
      tiffReadersByLabel_.put(label, pos.getCurrentReader());
         
      int frame;
      try {
         frame = MDUtils.getFrameIndex(taggedImage.tags);
      } catch (JSONException ex) {
         frame = 0;
      }
      cached_.add(taggedImage, label);
      lastFrame_ = Math.max(lastFrame_, frame);
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
   public void finished() {
      newDataSet_ = false;
      try {
         if (positions_ != null) {
            for (Position p : positions_.values()) {
               p.finished();
            }
         }                 
      } catch (IOException ex) {
         ReportingUtils.logError(ex);
      }
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
      processSummaryMD();
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
      return lastFrame_;
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
      private String positionName_;
      private TaggedImageStorageMultipageTiff mpTiff_;
      
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
      
      public void writeImage(TaggedImage img) {
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
         try {
            tiffWriters_.getLast().writeImage(img);
         } catch (IOException ex) {
            ReportingUtils.logError(ex);
         }
         
         //write metadata
         if (omeTiff_) {
            try {
               addToOMEMetadata(img.tags);
            } catch (Exception ex) {
               ReportingUtils.logError("Problem making OME metadata");
            }
         }

         try {
            if (seperateMetadataFile_) {
               writeToMetadataFile(img.tags);
            }
         } catch (JSONException ex) {
            ReportingUtils.logError("Problem with image metadata");
         }
      }
      
      private int estimateOMEMDSize() {
         return totalNumImagePlanes_*omeXMLImageLength_ + numPositions_*omeXMLBaseLength_; 
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

      private void startOMEXML() throws JSONException, MMScriptException {
         //Last one is samples per pixel
         MetadataTools.populateMetadata(omeMD_, index_, baseFilename_, MultipageTiffWriter.BYTE_ORDER.equals(ByteOrder.LITTLE_ENDIAN),
                 "XYZCT", "uint" + (MDUtils.isGRAY8(summaryMetadata_) ? "8" : "16"),
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
                 
         if (summaryMetadata_.has("PixelSize_um")) {
            double pixelSize = summaryMetadata_.getDouble("PixelSize_um");
            omeMD_.setPixelsPhysicalSizeX(new PositiveFloat(pixelSize), index_);
            omeMD_.setPixelsPhysicalSizeY(new PositiveFloat(pixelSize), index_);
         }
         if (summaryMetadata_.has("z-step_um")) {
            double zStep = summaryMetadata_.getDouble("z-step_um");
            omeMD_.setPixelsPhysicalSizeZ(new PositiveFloat(zStep), index_);
         }

//         String instrumentID = MetadataTools.createLSID("Instrument", 0);
//         store.setInstrumentID(instrumentID, 0);

//        String date = DateTools.formatDate(p.time, DATE_FORMAT);
//        store.setImageAcquisitionDate(new Timestamp(date), i);
//
//        store.setImageDescription(p.comment, i);
//
//        // link Instrument and Image
//        store.setImageInstrumentRef(instrumentID, i);
//      
//
//        int nextStamp = 0;
//        for (int q=0; q<getImageCount(); q++) {
//          store.setPlaneExposureTime(p.exposureTime, i, q);
//          String tiff = positions.get(getSeries()).getFile(q);
//          if (tiff != null && new Location(tiff).exists() &&
//            nextStamp < p.timestamps.length)
//          {
//            store.setPlaneDeltaT(p.timestamps[nextStamp++], i, q);
//          }
//        }
//
//        String serialNumber = p.detectorID;
//        p.detectorID = MetadataTools.createLSID("Detector", 0, i);
//
//        for (int c=0; c<p.channels.length; c++) {
//          store.setDetectorSettingsBinning(getBinning(p.binning), i, c);
//          store.setDetectorSettingsGain(new Double(p.gain), i, c);
//          if (c < p.voltage.size()) {
//            store.setDetectorSettingsVoltage(p.voltage.get(c), i, c);
//          }
//          store.setDetectorSettingsID(p.detectorID, i, c);
//        }
//
//        store.setDetectorID(p.detectorID, 0, i);
//        if (p.detectorModel != null) {
//          store.setDetectorModel(p.detectorModel, 0, i);
//        }
//
//        if (serialNumber != null) {
//          store.setDetectorSerialNumber(serialNumber, 0, i);
//        }
//
//        if (p.detectorManufacturer != null) {
//          store.setDetectorManufacturer(p.detectorManufacturer, 0, i);
//        }
//
//        if (p.cameraMode == null) p.cameraMode = "Other";
//        store.setDetectorType(getDetectorType(p.cameraMode), 0, i);
//        store.setImagingEnvironmentTemperature(p.temperature, i);
//      }
//    }   
      }

      private void addToOMEMetadata(JSONObject tags) throws JSONException {
         omeMD_.setTiffDataIFD(new NonNegativeInteger(ifdCount_), index_, planeIndex_);
         omeMD_.setTiffDataFirstZ(new NonNegativeInteger(MDUtils.getSliceIndex(tags)), index_, planeIndex_);
         omeMD_.setTiffDataFirstC(new NonNegativeInteger(MDUtils.getChannelIndex(tags)), index_, planeIndex_);
         omeMD_.setTiffDataFirstT(new NonNegativeInteger(MDUtils.getFrameIndex(tags)), index_, planeIndex_);
         omeMD_.setTiffDataPlaneCount(new NonNegativeInteger(1), index_, planeIndex_);
         omeMD_.setUUIDFileName(currentTiffFilename_, index_, planeIndex_);
         
         omeMD_.setPlaneTheZ(new NonNegativeInteger(MDUtils.getChannelIndex(tags)), index_, planeIndex_);
         omeMD_.setPlaneTheC(new NonNegativeInteger(MDUtils.getSliceIndex(tags)), index_, planeIndex_);
         omeMD_.setPlaneTheT(new NonNegativeInteger(MDUtils.getFrameIndex(tags)), index_, planeIndex_); 
         if (tags.has("Exposure-ms")) {
            omeMD_.setPlaneExposureTime(tags.getDouble("Exposure-ms")/1000.0, index_, planeIndex_);
         }
         if (tags.has("XPositionUm")) {
            omeMD_.setPlanePositionX(tags.getDouble("XPositionUm"), index_, planeIndex_);
         }
         if (tags.has("YPositionUm")) {
            omeMD_.setPlanePositionY(tags.getDouble("YPositionUm"), index_, planeIndex_);
         }
         if (tags.has("ZPositionUm")) {
            omeMD_.setPlanePositionZ(tags.getDouble("ZPositionUm"), index_, planeIndex_);
         }
         //TODO add in delta T????
         
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