///////////////////////////////////////////////////////////////////////////////
// PROJECT:       Micro-Manager
// SUBSYSTEM:     Data API
// -----------------------------------------------------------------------------
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

package org.micromanager.data;

import java.util.List;
import org.micromanager.MultiStagePosition;
import org.micromanager.PropertyMap;

/**
 * This class defines the summary metadata that applies to all images in a dataset. It is immutable;
 * construct it with a SummaryMetadataBuilder. All fields of the SummaryMetadata that are not
 * explicitly initialized will default to null. You are not expected to implement this interface; it
 * is here to describe how you can interact with SummaryMetadata created by Micro-Manager itself. If
 * you need to create a new SummaryMetadata, either use the getSummaryMetadataBuilder() method of
 * the DataManager class, or call the copy() method of an existing SummaryMetadata instance.
 *
 * <p>This class uses a Builder pattern. Please see https://micro-manager.org/wiki/Using_Builders
 * for more information.
 */
public interface SummaryMetadata {
  interface Builder extends SummaryMetadataBuilder {
    @Override
    Builder prefix(String prefix);

    @Override
    Builder userName(String userName);

    @Override
    Builder profileName(String profileName);

    @Override
    Builder computerName(String computerName);

    @Override
    Builder directory(String directory);

    @Override
    Builder channelGroup(String channelGroup);

    @Override
    Builder channelNames(String... channelNames);

    Builder channelNames(Iterable<String> channelNames);

    @Override
    Builder zStepUm(Double zStepUm);

    @Override
    Builder waitInterval(Double waitInterval);

    Builder customIntervalsMs(double... customIntervalsMs);

    Builder customIntervalsMs(Iterable<Double> customIntervalsMs);

    @Override
    Builder axisOrder(String... axisOrder);

    Builder axisOrder(Iterable<String> axisOrder);

    @Override
    Builder intendedDimensions(Coords intendedDimensions);

    @Override
    Builder keepShutterOpenSlices(Boolean keepShutterOpenSlices);

    @Override
    Builder keepShutterOpenChannels(Boolean keepShutterOpenChannels);

    @Override
    Builder startDate(String startDate);

    @Override
    Builder stagePositions(MultiStagePosition... stagePositions);

    Builder stagePositions(Iterable<MultiStagePosition> stagePositions);

    @Override
    Builder userData(PropertyMap userData);
  }

  /** @deprecated - Use SummaryMetadata.Builder instead */
  @Deprecated
  interface SummaryMetadataBuilder {
    SummaryMetadata build();

    SummaryMetadataBuilder prefix(String prefix);

    SummaryMetadataBuilder userName(String userName);

    SummaryMetadataBuilder profileName(String profileName);

    SummaryMetadataBuilder computerName(String computerName);

    SummaryMetadataBuilder directory(String directory);

    SummaryMetadataBuilder channelGroup(String channelGroup);

    SummaryMetadataBuilder channelNames(String... channelNames);

    SummaryMetadataBuilder zStepUm(Double zStepUm);

    SummaryMetadataBuilder waitInterval(Double waitInterval);

    @Deprecated
    SummaryMetadataBuilder customIntervalsMs(Double[] customIntervalsMs);

    SummaryMetadataBuilder axisOrder(String... axisOrder);

    SummaryMetadataBuilder intendedDimensions(Coords intendedDimensions);

    SummaryMetadataBuilder keepShutterOpenSlices(Boolean keepShutterOpenSlices);

    SummaryMetadataBuilder keepShutterOpenChannels(Boolean keepShutterOpenChannels);

    SummaryMetadataBuilder startDate(String startDate);

    SummaryMetadataBuilder stagePositions(MultiStagePosition... stagePositions);

    SummaryMetadataBuilder userData(PropertyMap userData);
  }

  Builder copyBuilder();

  /**
   * @return - A copy of the SummaryMetadataBuilder
   * @deprecated - Use SummaryMetadata.Builder.copyBuilder instead
   */
  @Deprecated
  SummaryMetadataBuilder copy();

  /**
   * The user-supplied portion of the filename, plus any additional numerical identifier needed to
   * ensure uniqueness. This should in practice be the name of the directory that the data is saved
   * in.
   *
   * @return user-supplied portion of the filename.
   */
  String getPrefix();

