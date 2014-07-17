package mmcorej;

import java.util.UUID;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * This class holds the metadata for ImagePlanes. It is intended to be 
 * immutable; construct new Metadatas using a MetadataBuilder, or by using
 * the copy() method (which generates a MetadataBuilder). Any fields that are
 * not explicitly set will default to null.
 */
public class Metadata {

   /**
    * This class constructs Metadata objects. Use the build() method to 
    * generate a Metadata.
    */
   public static class MetadataBuilder {
      private UUID uuid_builder_ = null;
      private String source_builder_ = null;
      private String microManagerVersion_builder_ = null;
      private String metadataVersion_builder_ = null;
      private String acquisitionName_builder_ = null;
      private String fileName_builder_ = null;
      private String userName_builder_ = null;
      private String computerName_builder_ = null;
      private String prefix_builder_ = null;
      private String directory_builder_ = null;
      private String startTime_builder_ = null;
      
      private JSONArray channelNames_builder_ = null;
      private JSONArray channelColors_builder_ = null;
      private JSONArray channelContrastMins_builder_ = null;
      private JSONArray channelContrastMaxes_builder_ = null;
      private JSONArray initialPositionList_builder_ = null;

      private int numChannels_builder_ = null;
      private int numSlices_builder_ = null;
      private int numFrames_builder_ = null;
      private int numPositions_builder_ = null;
      private double zStepUm_builder_ = null;
      private double waitIntervalMs_builder_ = null;
      private JSONArray customIntervalsMs_builder_ = null;
      private boolean timeFirst_builder_ = null;
      private boolean slicesFirst_builder_ = null;
      private boolean keepShutterOpenSlices_builder_ = null;
      private boolean keepShutterOpenChannels_builder_ = null;

      private String pixelType_builder_ = null;
      private int bitDepth_builder_ = null;
      private int bytesPerPixel_builder_ = null;
      private int numComponents_builder_ = null;
      private int ijType_builder_ = null;
      private int frame_builder_ = null;
      private String channelName_builder_ = null;
      private double exposureMs_builder_ = null;
      private double elapsedTimeMs_builder_ = null;
      private double startTimeMs_builder_ = null;
      private int binning_builder_ = null;
      
      private int channelIndex_builder_ = null;
      private int frameIndex_builder_ = null;
      private int positionIndex_builder_ = null;
      private int sliceIndex_builder_ = null;
      private int imageNumber_builder_ = null;
      private int gridRow_builder_ = null;
      private int gridColumn_builder_ = null;
      private String positionName_builder_ = null;
      private double xPositionUm_builder_ = null;
      private double yPositionUm_builder_ = null;
      private double zPositionUm_builder_ = null;

      private double pixelSizeUm_builder_ = null;
      private String camera_builder_ = null;
      private String receivedTime_builder_ = null;
      private String excitationLabel_builder_ = null;
      private String emissionLabel_builder_ = null;
      private String ROI_builder_ = null;
      private String comments_builder_ = null;

      private int pixelWidth_builder_ = null;
      private int pixelHeight_builder_ = null;
      private int color_builder_ = null;
      private double pixelAspect_builder_ = null;

      public Metadata build() {
         return new Metadata(this);
      }

      public MetadataBuilder uuid(UUID uuid) {
         uuid_builder_ = uuid;
         return this;
      }

      public MetadataBuilder source(String source) {
         source_builder_ = source;
         return this;
      }

      public MetadataBuilder microManagerVersion(String microManagerVersion) {
         microManagerVersion_builder_ = microManagerVersion;
         return this;
      }

      public MetadataBuilder metadataVersion(String metadataVersion) {
         metadataVersion_builder_ = metadataVersion;
         return this;
      }

      public MetadataBuilder acquisitionName(String acquisitionName) {
         acquisitionName_builder_ = acquisitionName;
         return this;
      }

      public MetadataBuilder fileName(String fileName) {
         fileName_builder_ = fileName;
         return this;
      }

      public MetadataBuilder userName(String userName) {
         userName_builder_ = userName;
         return this;
      }

      public MetadataBuilder computerName(String computerName) {
         computerName_builder_ = computerName;
         return this;
      }

      public MetadataBuilder prefix(String prefix) {
         prefix_builder_ = prefix;
         return this;
      }

      public MetadataBuilder directory(String directory) {
         directory_builder_ = directory;
         return this;
      }

      public MetadataBuilder startTime(String startTime) {
         startTime_builder_ = startTime;
         return this;
      }

      public MetadataBuilder channelNames(JSONArray channelNames) {
         channelNames_builder_ = channelNames;
         return this;
      }

