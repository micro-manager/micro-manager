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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.commons.lang3.ArrayUtils;
import org.micromanager.MultiStagePosition;
import org.micromanager.PropertyMap;
import org.micromanager.PropertyMaps;
import org.micromanager.UserProfile;
import org.micromanager.data.Coords;
import org.micromanager.data.SummaryMetadata;
import static org.micromanager.data.internal.PropertyKey.*;
import org.micromanager.internal.MMStudio;
import org.micromanager.internal.utils.ReportingUtils;

public final class DefaultSummaryMetadata implements SummaryMetadata {
   /**
    * This is the version string for all metadata as saved in Micro-Manager
    * data files.
    * <p>
    * Because the files are read-only once written, this number does not need
    * to be frequently incremented. For example, new fields can be added
    * without changing the version number as long as care is taken to ensure
    * that keys used in the past are avoided.
    */
   public static final String CURRENT_METADATA_VERSION = "12.0.0";

   // TODO This shouldn't live here. Move to DataManager.
   public static SummaryMetadata getStandardSummaryMetadata() {
      UserProfile profile = MMStudio.getInstance().profile();

      Builder b = new Builder().
            userName(System.getProperty("user.name")).
            profileName(profile.getProfileName());

      try {
         b.computerName(InetAddress.getLocalHost().getHostName());
      }
      catch (UnknownHostException e) {
      }
      catch (Exception e) {
         // Apple Java 6 might throw other exceptions when there is no network
         // interface.
      }

      return b.build();
   }


   public static class Builder implements SummaryMetadata.Builder {
      private final PropertyMap.Builder b_;

      public Builder() {
         // Tests may run without an MMStudio instance, so check to avoid null 
         // pointer errors
         String version = "Unknown";
         if (MMStudio.getInstance() != null) {
            version = MMStudio.getInstance().compat().getVersion();
         }
         b_ = PropertyMaps.builder().
               putString(MICRO_MANAGER_VERSION.key(), version).
               putString(METADATA_VERSION.key(), CURRENT_METADATA_VERSION). 
                 // TODO: we should not depend on the defaults provided here
                 // Many bugs manifest themselves if this field is not set
               putStringList(AXIS_ORDER.key(), Coords.C, Coords.T, Coords.Z, Coords.P);
      }

      private Builder(PropertyMap toCopy) {
         b_ = toCopy.copyBuilder();
      }

      @Override
      public DefaultSummaryMetadata build() {
         return new DefaultSummaryMetadata(b_.build());
      }

      @Override
      public Builder prefix(String prefix) {
         b_.putString(PREFIX.key(), prefix);
         return this;
      }

      @Override
      public Builder userName(String userName) {
         b_.putString(USER_NAME.key(), userName);
         return this;
      }

      @Override
      public Builder profileName(String profileName) {
         b_.putString(PROFILE_NAME.key(), profileName);
         return this;
      }

      @Override
      public Builder computerName(String computerName) {
         b_.putString(COMPUTER_NAME.key(), computerName);
         return this;
      }

      @Override
      public Builder directory(String directory) {
         b_.putString(DIRECTORY.key(), directory);
         return this;
      }

      @Override
      public Builder channelGroup(String channelGroup) {
         b_.putString(CHANNEL_GROUP.key(), channelGroup);
         return this;
      }

      @Override
      public Builder channelNames(String... channelNames) {
         b_.putStringList(CHANNEL_NAMES.key(), channelNames);
         return this;
      }

      @Override
      public Builder channelNames(Iterable<String> channelNames) {
         b_.putStringList(CHANNEL_NAMES.key(), channelNames);
         return this;
      }

      @Override
      public Builder zStepUm(Double zStepUm) {
         b_.putDouble(Z_STEP_UM.key(), zStepUm);
         return this;
      }

      @Override
      public Builder waitInterval(Double waitInterval) {
         b_.putDouble(INTERVAL_MS.key(), waitInterval);
         return this;
      }

      @Override
      public Builder customIntervalsMs(double... customIntervalsMs) {
         b_.putDoubleList(CUSTOM_INTERVALS_MS.key(), customIntervalsMs);
         return this;
      }

