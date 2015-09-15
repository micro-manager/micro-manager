///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Data API implementation
//-----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger, 2015
//
// COPYRIGHT:    University of California, San Francisco, 2015
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

package org.micromanager.data.internal;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import org.micromanager.data.Coords;
import org.micromanager.data.SummaryMetadata;
import org.micromanager.MultiStagePosition;
import org.micromanager.StagePosition;

import org.micromanager.internal.MMStudio;

import org.micromanager.internal.utils.DefaultUserProfile;
import org.micromanager.internal.utils.MDUtils;
import org.micromanager.internal.utils.NumberUtils;
import org.micromanager.internal.utils.ReportingUtils;

import org.micromanager.PropertyMap;

public class DefaultSummaryMetadata implements SummaryMetadata {

   /**
    * This is the version string for all metadata as saved in Micro-Manager
    * data files.
    */
   public static final String METADATA_VERSION = "11.0.0";

   /**
    * Retrieve the summary metadata that has been saved in the preferences.
    * NB not all parameters of the SummaryMetadata are preserved in the
    * preferences (on the assumption that certain fields only make sense when
    * in reference to a specific dataset).
    */
   public static DefaultSummaryMetadata getStandardSummaryMetadata() {
      DefaultUserProfile profile = DefaultUserProfile.getInstance();
      Builder builder = new Builder();

      builder.userName(profile.getString(DefaultSummaryMetadata.class,
               "userName", System.getProperty("user.name")));
      builder.profileName(profile.getProfileName());
      builder.microManagerVersion(MMStudio.getInstance().getVersion());
      builder.metadataVersion(METADATA_VERSION);

      try {
         String computerName = InetAddress.getLocalHost().getHostName();
         builder.computerName(profile.getString(DefaultSummaryMetadata.class,
                  "computerName", computerName));
      } catch (UnknownHostException e) {
         ReportingUtils.logError(e, "Couldn't get computer name for standard summary metadata.");
      }

      builder.comments(profile.getString(DefaultSummaryMetadata.class,
               "comments", ""));

      return builder.build();
   }

   /**
    * Save the provided summary metadata as the new defaults. Note that only
    * a few fields are preserved.
    */
   public static void setStandardSummaryMetadata(SummaryMetadata summary) {
      DefaultUserProfile profile = DefaultUserProfile.getInstance();
      profile.setString(DefaultSummaryMetadata.class,
             "userName", summary.getUserName());
      profile.setString(DefaultSummaryMetadata.class,
             "computerName", summary.getComputerName());
      profile.setString(DefaultSummaryMetadata.class,
             "comments", summary.getComments());
   }

   public static class Builder implements SummaryMetadata.SummaryMetadataBuilder {

      private String name_ = null;
      private String prefix_ = null;
      private String userName_ = null;
      private String profileName_ = null;
      private String microManagerVersion_ = null;
      private String metadataVersion_ = null;
      private String computerName_ = null;
      private String directory_ = null;
      private String comments_ = null;
      
      private String channelGroup_ = null;
      private String[] channelNames_ = null;
      private Double zStepUm_ = null;
      private Double waitInterval_ = null;
      private Double[] customIntervalsMs_ = null;
      private String[] axisOrder_ = null;
      private Coords intendedDimensions_ = null;
      private String startDate_ = null;
      private MultiStagePosition[] stagePositions_ = null;

      private PropertyMap userData_ = null;

      @Override
      public DefaultSummaryMetadata build() {
         return new DefaultSummaryMetadata(this);
      }
      
      @Override
      public SummaryMetadataBuilder name(String name) {
         name_ = name;
         return this;
      }

      @Override
      public SummaryMetadataBuilder prefix(String prefix) {
         prefix_ = prefix;
         return this;
      }

      @Override
      public SummaryMetadataBuilder userName(String userName) {
         userName_ = userName;
         return this;
      }

      @Override
      public SummaryMetadataBuilder profileName(String profileName) {
         profileName_ = profileName;
         return this;
      }

      @Override
      public SummaryMetadataBuilder microManagerVersion(String microManagerVersion) {
         microManagerVersion_ = microManagerVersion;
         return this;
      }