      public MetadataBuilder channelColors(JSONArray channelColors) {
         channelColors_builder_ = channelColors;
         return this;
      }

      public MetadataBuilder channelContrastMins(JSONArray channelContrastMins) {
         channelContrastMins_builder_ = channelContrastMins;
         return this;
      }

      public MetadataBuilder channelContrastMaxes(JSONArray channelContrastMaxes) {
         channelContrastMaxes_builder_ = channelContrastMaxes;
         return this;
      }

      public MetadataBuilder initialPositionList(JSONArray initialPositionList) {
         initialPositionList_builder_ = initialPositionList;
         return this;
      }

      public MetadataBuilder numChannels(int numChannels) {
         numChannels_builder_ = numChannels;
         return this;
      }

      public MetadataBuilder numSlices(int numSlices) {
         numSlices_builder_ = numSlices;
         return this;
      }

      public MetadataBuilder numFrames(int numFrames) {
         numFrames_builder_ = numFrames;
         return this;
      }

      public MetadataBuilder numPositions(int numPositions) {
         numPositions_builder_ = numPositions;
         return this;
      }

      public MetadataBuilder zStepUm(double zStepUm) {
         zStepUm_builder_ = zStepUm;
         return this;
      }

      public MetadataBuilder waitIntervalMs(double waitIntervalMs) {
         waitIntervalMs_builder_ = waitIntervalMs;
         return this;
      }

      public MetadataBuilder customIntervalsMs(JSONArray customIntervalsMs) {
         customIntervalsMs_builder_ = customIntervalsMs;
         return this;
      }

      public MetadataBuilder timeFirst(boolean timeFirst) {
         timeFirst_builder_ = timeFirst;
         return this;
      }

      public MetadataBuilder slicesFirst(boolean slicesFirst) {
         slicesFirst_builder_ = slicesFirst;
         return this;
      }

      public MetadataBuilder keepShutterOpenSlices(boolean keepShutterOpenSlices) {
         keepShutterOpenSlices_builder_ = keepShutterOpenSlices;
         return this;
      }

      public MetadataBuilder keepShutterOpenChannels(boolean keepShutterOpenChannels) {
         keepShutterOpenChannels_builder_ = keepShutterOpenChannels;
         return this;
      }

      public MetadataBuilder pixelType(String pixelType) {
         pixelType_builder_ = pixelType;
         return this;
      }

      public MetadataBuilder bitDepth(int bitDepth) {
         bitDepth_builder_ = bitDepth;
         return this;
      }

      public MetadataBuilder bytesPerPixel(int bytesPerPixel) {
         bytesPerPixel_builder_ = bytesPerPixel;
         return this;
      }

      public MetadataBuilder numComponents(int numComponents) {
         numComponents_builder_ = numComponents;
         return this;
      }

      public MetadataBuilder ijType(int ijType) {
         ijType_builder_ = ijType;
         return this;
      }

      public MetadataBuilder frame(int frame) {
         frame_builder_ = frame;
         return this;
      }

      public MetadataBuilder channelName(String channelName) {
         channelName_builder_ = channelName;
         return this;
      }

      public MetadataBuilder exposureMs(double exposureMs) {
         exposureMs_builder_ = exposureMs;
         return this;
      }

      public MetadataBuilder elapsedTimeMs(double elapsedTimeMs) {
         elapsedTimeMs_builder_ = elapsedTimeMs;
         return this;
      }

      public MetadataBuilder startTimeMs(double startTimeMs) {
         startTimeMs_builder_ = startTimeMs;
         return this;
      }

      public MetadataBuilder binning(int binning) {
         binning_builder_ = binning;
         return this;
      }

      public MetadataBuilder channelIndex(int channelIndex) {
         channelIndex_builder_ = channelIndex;
         return this;
      }

      public MetadataBuilder frameIndex(int frameIndex) {
         frameIndex_builder_ = frameIndex;
         return this;
      }

      public MetadataBuilder positionIndex(int positionIndex) {
         positionIndex_builder_ = positionIndex;
         return this;
      }

      public MetadataBuilder sliceIndex(int sliceIndex) {
         sliceIndex_builder_ = sliceIndex;
         return this;
      }

      public MetadataBuilder imageNumber(int imageNumber) {
         imageNumber_builder_ = imageNumber;
         return this;
      }

      public MetadataBuilder gridRow(int gridRow) {
         gridRow_builder_ = gridRow;
         return this;
      }

      public MetadataBuilder gridColumn(int gridColumn) {
         gridColumn_builder_ = gridColumn;
         return this;
      }

