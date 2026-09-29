package org.micromanager.acquisition.internal.acqengjcompat.multimda;

import java.io.File;
import org.micromanager.PositionList;
import org.micromanager.Studio;
import org.micromanager.acquisition.SequenceSettings;

/**
 * Data structure to remember the AcqSettings file, as well as
 * PositionList.
 *
 * <p>Note: the acqSettings_.usePositionList() flag is always derived from whether a
 * position list with positions is currently loaded for this row; it is never taken
 * directly from a user toggle. Both setPositionListFile() and setAcqSettings()
 * re-derive it so the GUI explanation and the executor stay consistent.
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
         if (positionList.getNumberOfPositions() > 0) {
            positionList_ = positionList;
            positionListFile_ = positionListFile;
         } else {
            // An empty list is treated the same as "no position list": keep both
            // references null so the UI shows "current position" instead of a filename
            // that would not actually be used at run time.
            positionList_ = null;
            positionListFile_ = null;
         }
         // A separate position list file does not flip the usePositionList flag that
         // came from the acquisition settings file, so set it here based on the loaded
         // list. Both the GUI explanation and the executor read this flag.
         acqSettings_ = new SequenceSettings.Builder(acqSettings_)
               .usePositionList(positionList_ != null).build();
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