      @Override
      public SummaryMetadataBuilder metadataVersion(String metadataVersion) {
         metadataVersion_ = metadataVersion;
         return this;
      }

      @Override
      public SummaryMetadataBuilder computerName(String computerName) {
         computerName_ = computerName;
         return this;
      }

      @Override
      public SummaryMetadataBuilder directory(String directory) {
         directory_ = directory;
         return this;
      }

      @Override
      public SummaryMetadataBuilder comments(String comments) {
         comments_ = comments;
         return this;
      }

      @Override
      public SummaryMetadataBuilder channelGroup(String channelGroup) {
         channelGroup_ = channelGroup;
         return this;
      }

      @Override
      public SummaryMetadataBuilder channelNames(String[] channelNames) {
         channelNames_ = (channelNames == null) ? null : channelNames.clone();
         return this;
      }

      @Override
      public SummaryMetadataBuilder zStepUm(Double zStepUm) {
         zStepUm_ = zStepUm;
         return this;
      }

      @Override
      public SummaryMetadataBuilder waitInterval(Double waitInterval) {
         waitInterval_ = waitInterval;
         return this;
      }

      @Override
      public SummaryMetadataBuilder customIntervalsMs(Double[] customIntervalsMs) {
         customIntervalsMs_ = (customIntervalsMs == null) ? null : customIntervalsMs.clone();
         return this;
      }

      @Override
      public SummaryMetadataBuilder axisOrder(String[] axisOrder) {
         axisOrder_ = axisOrder;
         return this;
      }

      @Override
      public SummaryMetadataBuilder intendedDimensions(Coords intendedDimensions) {
         intendedDimensions_ = intendedDimensions;
         return this;
      }

      @Override
      public SummaryMetadataBuilder startDate(String startDate) {
         startDate_ = startDate;
         return this;
      }

      @Override
      public SummaryMetadataBuilder stagePositions(MultiStagePosition[] stagePositions) {
         stagePositions_ = (stagePositions == null) ? null : stagePositions.clone();
         return this;
      }

      @Override
      public SummaryMetadataBuilder userData(PropertyMap userData) {
         userData_ = userData;
         return this;
      }
   }
   
   private String name_ = null;
   private String prefix_ = null;
   private String userName_ = null;
   private String profileName_ = null;
   private String microManagerVersion_ = null;
   private String metadataVersion_ = null;
   private String computerName_ = null;
   private String directory_ = null;
   private String comments_ = null;

   private String channelGroup_ = null;
   private String[] channelNames_ = null;
   private Double zStepUm_ = null;
   private Double waitInterval_ = null;
   private Double[] customIntervalsMs_ = null;
   private String[] axisOrder_ = null;
   private Coords intendedDimensions_ = null;
   private String startDate_ = null;
   private MultiStagePosition[] stagePositions_ = null;

   private PropertyMap userData_ = null;

   public DefaultSummaryMetadata(Builder builder) {
      name_ = builder.name_;
      prefix_ = builder.prefix_;
      userName_ = builder.userName_;
      profileName_ = builder.profileName_;
      microManagerVersion_ = builder.microManagerVersion_;
      metadataVersion_ = builder.metadataVersion_;
      computerName_ = builder.computerName_;
      directory_ = builder.directory_;
      comments_ = builder.comments_;

      channelGroup_ = builder.channelGroup_;
      channelNames_ = builder.channelNames_;
      zStepUm_ = builder.zStepUm_;
      waitInterval_ = builder.waitInterval_;
      customIntervalsMs_ = builder.customIntervalsMs_;
      axisOrder_ = builder.axisOrder_;
      intendedDimensions_ = builder.intendedDimensions_;
      startDate_ = builder.startDate_;
      stagePositions_ = builder.stagePositions_;

      userData_ = builder.userData_;
   }

   @Override
   public String getName() {
      return name_;
   }

   @Override
   public String getPrefix() {
      return prefix_;
   }

   @Override
   public String getUserName() {
      return userName_;
   }

   @Override
   public String getProfileName() {
      return profileName_;
   }

   @Override
   public String getMicroManagerVersion() {
      return microManagerVersion_;
   }