      public MetadataBuilder positionName(String positionName) {
         positionName_builder_ = positionName;
         return this;
      }

      public MetadataBuilder xPositionUm(double xPositionUm) {
         xPositionUm_builder_ = xPositionUm;
         return this;
      }

      public MetadataBuilder yPositionUm(double yPositionUm) {
         yPositionUm_builder_ = yPositionUm;
         return this;
      }

      public MetadataBuilder zPositionUm(double zPositionUm) {
         zPositionUm_builder_ = zPositionUm;
         return this;
      }

      public MetadataBuilder pixelSizeUm(double pixelSizeUm) {
         pixelSizeUm_builder_ = pixelSizeUm;
         return this;
      }

      public MetadataBuilder camera(String camera) {
         camera_builder_ = camera;
         return this;
      }

      public MetadataBuilder receivedTime(String receivedTime) {
         receivedTime_builder_ = receivedTime;
         return this;
      }

      public MetadataBuilder excitationLabel(String excitationLabel) {
         excitationLabel_builder_ = excitationLabel;
         return this;
      }

      public MetadataBuilder emissionLabel(String emissionLabel) {
         emissionLabel_builder_ = emissionLabel;
         return this;
      }

      public MetadataBuilder ROI(String ROI) {
         ROI_builder_ = ROI;
         return this;
      }

      public MetadataBuilder comments(String comments) {
         comments_builder_ = comments;
         return this;
      }

      public MetadataBuilder pixelWidth(int pixelWidth) {
         pixelWidth_builder_ = pixelWidth;
         return this;
      }

      public MetadataBuilder pixelHeight(int pixelHeight) {
         pixelHeight_builder_ = pixelHeight;
         return this;
      }

      public MetadataBuilder color(int color) {
         color_builder_ = color;
         return this;
      }

      public MetadataBuilder pixelAspect(double pixelAspect) {
         pixelAspect_builder_ = pixelAspect;
         return this;
      }
   }

   private UUID uuid_;
   private String source_;
   private String microManagerVersion_;
   private String metadataVersion_;
   private String acquisitionName_;
   private String fileName_;
   private String userName_;
   private String computerName_;
   private String prefix_;
   private String directory_;
   private String startTime_;
   
   private JSONArray channelNames_;
   private JSONArray channelColors_;
   private JSONArray channelContrastMins_;
   private JSONArray channelContrastMaxes_;
   private JSONArray initialPositionList_;

   private int numChannels_;
   private int numSlices_;
   private int numFrames_;
   private int numPositions_;
   private double zStepUm_;
   private double waitIntervalMs_;
   private JSONArray customIntervalsMs_;
   private boolean timeFirst_;
   private boolean slicesFirst_;
   private boolean keepShutterOpenSlices_;
   private boolean keepShutterOpenChannels_;

   private String pixelType_;
   private int bitDepth_;
   private int bytesPerPixel_;
   private int numComponents_;
   private int ijType_;
   private int frame_;
   private String channelName_;
   private double exposureMs_;
   private double elapsedTimeMs_;
   private double startTimeMs_;
   private int binning_;
   
   private int channelIndex_;
   private int frameIndex_;
   private int positionIndex_;
   private int sliceIndex_;
   private int imageNumber_;
   private int gridRow_;
   private int gridColumn_;
   private String positionName_;
   private double xPositionUm_;
   private double yPositionUm_;
   private double zPositionUm_;

   private double pixelSizeUm_;
   private String camera_;
   private String receivedTime_;
   private String excitationLabel_;
   private String emissionLabel_;
   private String ROI_;
   private String comments_;

   private int pixelWidth_;
   private int pixelHeight_;
   private int color_;
   private double pixelAspect_;

