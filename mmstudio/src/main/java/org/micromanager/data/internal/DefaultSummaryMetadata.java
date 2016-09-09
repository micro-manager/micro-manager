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
import java.util.Arrays;
import java.util.Iterator;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.MultiStagePosition;
import org.micromanager.PropertyMap;
import org.micromanager.PropertyMap.PropertyMapBuilder;
import org.micromanager.StagePosition;
import org.micromanager.data.Coords;
import org.micromanager.data.SummaryMetadata;
import org.micromanager.internal.MMStudio;
import org.micromanager.internal.utils.DefaultUserProfile;
import org.micromanager.internal.utils.MDUtils;
import org.micromanager.internal.utils.NumberUtils;
import org.micromanager.internal.utils.ReportingUtils;

public final class DefaultSummaryMetadata implements SummaryMetadata {

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
   }

   public static class Builder implements SummaryMetadata.SummaryMetadataBuilder {

      private String prefix_ = null;
      private String userName_ = null;
      private String profileName_ = null;
      private String microManagerVersion_ = null;
      private String metadataVersion_ = null;
      private String computerName_ = null;
      private String directory_ = null;
      
      private String channelGroup_ = null;
      private String[] channelNames_ = null;
      private Double zStepUm_ = null;
      private Double waitInterval_ = null;
      private Double[] customIntervalsMs_ = null;
      private String[] axisOrder_ = null;
      private Coords intendedDimensions_ = null;
      private String startDate_ = null;
      private MultiStagePosition[] stagePositions_ = null;
      private Boolean keepShutterOpenSlices_ = null;
      private Boolean keepShutterOpenChannels_ = null;

      private PropertyMap userData_ = null;

      @Override
      public DefaultSummaryMetadata build() {
         return new DefaultSummaryMetadata(this);
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
      public SummaryMetadataBuilder keepShutterOpenSlices(Boolean keepShutterOpenSlices) {
         keepShutterOpenSlices_ = keepShutterOpenSlices;
         return this;
      }

      @Override
      public SummaryMetadataBuilder keepShutterOpenChannels(Boolean keepShutterOpenChannels) {
         keepShutterOpenChannels_ = keepShutterOpenChannels;
         return this;
      }

      @Override
      public SummaryMetadataBuilder userData(PropertyMap userData) {
         userData_ = userData;
         return this;
      }
   }
   
   private String prefix_ = null;
   private String userName_ = null;
   private String profileName_ = null;
   private String microManagerVersion_ = null;
   private String metadataVersion_ = null;
   private String computerName_ = null;
   private String directory_ = null;

   private String channelGroup_ = null;
   private String[] channelNames_ = null;
   private Double zStepUm_ = null;
   private Double waitInterval_ = null;
   private Double[] customIntervalsMs_ = null;
   private String[] axisOrder_ = null;
   private Coords intendedDimensions_ = null;
   private String startDate_ = null;
   private MultiStagePosition[] stagePositions_ = null;
   private Boolean keepShutterOpenSlices_ = null;
   private Boolean keepShutterOpenChannels_ = null;

   private PropertyMap userData_ = null;

   public DefaultSummaryMetadata(Builder builder) {
      prefix_ = builder.prefix_;
      userName_ = builder.userName_;
      profileName_ = builder.profileName_;
      microManagerVersion_ = builder.microManagerVersion_;
      metadataVersion_ = builder.metadataVersion_;
      computerName_ = builder.computerName_;
      directory_ = builder.directory_;

      channelGroup_ = builder.channelGroup_;
      channelNames_ = builder.channelNames_;
      zStepUm_ = builder.zStepUm_;
      waitInterval_ = builder.waitInterval_;
      customIntervalsMs_ = builder.customIntervalsMs_;
      axisOrder_ = builder.axisOrder_;
      intendedDimensions_ = builder.intendedDimensions_;
      startDate_ = builder.startDate_;
      stagePositions_ = builder.stagePositions_;
      keepShutterOpenSlices_ = builder.keepShutterOpenSlices_;
      keepShutterOpenChannels_ = builder.keepShutterOpenChannels_;

      userData_ = builder.userData_;
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
   public String getChannelGroup() {
      return channelGroup_;
   }

   @Override
   public String[] getChannelNames() {
      return channelNames_;
   }

   @Override
   public String getSafeChannelName(int index) {
      if (index < 0 || channelNames_ == null ||
            channelNames_.length <= index || channelNames_[index] == null) {
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
   public Boolean getKeepShutterOpenSlices() {
      return keepShutterOpenSlices_;
   }

   @Override
   public Boolean getKeepShutterOpenChannels() {
      return keepShutterOpenChannels_;
   }

   @Override
   public PropertyMap getUserData() {
      return userData_;
   }

   @Override
   public SummaryMetadataBuilder copy() {
      return new Builder()
            .prefix(prefix_)
            .userName(userName_)
            .profileName(profileName_)
            .microManagerVersion(microManagerVersion_)
            .metadataVersion(metadataVersion_)
            .computerName(computerName_)
            .directory(directory_)
            .channelGroup(channelGroup_)
            .channelNames(channelNames_)
            .zStepUm(zStepUm_)
            .waitInterval(waitInterval_)
            .customIntervalsMs(customIntervalsMs_)
            .axisOrder(axisOrder_)
            .intendedDimensions(intendedDimensions_)
            .startDate(startDate_)
            .stagePositions(stagePositions_)
            .keepShutterOpenSlices(keepShutterOpenSlices_)
            .keepShutterOpenChannels(keepShutterOpenChannels_)
            .userData(userData_);
   }

   /**
    * Deserialize a JSON representation of the summary metadata.
    * NOTE: JSONExceptions in this method are all ignored as they just
    * indicate that a particular field is missing, and all fields are
    * optional.
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
         builder.prefix(tags.getString("Prefix"));
      }
      catch (JSONException e) {}

      try {
         builder.userName(tags.getString("UserName"));
      }
      catch (JSONException e) {}

      try {
         builder.profileName(tags.getString("ProfileName"));
      }
      catch (JSONException e) {}

      try {
         builder.microManagerVersion(tags.getString("MicroManagerVersion"));
      }
      catch (JSONException e) {}

      try {
         builder.metadataVersion(tags.getString("MetadataVersion"));
      }
      catch (JSONException e) {}

      try {
         builder.computerName(tags.getString("ComputerName"));
      }
      catch (JSONException e) {}

      try {
         builder.directory(tags.getString("Directory"));
      }
      catch (JSONException e) {}

      try {
         builder.channelGroup(tags.getString("ChannelGroup"));
      }
      catch (JSONException e) {}

      try {
         JSONArray names = tags.getJSONArray("ChNames");
         String[] namesArr = new String[names.length()];
         for (int i = 0; i < namesArr.length; ++i) {
            namesArr[i] = names.getString(i);
         }
         builder.channelNames(namesArr);
      }
      catch (JSONException e) {}

      try {
         builder.zStepUm(MDUtils.getZStepUm(tags));
      }
      catch (JSONException e) {}

      try {
         if (tags.has("WaitInterval")) {
            builder.waitInterval(tags.getDouble("WaitInterval"));
         }
         else if (tags.has("Interval_ms")) {
            builder.waitInterval(tags.getDouble("Interval_ms"));
         }
      }
      catch (JSONException e) {}

      try {
         builder.customIntervalsMs(new Double[] {NumberUtils.displayStringToDouble(tags.getString("CustomIntervals_ms"))});
      }
      catch (JSONException e) {}
      catch (java.text.ParseException e) {
         // Likely an array instead of a number, in which case the below logic
         // kicks in.
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
         catch (JSONException e) {}
      }

      try {
         JSONArray order = tags.getJSONArray("AxisOrder");
         String[] axisOrder = new String[order.length()];
         for (int i = 0; i < axisOrder.length; ++i) {
            axisOrder[i] = order.getString(i);
         }
         builder.axisOrder(axisOrder);
      }
      catch (JSONException e) {}

      // TODO: this is pretty horrible with all the try/catches, but we want
      // the lack of one dimension to not stop us from getting the others.
      // Structurally it's very similar to DefaultCoords.legacyFromJSON, but
      // accessing a slightly different set of tags.
      DefaultCoords.Builder dimsBuilder = new DefaultCoords.Builder();
      try {
         dimsBuilder.time(MDUtils.getNumFrames(tags));
      }
      catch (JSONException e) {}
      try {
         dimsBuilder.z(MDUtils.getNumSlices(tags));
      }
      catch (JSONException e) {}
      try {
         dimsBuilder.channel(MDUtils.getNumChannels(tags));
      }
      catch (JSONException e) {}
      try {
         dimsBuilder.stagePosition(MDUtils.getNumPositions(tags));
      }
      catch (JSONException e) {}
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
         catch (JSONException e) {}
      }

      try {
         if (tags.has("StartTime")) {
            builder.startDate(tags.getString("StartTime"));
         }
         else if (tags.has("Time")) {
            builder.startDate(tags.getString("Time"));
         }
      }
      catch (JSONException e) {}

      if (tags.has("StagePositions")) {
         try {
            JSONArray positions = tags.getJSONArray("StagePositions");
            MultiStagePosition[] stagePositions = new MultiStagePosition[positions.length()];
            for (int i = 0; i < stagePositions.length; ++i) {
               stagePositions[i] = MultiStagePositionFromJSON(positions.getJSONObject(i));
            }
            builder.stagePositions(stagePositions);
         }
         catch (JSONException e) {}
      }
      else if (tags.has("InitialPositionList")) {
         // Legacy 1.4 format for the same information.
         try {
            JSONArray positions = tags.getJSONArray("InitialPositionList");
            MultiStagePosition[] stagePositions = new MultiStagePosition[positions.length()];
            for (int i =0 ; i < stagePositions.length; ++i) {
               stagePositions[i] = MultiStagePositionFromJSON14(positions.getJSONObject(i));
            }
            builder.stagePositions(stagePositions);
         }
         catch (JSONException e) {}
      }

      try {
         builder.keepShutterOpenSlices(tags.getBoolean("KeepShutterOpenSlices"));
      }
      catch (JSONException e) {}
      try {
         builder.keepShutterOpenChannels(tags.getBoolean("KeepShutterOpenChannels"));
      }
      catch (JSONException e) {}


      if (tags.has("UserData")) {
         try {
            builder.userData(
                  DefaultPropertyMap.fromJSON(tags.getJSONObject("UserData")));
         }
         catch (JSONException e) {
         }
       }
      else {
         // 1.4 did not have the field UserData but user data were interspersed
         // with other metadata.
         String[] reservedNames = {"Prefix", "UserName", "ProfileName",
            "MicroManagerVersion", "MetadataVersion", "ComputerName",
            "Directory", "ChannelGroup", "ChNames", "WaitInterval",
            "Interval_ms", "CustomIntervals_ms", "AxisOrder",
            "IntendedDimensions", "StartTime", "Time", "StagePositions",
            "InitialPositionList", "KeepShutterOpenSlices", "UserData",
            "Frames", "Slices", "Channels", "PixelSize_um", "z-step_um",
            "ChContrastMax", "ChContrastMin"};

         PropertyMapBuilder pmb = MMStudio.getInstance().data().getPropertyMapBuilder();

         Iterator<String> keys = tags.keys();
         while (keys.hasNext()) {
            String key = keys.next();
            if (!Arrays.asList(reservedNames).contains(key)) {
               try {
                  pmb.putString(key, tags.getString(key));
               }
               catch (JSONException ex) {
               }
               catch (ClassCastException ex2) {
               }
            }
         }
         builder.userData(pmb.build());
       }

      return builder.build();
   }

   /**
    * Convert to a JSON representation for serialization.
    */
   public JSONObject toJSON() {
      try {
         JSONObject result = new JSONObject();
         result.put("Prefix", prefix_);
         result.put("UserName", userName_);
         result.put("ProfileName", profileName_);
         result.put("MicroManagerVersion", microManagerVersion_);
         result.put("MetadataVersion", metadataVersion_);
         result.put("ComputerName", computerName_);
         result.put("Directory", directory_);
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
         if (keepShutterOpenSlices_ != null) {
            result.put("KeepShutterOpenSlices", keepShutterOpenSlices_);
         }
         if (keepShutterOpenChannels_ != null) {
            result.put("KeepShutterOpenChannels", keepShutterOpenChannels_);
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
         // 1.4's JSON format for MultiStagePositions is different; try using
         // that instead.
         try {
            return MultiStagePositionFromJSON14(tags);
         }
         catch (JSONException e2) {
            ReportingUtils.logError("Failed to convert JSONObject to MultiStagePosition: " + e2);
            return null;
         }
      }
   }

   /**
    * Convert a JSONObject that was generated by Micro-Manager 1.4 to a
    * MultiStagePosition. The arrangement of data in the object is different
    * from the arrangement 2.0 uses.
    * TODO: this method does not attempt to preserve stage position properties.
    * However, 1.4 does not appear to save stage position properties anyway,
    * so this may be moot.
    */
   public static MultiStagePosition MultiStagePositionFromJSON14(
         JSONObject tags) throws JSONException {
      MultiStagePosition result = new MultiStagePosition();
      result.setLabel(tags.getString("Label"));
      result.setGridCoordinates(tags.getInt("GridColumnIndex"),
            tags.getInt("GridRowIndex"));
      JSONObject subPositions = tags.getJSONObject("DeviceCoordinatesUm");
      for (String key : MDUtils.getKeys(subPositions)) {
         StagePosition subPos = new StagePosition();
         subPos.stageName = key;
         JSONArray positions = subPositions.getJSONArray(key);
         if (positions.length() == 1) {
            subPos.x = positions.getDouble(0);
         }
         else if (positions.length() == 2) {
            subPos.x = positions.getDouble(0);
            subPos.y = positions.getDouble(1);
         }
         else {
            throw new JSONException("Unexpected axis length " + positions.length());
         }
         result.add(subPos);
      }
      return result;
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