   @Override
   public String getMetadataVersion() {
      return metadataVersion_;
   }

   @Override
   public String getComputerName() {
      return computerName_;
   }

   @Override
   public String getDirectory() {
      return directory_;
   }

   @Override
   public String getComments() {
      return comments_;
   }

   @Override
   public String getChannelGroup() {
      return channelGroup_;
   }

   @Override
   public String[] getChannelNames() {
      return channelNames_;
   }

   @Override
   public String getSafeChannelName(int index) {
      if (channelNames_ == null || channelNames_.length <= index ||
            channelNames_[index] == null) {
         return "channel " + index;
      }
      return channelNames_[index];
   }

   @Override
   public Double getZStepUm() {
      return zStepUm_;
   }

   @Override
   public Double getWaitInterval() {
      return waitInterval_;
   }

   @Override
   public Double[] getCustomIntervalsMs() {
      return customIntervalsMs_;
   }

   @Override
   public String[] getAxisOrder() {
      return axisOrder_;
   }

   @Override
   public Coords getIntendedDimensions() {
      return intendedDimensions_;
   }

   @Override
   public String getStartDate() {
      return startDate_;
   }

   @Override
   public MultiStagePosition[] getStagePositions() {
      return stagePositions_;
   }

   @Override
   public PropertyMap getUserData() {
      return userData_;
   }

   @Override
   public SummaryMetadataBuilder copy() {
      return new Builder()
            .name(name_)
            .prefix(prefix_)
            .userName(userName_)
            .profileName(profileName_)
            .microManagerVersion(microManagerVersion_)
            .metadataVersion(metadataVersion_)
            .computerName(computerName_)
            .directory(directory_)
            .comments(comments_)
            .channelGroup(channelGroup_)
            .channelNames(channelNames_)
            .zStepUm(zStepUm_)
            .waitInterval(waitInterval_)
            .customIntervalsMs(customIntervalsMs_)
            .axisOrder(axisOrder_)
            .intendedDimensions(intendedDimensions_)
            .startDate(startDate_)
            .stagePositions(stagePositions_)
            .userData(userData_);
   }