      @Override
      public Builder customIntervalsMs(Iterable<Double> customIntervalsMs) {
         b_.putDoubleList(CUSTOM_INTERVALS_MS.key(), customIntervalsMs);
         return this;
      }

      @Override
      @Deprecated
      public Builder customIntervalsMs(Double[] customIntervalsMs) {
         return customIntervalsMs(customIntervalsMs == null ? null :
               ArrayUtils.toPrimitive(customIntervalsMs));
      }

      @Override
      public Builder axisOrder(String... axisOrder) {
         b_.putStringList(AXIS_ORDER.key(), axisOrder);
         return this;
      }

      @Override
      public Builder axisOrder(Iterable<String> axisOrder) {
         b_.putStringList(AXIS_ORDER.key(), axisOrder);
         return this;
      }

      @Override
      public Builder intendedDimensions(Coords intendedDimensions) {
         b_.putPropertyMap(INTENDED_DIMENSIONS.key(), ((DefaultCoords) intendedDimensions).toPropertyMap());
         return this;
      }

      @Override
      public Builder startDate(String startDate) {
         b_.putString(START_TIME.key(), startDate);
         return this;
      }

      @Override
      public Builder stagePositions(MultiStagePosition... stagePositions) {
         return stagePositions(Arrays.asList(stagePositions));
      }

      @Override
      public Builder stagePositions(Iterable<MultiStagePosition> stagePositions) {
         List<PropertyMap> msps = new ArrayList<>();
         for (MultiStagePosition msp : stagePositions) {
            msps.add(msp.toPropertyMap());
         }
         b_.putPropertyMapList(STAGE_POSITIONS.key(), msps);
         return this;
      }

      @Override
      public Builder keepShutterOpenSlices(Boolean keepShutterOpenSlices) {
         b_.putBoolean(KEEP_SHUTTER_OPEN_SLICES.key(), keepShutterOpenSlices);
         return this;
      }

      @Override
      public Builder keepShutterOpenChannels(Boolean keepShutterOpenChannels) {
         b_.putBoolean(KEEP_SHUTTER_OPEN_CHANNELS.key(), keepShutterOpenChannels);
         return this;
      }

      @Override
      public Builder userData(PropertyMap userData) {
         b_.putPropertyMap(USER_DATA.key(), userData);
         return this;
      }
   }


   private final PropertyMap pmap_;

   private DefaultSummaryMetadata(PropertyMap pmap) {
      pmap_ = pmap;

      // Check map format
      // Experience has shown that these functions can encounter exceptions when
      // opening certain files.  We need to know about these exceptions.
      try {
         getPrefix();
         getUserName();
         getProfileName();
         getMicroManagerVersion();
         getMetadataVersion();
         getComputerName();
         getDirectory();
         getChannelGroup();
         getChannelNameList();
         getZStepUm();
         getWaitInterval();
         getCustomIntervalsMsList();
         getOrderedAxes();
         getIntendedDimensions();
         getStartDate();
         getStagePositionList();
         getKeepShutterOpenSlices();
         getKeepShutterOpenChannels();
         getUserData();
      } catch (Exception ex) {
         ReportingUtils.showError(ex,
                 "Encountered an error reading metadata.  Please report (Help > Report a Problem)");
      }
   }

   @Override
   public String getPrefix() {
      return pmap_.getString(PREFIX.key(), null);
   }

   @Override
   public String getUserName() {
      return pmap_.getString(USER_NAME.key(), null);
   }

   @Override
   public String getProfileName() {
      return pmap_.getString(PROFILE_NAME.key(), null);
   }

   @Override
   public String getMicroManagerVersion() {
      return pmap_.getString(MICRO_MANAGER_VERSION.key(), null);
   }

   @Override
   public String getMetadataVersion() {
      return pmap_.getString(METADATA_VERSION.key(), null);
   }

   @Override
   public String getComputerName() {
      return pmap_.getString(COMPUTER_NAME.key(), null);
   }

   @Override
   public String getDirectory() {
      return pmap_.getString(DIRECTORY.key(), null);
   }

   @Override
   public String getChannelGroup() {
      return pmap_.getString(CHANNEL_GROUP.key(), null);
   }

