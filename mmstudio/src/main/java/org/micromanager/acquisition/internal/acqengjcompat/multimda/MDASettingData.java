package org.micromanager.acquisition.internal.acqengjcompat.multimda;

import java.io.File;
import org.micromanager.PositionList;
import org.micromanager.Studio;
import org.micromanager.acquisition.SequenceSettings;

/**
 * Data structure to remember the AcqSettings file, as well as
 * PositionList.
 */
public class MDASettingData {
   private final Studio studio_;
   private File acqSettingFile_;
   private SequenceSettings acqSettings_;
   private File positionListFile_;
   private PositionList positionList_;
   private String presetGroup_;
   private String presetName_;

   public MDASettingData(Studio studio, File acqSettingFile, SequenceSettings acqSettings) {
      studio_ = studio;
      acqSettingFile_ = acqSettingFile;
      acqSettings_ = acqSettings;
   }

   public void setPositionListFile(File positionListFile) {
      // Load into a temporary list so a failed load does not leave a stale
      // positionListFile_ around (which would otherwise be persisted on shutdown
      // and keep the UI showing the old filename even though positions are disabled).
      PositionList positionList = new PositionList();
      try {
         positionList.load(positionListFile);
         positionList_ = positionList;
         positionListFile_ = positionListFile;
         // A separate position list file does not flip the usePositionList flag that
         // came from the acquisition settings file, so set it here based on the loaded
         // list. Both the GUI explanation and the executor read this flag.
         acqSettings_ = new SequenceSettings.Builder(acqSettings_)
               .usePositionList(positionList_.getNumberOfPositions() > 0).build();
      } catch (Exception e) {
         positionList_ = null;
         positionListFile_ = null;
         acqSettings_ = new SequenceSettings.Builder(acqSettings_)
               .usePositionList(false).build();
         studio_.logs().showError(e);
      }
   }

   public File getPositionListFile() {
      return positionListFile_;
   }

   public PositionList getPositionList() {
      return positionList_;
   }

   public SequenceSettings getSequenceSettings() {
      return acqSettings_;
   }

   public File getAcqSettingFile() {
      return acqSettingFile_;
   }

   public void setAcqSettings(File acqFile, SequenceSettings acqSettings) {
      acqSettingFile_ = acqFile;
      // A newly loaded acquisition settings file would clobber the usePositionList flag,
      // so re-apply it based on whether a position list is currently loaded for this row.
      acqSettings_ = new SequenceSettings.Builder(acqSettings)
            .usePositionList(positionList_ != null
                  && positionList_.getNumberOfPositions() > 0).build();
   }

   public void setPresetGroup(String presetGroup) {
      presetGroup_ = presetGroup;
   }

   public String getPresetGroup() {
      return presetGroup_;
   }

   public void setPresetName(String presetName) {
      presetName_ = presetName;
   }

   public String getPresetName() {
      return presetName_;
   }

}