   /**
    * For temporary backwards compatibility, generate a new SummaryMetadata
    * from a provided JSON object.
    */
   public static SummaryMetadata legacyFromJSON(JSONObject tags) {
      if (tags == null) {
         return new Builder().build();
      }
      // Most of these fields are not exposed in MDUtils and thus are 
      // functionally read-only from the perspective of the Java layer, 
      // excepting the acquisition engine (which is presumably what sets them
      // in the first place).
      // TODO: not preserving the index-related metadata.
      Builder builder = new Builder();

      try {
         builder.name(tags.getString("Name"));
      }
      catch (JSONException e) {
         ReportingUtils.logDebugMessage("SummaryMetadata failed to extract field name");
      }

      try {
         builder.prefix(tags.getString("Prefix"));
      }
      catch (JSONException e) {
         ReportingUtils.logDebugMessage("SummaryMetadata failed to extract field prefix");
      }

      try {
         builder.userName(tags.getString("UserName"));
      }
      catch (JSONException e) {
         ReportingUtils.logDebugMessage("SummaryMetadata failed to extract field userName");
      }

      try {
         builder.profileName(tags.getString("ProfileName"));
      }
      catch (JSONException e) {
         ReportingUtils.logDebugMessage("SummaryMetadata failed to extract field profileName");
      }

      try {
         builder.microManagerVersion(tags.getString("MicroManagerVersion"));
      }
      catch (JSONException e) {
         ReportingUtils.logDebugMessage("SummaryMetadata failed to extract field microManagerVersion");
      }

      try {
         builder.metadataVersion(tags.getString("MetadataVersion"));
      }
      catch (JSONException e) {
         ReportingUtils.logDebugMessage("SummaryMetadata failed to extract field metadataVersion");
      }

      try {
         builder.computerName(tags.getString("ComputerName"));
      }
      catch (JSONException e) {
         ReportingUtils.logDebugMessage("SummaryMetadata failed to extract field computerName");
      }

      try {
         builder.directory(tags.getString("Directory"));
      }
      catch (JSONException e) {
         ReportingUtils.logDebugMessage("SummaryMetadata failed to extract field directory");
      }

      try {
         builder.comments(MDUtils.getComments(tags));
      }
      catch (JSONException e) {
         ReportingUtils.logDebugMessage("SummaryMetadata failed to extract field comments");
      }

      try {
         builder.channelGroup(tags.getString("ChannelGroup"));
      }
      catch (JSONException e) {
         ReportingUtils.logDebugMessage("SummaryMetadata failed to extract field channelGroup");
      }

      try {
         JSONArray names = tags.getJSONArray("ChNames");
         String[] namesArr = new String[names.length()];
         for (int i = 0; i < namesArr.length; ++i) {
            namesArr[i] = names.getString(i);
         }
         builder.channelNames(namesArr);
      }
      catch (JSONException e) {
         ReportingUtils.logDebugMessage("SummaryMetadata failed to extract field channelNames");
      }

      try {
         builder.zStepUm(MDUtils.getZStepUm(tags));
      }
      catch (JSONException e) {
         ReportingUtils.logDebugMessage("SummaryMetadata failed to extract field zStepUm");
      }

      try {
         if (tags.has("WaitInterval")) {
            builder.waitInterval(tags.getDouble("WaitInterval"));
         }
         else if (tags.has("Interval_ms")) {
            builder.waitInterval(tags.getDouble("Interval_ms"));
         }
      }
      catch (JSONException e) {
         ReportingUtils.logDebugMessage("SummaryMetadata failed to extract field waitInterval");
      }

      try {
         builder.customIntervalsMs(new Double[] {NumberUtils.displayStringToDouble(tags.getString("CustomIntervals_ms"))});
      }
      catch (JSONException e) {
         ReportingUtils.logDebugMessage("SummaryMetadata failed to extract field customIntervalsMs");
      }
      catch (java.text.ParseException e) {
         ReportingUtils.logDebugMessage("Failed to parse input string for customIntervalsMs");
      }

      if (builder.customIntervalsMs_ == null) {
         // Try treating it as an array rather than a string.
         try {
            JSONArray intervals = tags.getJSONArray("CustomIntervals_ms");
            Double[] customIntervals = new Double[intervals.length()];
            for (int i = 0; i < customIntervals.length; ++i) {
               customIntervals[i] = intervals.getDouble(i);
            }
            builder.customIntervalsMs(customIntervals);
         }
         catch (JSONException e) {
            ReportingUtils.logDebugMessage("SummaryMetadata failed to extract field customIntervalsMs");
         }
      }

      try {
         JSONArray order = tags.getJSONArray("AxisOrder");
         String[] axisOrder = new String[order.length()];
         for (int i = 0; i < axisOrder.length; ++i) {
            axisOrder[i] = order.getString(i);
         }
         builder.axisOrder(axisOrder);
      }
      catch (JSONException e) {
         ReportingUtils.logDebugMessage("SummaryMetadata failed to extract field axisOrder");
      }

      // TODO: this is pretty horrible with all the try/catches, but we want
      // the lack of one dimension to not stop us from getting the others.
      // Structurally it's very similar to DefaultCoords.legacyFromJSON, but
      // accessing a slightly different set of tags.
      DefaultCoords.Builder dimsBuilder = new DefaultCoords.Builder();
      try {
         dimsBuilder.time(MDUtils.getNumFrames(tags));
      }
      catch (JSONException e) {
         ReportingUtils.logDebugMessage("Failed to extract intended time axis length: " + e);
      }
      try {
         dimsBuilder.z(MDUtils.getNumSlices(tags));
      }
      catch (JSONException e) {
         ReportingUtils.logDebugMessage("Failed to extract intended z axis length: " + e);
      }
      try {
         dimsBuilder.channel(MDUtils.getNumChannels(tags));
      }
      catch (JSONException e) {
         ReportingUtils.logDebugMessage("Failed to extract intended channel axis length: " + e);
      }
      try {
         dimsBuilder.stagePosition(MDUtils.getNumPositions(tags));
      }
      catch (JSONException e) {
         ReportingUtils.logDebugMessage("Failed to extract intended position axis length: " + e);
      }
      builder.intendedDimensions(dimsBuilder.build());

      if (tags.has("IntendedDimensions")) {
         // Replace the manually-hacked-together Coords of the above with what
         // the metadata explicitly says are the intended dimensions.
         try {
            JSONObject dims = tags.getJSONObject("IntendedDimensions");
            dimsBuilder = new DefaultCoords.Builder();
            for (String key : MDUtils.getKeys(dims)) {
               dimsBuilder.index(key, dims.getInt(key));
            }
            builder.intendedDimensions(dimsBuilder.build());
         }
         catch (JSONException e) {
            ReportingUtils.logDebugMessage("Failed to extract intended dimensions: " + e);
         }
      }

      try {
         if (tags.has("StartTime")) {
            builder.startDate(tags.getString("StartTime"));
         }
         else if (tags.has("Time")) {
            builder.startDate(tags.getString("Time"));
         }
      }
      catch (JSONException e) {
         ReportingUtils.logDebugMessage("SummaryMetadata failed to extract field startDate");
      }

      if (tags.has("StagePositions")) {
         try {
            JSONArray positions = tags.getJSONArray("StagePositions");
            MultiStagePosition[] stagePositions = new MultiStagePosition[positions.length()];
            for (int i = 0; i < stagePositions.length; ++i) {
               stagePositions[i] = MultiStagePositionFromJSON(positions.getJSONObject(i));
            }
            builder.stagePositions(stagePositions);
         }
         catch (JSONException e) {
            ReportingUtils.logDebugMessage("SummaryMetadata failed to extract field stagePositions");
         }
      }
      if (tags.has("UserData")) {
         try {
            builder.userData(
                  DefaultPropertyMap.fromJSON(tags.getJSONObject("UserData")));
         }
         catch (JSONException e) {
            ReportingUtils.logDebugMessage("SummaryMetadata failed to extract field userData: " + e);
         }
      }

      return builder.build();
   }