   public Metadata(MetadataBuilder builder) {
      uuid_ = builder.uuid_builder_;
      source_ = builder.source_builder_;
      microManagerVersion_ = builder.microManagerVersion_builder_;
      metadataVersion_ = builder.metadataVersion_builder_;
      acquisitionName_ = builder.acquisitionName_builder_;
      fileName_ = builder.fileName_builder_;
      userName_ = builder.userName_builder_;
      computerName_ = builder.computerName_builder_;
      prefix_ = builder.prefix_builder_;
      directory_ = builder.directory_builder_;
      startTime_ = builder.startTime_builder_;
      
      channelNames_ = builder.channelNames_builder_;
      channelColors_ = builder.channelColors_builder_;
      channelContrastMins_ = builder.channelContrastMins_builder_;
      channelContrastMaxes_ = builder.channelContrastMaxes_builder_;
      initialPositionList_ = builder.initialPositionList_builder_;

      numChannels_ = builder.numChannels_builder_;
      numSlices_ = builder.numSlices_builder_;
      numFrames_ = builder.numFrames_builder_;
      numPositions_ = builder.numPositions_builder_;
      zStepUm_ = builder.zStepUm_builder_;
      waitIntervalMs_ = builder.waitIntervalMs_builder_;
      customIntervalsMs_ = builder.customIntervalsMs_builder_;
      timeFirst_ = builder.timeFirst_builder_;
      slicesFirst_ = builder.slicesFirst_builder_;
      keepShutterOpenSlices_ = builder.keepShutterOpenSlices_builder_;
      keepShutterOpenChannels_ = builder.keepShutterOpenChannels_builder_;

      pixelType_ = builder.pixelType_builder_;
      bitDepth_ = builder.bitDepth_builder_;
      bytesPerPixel_ = builder.bytesPerPixel_builder_;
      numComponents_ = builder.numComponents_builder_;
      ijType_ = builder.ijType_builder_;
      frame_ = builder.frame_builder_;
      channelName_ = builder.channelName_builder_;
      exposureMs_ = builder.exposureMs_builder_;
      elapsedTimeMs_ = builder.elapsedTimeMs_builder_;
      startTimeMs_ = builder.startTimeMs_builder_;
      binning_ = builder.binning_builder_;
      
      channelIndex_ = builder.channelIndex_builder_;
      frameIndex_ = builder.frameIndex_builder_;
      positionIndex_ = builder.positionIndex_builder_;
      sliceIndex_ = builder.sliceIndex_builder_;
      imageNumber_ = builder.imageNumber_builder_;
      gridRow_ = builder.gridRow_builder_;
      gridColumn_ = builder.gridColumn_builder_;
      positionName_ = builder.positionName_builder_;
      xPositionUm_ = builder.xPositionUm_builder_;
      yPositionUm_ = builder.yPositionUm_builder_;
      zPositionUm_ = builder.zPositionUm_builder_;

      pixelSizeUm_ = builder.pixelSizeUm_builder_;
      camera_ = builder.camera_builder_;
      receivedTime_ = builder.receivedTime_builder_;
      excitationLabel_ = builder.excitationLabel_builder_;
      emissionLabel_ = builder.emissionLabel_builder_;
      ROI_ = builder.ROI_builder_;
      comments_ = builder.comments_builder_;

      pixelWidth_ = builder.pixelWidth_builder_;
      pixelHeight_ = builder.pixelHeight_builder_;
      color_ = builder.color_builder_;
      pixelAspect_ = builder.pixelAspect_builder_;
   }
   
   public MetadataBuilder copy() {
      return new MetadataBuilder()
            .uuid(uuid_)
            .source(source_)
            .microManagerVersion(microManagerVersion_)
            .metadataVersion(metadataVersion_)
            .acquisitionName(acquisitionName_)
            .fileName(fileName_)
            .userName(userName_)
            .computerName(computerName_)
            .prefix(prefix_)
            .directory(directory_)
            .startTime(startTime_)
            .channelNames(channelNames_)
            .channelColors(channelColors_)
            .channelContrastMins(channelContrastMins_)
            .channelContrastMaxes(channelContrastMaxes_)
            .initialPositionList(initialPositionList_)
            .numChannels(numChannels_)
            .numSlices(numSlices_)
            .numFrames(numFrames_)
            .numPositions(numPositions_)
            .zStepUm(zStepUm_)
            .waitIntervalMs(waitIntervalMs_)
            .customIntervalsMs(customIntervalsMs_)
            .timeFirst(timeFirst_)
            .slicesFirst(slicesFirst_)
            .keepShutterOpenSlices(keepShutterOpenSlices_)
            .keepShutterOpenChannels(keepShutterOpenChannels_)
            .pixelType(pixelType_)
            .bitDepth(bitDepth_)
            .bytesPerPixel(bytesPerPixel_)
            .numComponents(numComponents_)
            .ijType(ijType_)
            .frame(frame_)
            .channelName(channelName_)
            .exposureMs(exposureMs_)
            .elapsedTimeMs(elapsedTimeMs_)
            .startTimeMs(startTimeMs_)
            .binning(binning_)
            .channelIndex(channelIndex_)
            .frameIndex(frameIndex_)
            .positionIndex(positionIndex_)
            .sliceIndex(sliceIndex_)
            .imageNumber(imageNumber_)
            .gridRow(gridRow_)
            .gridColumn(gridColumn_)
            .positionName(positionName_)
            .xPositionUm(xPositionUm_)
            .yPositionUm(yPositionUm_)
            .zPositionUm(zPositionUm_)
            .pixelSizeUm(pixelSizeUm_)
            .camera(camera_)
            .receivedTime(receivedTime_)
            .excitationLabel(excitationLabel_)
            .emissionLabel(emissionLabel_)
            .ROI(ROI_)
            .comments(comments_)
            .pixelWidth(pixelWidth_)
            .pixelHeight(pixelHeight_)
            .color(color_)
            .pixelAspect(pixelAspect_);
   }