   @Override
   public List<String> getChannelNameList() {
      return pmap_.getStringList(CHANNEL_NAMES.key(), Collections.<String>emptyList());
   }

   @Override
   @Deprecated
   public String[] getChannelNames() {
      return pmap_.containsKey(CHANNEL_NAMES.key()) ?
            getChannelNameList().toArray(new String[0]) : null;
   }

   @Override
   public String getSafeChannelName(int index) {
      List<String> channels = pmap_.getStringList(CHANNEL_NAMES.key());
      if (index >= 0 && index < channels.size()) {
         return channels.get(index);
      }
      return "channel " + index;
   }

   @Override
   public Double getZStepUm() {
      return pmap_.containsKey(Z_STEP_UM.key()) ?
            pmap_.getDouble(Z_STEP_UM.key(), Double.NaN) : null;
   }

   @Override
   public Double getWaitInterval() {
      return pmap_.containsKey(INTERVAL_MS.key()) ?
            pmap_.getDouble(INTERVAL_MS.key(), Double.NaN) : null;
   }

   @Override
   public List<Double> getCustomIntervalsMsList() {
      return pmap_.getDoubleList(CUSTOM_INTERVALS_MS.key(), (List) null);
   }

   @Override
   public double[] getCustomIntervalsMsArray() {
      return pmap_.getDoubleList(CUSTOM_INTERVALS_MS.key());
   }

   @Override
   @Deprecated
   public Double[] getCustomIntervalsMs() {
      return ArrayUtils.toObject(getCustomIntervalsMsArray());
   }

   @Override
   public List<String> getOrderedAxes() {
      return pmap_.getStringList(AXIS_ORDER.key(), Collections.<String>emptyList());
   }

   /**
    * 
    * @return Array with axes used in this data set in desired order
    * @deprecated use getOrderedAxes instead
    */
   
   @Override
   @Deprecated
   public String[] getAxisOrder() {
      return pmap_.getStringList(AXIS_ORDER.key()).toArray(new String[0]);
   }

   @Override
   public Coords getIntendedDimensions() {
      return DefaultCoords.fromPropertyMap(pmap_.getPropertyMap(
            INTENDED_DIMENSIONS.key(), PropertyMaps.emptyPropertyMap()));
   }

   @Override
   public String getStartDate() {
      return pmap_.getString(START_TIME.key(), null);
   }

   @Override
   public List<MultiStagePosition> getStagePositionList() {
      List<MultiStagePosition> ret = new ArrayList<>();
      List<PropertyMap> msps = pmap_.getPropertyMapList(STAGE_POSITIONS.key());
      for (PropertyMap mspPmap : msps) {
         ret.add(MultiStagePosition.fromPropertyMap(mspPmap));
      }
      return ret;
   }

   @Override
   @Deprecated
   public MultiStagePosition[] getStagePositions() {
      return getStagePositionList().toArray(new MultiStagePosition[0]);
   }

   @Override
   public Boolean getKeepShutterOpenSlices() {
      return pmap_.containsKey(KEEP_SHUTTER_OPEN_SLICES.key()) ?
            pmap_.getBoolean(KEEP_SHUTTER_OPEN_SLICES.key(), false) : null;
   }

   @Override
   public Boolean getKeepShutterOpenChannels() {
      return pmap_.containsKey(KEEP_SHUTTER_OPEN_CHANNELS.key()) ?
            pmap_.getBoolean(KEEP_SHUTTER_OPEN_CHANNELS.key(), false) : null;
   }

   @Override
   public PropertyMap getUserData() {
      return pmap_.getPropertyMap(USER_DATA.key(), PropertyMaps.emptyPropertyMap());
   }

   @Override
   public Builder copyBuilder() {
      return new Builder(pmap_);
   }

   @Override
   @Deprecated
   public Builder copy() {
      return copyBuilder();
   }

   public PropertyMap toPropertyMap() {
      return pmap_;
   }

   public static SummaryMetadata fromPropertyMap(PropertyMap pmap) {
      return new DefaultSummaryMetadata(pmap);
   }

   @Override
   public String toString() {
      return toPropertyMap().toString();
   }
}