   /**
    * For backwards compatibility, convert to a JSON representation.
    */
   public JSONObject toJSON() {
      try {
         JSONObject result = new JSONObject();
         MDUtils.setFileName(result, name_);
         result.put("Name", name_);
         result.put("Prefix", prefix_);
         result.put("UserName", userName_);
         result.put("ProfileName", profileName_);
         result.put("MicroManagerVersion", microManagerVersion_);
         result.put("MetadataVersion", metadataVersion_);
         result.put("ComputerName", computerName_);
         result.put("Directory", directory_);
         MDUtils.setComments(result, comments_);
         result.put("ChannelGroup", channelGroup_);
         if (channelNames_ != null) {
            JSONArray names = new JSONArray();
            for (int i = 0; i < channelNames_.length; ++i) {
               names.put(channelNames_[i]);
            }
            result.put("ChNames", names);
         }
         if (zStepUm_ != null) {
            MDUtils.setZStepUm(result, zStepUm_);
         }
         result.put("WaitInterval", waitInterval_);
         if (customIntervalsMs_ != null) {
            JSONArray intervals = new JSONArray();
            for (int i = 0; i < customIntervalsMs_.length; ++i) {
               intervals.put(customIntervalsMs_[i]);
            }
            result.put("CustomIntervals_ms", intervals);
         }
         if (axisOrder_ != null) {
            JSONArray order = new JSONArray();
            for (int i = 0; i < axisOrder_.length; ++i) {
               order.put(axisOrder_[i]);
            }
            result.put("AxisOrder", order);
         }
         if (intendedDimensions_ != null) {
            MDUtils.setNumChannels(result,
                  intendedDimensions_.getChannel());
            MDUtils.setNumFrames(result,
                  intendedDimensions_.getTime());
            MDUtils.setNumSlices(result,
                  intendedDimensions_.getZ());
            MDUtils.setNumPositions(result,
                  intendedDimensions_.getStagePosition());

            JSONObject intendedDims = new JSONObject();
            for (String axis : intendedDimensions_.getAxes()) {
               intendedDims.put(axis, intendedDimensions_.getIndex(axis));
            }
            result.put("IntendedDimensions", intendedDims);
         }
         result.put("StartTime", startDate_);
         if (stagePositions_ != null) {
            JSONArray positions = new JSONArray();
            for (int i = 0; i < stagePositions_.length; ++i) {
               positions.put(MultiStagePositionToJSON(stagePositions_[i]));
            }
            result.put("StagePositions", positions);
         }
         if (userData_ != null) {
            result.put("UserData", ((DefaultPropertyMap) userData_).toJSON());
         }
         return result;
      }
      catch (JSONException e) {
         ReportingUtils.logError(e, "Couldn't convert DefaultSummaryMetadata to JSON");
         return null;
      }
   }