  /**
   * The signed-in user of the machine that collected this data
   *
   * @return signed-in user of the machine that collected this data
   */
  String getUserName();

  /**
   * The name of the Micro-Manager profile used to collect this data.
   *
   * @return name of the micro-Manager profile used to collect this data
   */
  String getProfileName();

  /**
   * The version of Micro-Manager used to collect the data
   *
   * @return version of Micro-Manager used to collect the data
   */
  String getMicroManagerVersion();

  /**
   * The version of the metadata when the data was collected
   *
   * @return version of the metadata when the data was collected
   */
  String getMetadataVersion();

  /**
   * The name of the computer the data was collected on
   *
   * @return name of the computer the data was collected on
   */
  String getComputerName();

  /**
   * The directory the data was originally saved to
   *
   * @return directory the data was originally saved to
   */
  String getDirectory();

  /**
   * The config group that was used to switch between channels.
   *
   * @return name of the channel config group
   */
  String getChannelGroup();

  List<String> getChannelNameList();

  /**
   * @return Array with channelname
   * @deprecated - Use getChannelNameList() instead
   */
  @Deprecated
  String[] getChannelNames();

  /**
   * Retrieve the name of the specified channel. Ordinarily this is simply an index into the
   * channelNames array (per getChannelNames()), but if there is no name there (channelNames is
   * unset, or not long enough, or contains a null), then the name will be "channel N" where N is
   * the channel index.
   *
   * @param index Channel index to get the name for.
   * @return Name of the channel.
   */
  String getSafeChannelName(int index);

  /**
   * Distance between slices in a volume of data, in microns
   *
   * @return distance between slices in a volume of data, in microns
   */
  Double getZStepUm();

  /**
   * Amount of time to wait between timepoints
   *
   * @return amount of time to wait between timepoints
   */
  Double getWaitInterval();

  /**
   * When using a variable amount of time between timepoints, this array has the list of wait times
   *
   * @return list of wait times between timepoints
   */
  List<Double> getCustomIntervalsMsList();

  double[] getCustomIntervalsMsArray();
  /**
   * @return - Array with custom intervals
   * @deprecated - Use double[] getCustomIntervalsMsArray() instead
   */
  @Deprecated
  Double[] getCustomIntervalsMs();

  /**
   * The order in which axes changed when adding images to the dataset. The first entry in this
   * array is the first axis to have a nonzero value; the second entry is the second to have a
   * nonzero value, etc. In other words, the entries should be ordered from changes-most-frequently
   * to changes-least-frequently. RewritableDatastores will automatically update this property as
   * images are added to the Datastore, if the property is not set manually. Other Datastore types
   * cannot change the SummaryMetadata as images are added; thus this property will need to be set
   * manually in those cases.
   *
   * @return Axis names in order of change rate.
   */
  List<String> getOrderedAxes();

  /**
   * @return - Array with Axis ordered as described for getOrderedAxes()
   * @deprecated - Use getOrdereddAxes() instead
   */
  @Deprecated
  String[] getAxisOrder();

  /**
   * The expected number of images along each axis that were to be collected. The actual dimensions
   * may differ if the acquisition was aborted partway through for any reason.
   *
   * @return expected number of images along each axis that were to be collected
   */
  Coords getIntendedDimensions();

  /**
   * For acquisitions with more than one Z slice, whether or not the shutter was left open in
   * between each slice.
   *
   * @return whether shutter was left open between Z slices.
   */
  Boolean getKeepShutterOpenSlices();

  /**
   * For acquisitions with more than one channel, whether or not the shutter was left open in
   * between each channel.
   *
   * @return whether shutter was left open between channels.
   */
  Boolean getKeepShutterOpenChannels();

  /**
   * The date and time at which the acquisition started
   *
   * @return date and time at which the acquisition started
   */
  String getStartDate();

  /**
   * The stage positions that were to be visited in the acquisition
   *
   * @return stage positions that were to be visited in the acquisition
   */
  List<MultiStagePosition> getStagePositionList();

  /**
   * @return see getStagePostionList()
   * @deprecated - Use getStagePositionList() instead
   */
  @Deprecated
  MultiStagePosition[] getStagePositions();

  /**
   * Any general-purpose user meta data
   *
   * @return Any general-purpose user meta data
   */
  PropertyMap getUserData();
}