   public UUID getUuid() {
      return uuid_;
   }

   public String getSource() {
      return source_;
   }

   public String getMicroManagerVersion() {
      return microManagerVersion_;
   }

   public String getMetadataVersion() {
      return metadataVersion_;
   }

   public String getAcquisitionName() {
      return acquisitionName_;
   }

   public String getFileName() {
      return fileName_;
   }

   public String getUserName() {
      return userName_;
   }

   public String getComputerName() {
      return computerName_;
   }

   public String getPrefix() {
      return prefix_;
   }

   public String getDirectory() {
      return directory_;
   }

   public String getStartTime() {
      return startTime_;
   }

   public JSONArray getChannelNames() {
      return channelNames_;
   }

   public JSONArray getChannelColors() {
      return channelColors_;
   }

   public JSONArray getChannelContrastMins() {
      return channelContrastMins_;
   }

   public JSONArray getChannelContrastMaxes() {
      return channelContrastMaxes_;
   }

   public JSONArray getInitialPositionList() {
      return initialPositionList_;
   }

   public int getNumChannels() {
      return numChannels_;
   }

   public int getNumSlices() {
      return numSlices_;
   }

   public int getNumFrames() {
      return numFrames_;
   }

   public int getNumPositions() {
      return numPositions_;
   }

   public double getZStepUm() {
      return zStepUm_;
   }

   public double getWaitIntervalMs() {
      return waitIntervalMs_;
   }

   public JSONArray getCustomIntervalsMs() {
      return customIntervalsMs_;
   }

   public boolean getTimeFirst() {
      return timeFirst_;
   }

   public boolean getSlicesFirst() {
      return slicesFirst_;
   }

   public boolean getKeepShutterOpenSlices() {
      return keepShutterOpenSlices_;
   }

   public boolean getKeepShutterOpenChannels() {
      return keepShutterOpenChannels_;
   }

   public String getPixelType() {
      return pixelType_;
   }

   public int getBitDepth() {
      return bitDepth_;
   }

   public int getBytesPerPixel() {
      return bytesPerPixel_;
   }

   public int getNumComponents() {
      return numComponents_;
   }

   public int getIjType() {
      return ijType_;
   }

   public int getFrame() {
      return frame_;
   }

   public String getChannelName() {
      return channelName_;
   }

   public double getExposureMs() {
      return exposureMs_;
   }

   public double getElapsedTimeMs() {
      return elapsedTimeMs_;
   }

   public double getStartTimeMs() {
      return startTimeMs_;
   }

   public int getBinning() {
      return binning_;
   }

   public int getChannelIndex() {
      return channelIndex_;
   }

   public int getFrameIndex() {
      return frameIndex_;
   }

   public int getPositionIndex() {
      return positionIndex_;
   }

   public int getSliceIndex() {
      return sliceIndex_;
   }

   public int getImageNumber() {
      return imageNumber_;
   }

   public int getGridRow() {
      return gridRow_;
   }

   public int getGridColumn() {
      return gridColumn_;
   }

   public String getPositionName() {
      return positionName_;
   }

   public double getXPositionUm() {
      return xPositionUm_;
   }

   public double getYPositionUm() {
      return yPositionUm_;
   }

   public double getZPositionUm() {
      return zPositionUm_;
   }

   public double getPixelSizeUm() {
      return pixelSizeUm_;
   }

   public String getCamera() {
      return camera_;
   }

   public String getReceivedTime() {
      return receivedTime_;
   }

   public String getExcitationLabel() {
      return excitationLabel_;
   }

   public String getEmissionLabel() {
      return emissionLabel_;
   }

   public String getROI() {
      return ROI_;
   }

   public String getComments() {
      return comments_;
   }

   public int getPixelWidth() {
      return pixelWidth_;
   }

   public int getPixelHeight() {
      return pixelHeight_;
   }

   public int getColor() {
      return color_;
   }

   public double getPixelAspect() {
      return pixelAspect_;
   }
}