   /**
    * Convert a MultiStagePosition to a JSONObject. It's here instead of in
    * MultiStagePosition.java to avoid exposing our JSON library in the API,
    * as said library is old and creaky.
    */
   public static JSONObject MultiStagePositionToJSON(MultiStagePosition pos) {
      try {
         JSONObject result = new JSONObject();
         result.put("label", pos.getLabel());
         result.put("defaultXYStage", pos.getDefaultXYStage());
         result.put("defaultZStage", pos.getDefaultZStage());
         result.put("gridRow", pos.getGridRow());
         result.put("gridCol", pos.getGridColumn());
         DefaultPropertyMap.Builder propBuilder = new DefaultPropertyMap.Builder();
         for (String name : pos.getPropertyNames()) {
            propBuilder.putString(name, pos.getProperty(name));
         }
         result.put("properties", ((DefaultPropertyMap) (propBuilder.build())).toJSON());
         JSONArray subPoses = new JSONArray();
         for (int i = 0; i < pos.size(); ++i) {
            subPoses.put(StagePositionToJSON(pos.get(i)));
         }
         result.put("subpositions", subPoses);
         return result;
      }
      catch (JSONException e) {
         ReportingUtils.logError("Failed to convert MultiStagePosition to JSON: " + e);
         return null;
      }
   }

   /**
    * Convert a JSONObject to a MultiStagePosition. As with
    * MultiStagePositionToJSON, this method is here to avoid exposing our old
    * JSON library in the API.
    */
   public static MultiStagePosition MultiStagePositionFromJSON(
         JSONObject tags) {
      try {
         MultiStagePosition result = new MultiStagePosition();
         result.setLabel(tags.getString("label"));
         result.setDefaultXYStage(tags.getString("defaultXYStage"));
         result.setDefaultZStage(tags.getString("defaultZStage"));
         result.setGridCoordinates(tags.getInt("gridRow"),
               tags.getInt("gridCol"));
         JSONObject props = tags.getJSONObject("properties");
         for (String key : MDUtils.getKeys(props)) {
            result.setProperty(key, props.getString(key));
         }
         JSONArray subPoses = tags.getJSONArray("subpositions");
         for (int i = 0; i < subPoses.length(); ++i) {
            JSONObject subPos = subPoses.getJSONObject(i);
            result.add(StagePositionFromJSON(subPos));
         }
         return result;
      }
      catch (JSONException e) {
         ReportingUtils.logError("Failed to convert JSONObject to MultiStagePosition: " + e);
         return null;
      }
   }

   /**
    * Convert a StagePosition to a JSONObject. Here instead of in
    * StagePosition.java to avoid polluting our API with an old JSON library.
    */
   private static JSONObject StagePositionToJSON(StagePosition pos) throws JSONException {
      JSONObject result = new JSONObject();
      result.put("x", pos.x);
      result.put("y", pos.y);
      result.put("z", pos.z);
      result.put("stageName", pos.stageName);
      result.put("numAxes", pos.numAxes);
      return result;
   }

   /**
    * Convert a JSONObject to a StagePosition. Here instead of in
    * StagePosition.java to avoid polluting our API with an old JSON library.
    */
   private static StagePosition StagePositionFromJSON(JSONObject tags) throws JSONException {
      StagePosition result = new StagePosition();
      result.x = tags.getDouble("x");
      result.y = tags.getDouble("y");
      result.z = tags.getDouble("z");
      result.stageName = tags.getString("stageName");
      result.numAxes = tags.getInt("numAxes");
      return result;
   }
}
