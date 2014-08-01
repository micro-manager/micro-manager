/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package MMCustomization;

import com.imaging100x.twophoton.SettingsDialog;
import com.imaging100x.twophoton.TwoPhotonControl;
import ij.IJ;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JOptionPane;
import mmcorej.CMMCore;
import mmcorej.TaggedImage;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.AcqControlDlg;
import org.micromanager.AcquisitionEngine2010;
import org.micromanager.MMStudio;
import org.micromanager.acquisition.AcquisitionManager;
import org.micromanager.acquisition.AcquisitionWrapperEngine;
import org.micromanager.acquisition.DefaultTaggedImageSink;
import org.micromanager.acquisition.MMImageCache;
import org.micromanager.api.IAcquisitionEngine2010;
import org.micromanager.api.ImageCache;
import org.micromanager.api.SequenceSettings;
import org.micromanager.api.TaggedImageStorage;
import org.micromanager.utils.JavaUtils;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.MMScriptException;
import org.micromanager.utils.ReportingUtils;

/**
 * Adapter class that sends commands in MM from acqWrapperEngine interface to
 * the real acquisition engine created within the plugin
 *
 * @author Henry
 */
public class AcquisitionWrapperEngineAdapter extends AcquisitionWrapperEngine {

    private IAcquisitionEngine2010 acqEngine_;
    private Preferences prefs_;
    private TwoPhotonControl twoPC_;
    private double initialZPosition_;
    private double focusOffset_ = 0;
    private CMMCore core_ = MMStudio.getInstance().getCore();
    private boolean runAutofocus_ = false;
    //most recent storage
    private TaggedImageStorage storage_;

    public AcquisitionWrapperEngineAdapter(TwoPhotonControl twoP, Preferences prefs) throws NoSuchFieldException {
        super((AcquisitionManager) JavaUtils.getRestrictedFieldValue(
                MMStudio.getInstance(), MMStudio.class, "acqMgr_"));
        MMStudio gui = MMStudio.getInstance();
        
        setCore(MMStudio.getInstance().getCore(), gui.getAutofocusManager());
        twoPC_ = twoP;

        prefs_ = prefs;
        acqEngine_ = new AcquisitionEngine2010(MMStudio.getInstance().getCore());

        try {
            gui.setAcquisitionEngine(this);
            //assorted things copied from MMStudio to ensure compatibility
            setParentGUI(gui);
            setZStageDevice(gui.getCore().getFocusDevice());
            setPositionList(gui.getPositionList());
            JavaUtils.setRestrictedFieldValue(gui.getAcqDlg(), AcqControlDlg.class, "acqEng_", this);
        } catch (Exception ex) {
            ReportingUtils.showError("Could't override acquisition engine");
        }
    }

    public boolean shouldAFRun() {
       boolean result = runAutofocus_;
       runAutofocus_ = false;
       return result;
    }
    
    public TaggedImageStorage getStorage() {
        return storage_;
    }
    
    public double getFocusOffset() {
        return focusOffset_;
    }
    
    public double setFocusOffset(double offset) {
       //image moves up = negative offset
        focusOffset_ = offset;
        try {
            //set new position (this is how the autofocus works)
            IJ.log("Setting Z position: " + (initialZPosition_ + focusOffset_));
            core_.setPosition(TwoPhotonControl.Z_STAGE, initialZPosition_ + focusOffset_);
        } catch (Exception e) {
            IJ.log("Couldnt set focus offset, initial z pos: " + initialZPosition_ + " focusOffset: " + focusOffset_);
        }
        return initialZPosition_ + focusOffset_;
    }
    
   private void addRunnables(SequenceSettings settings) {
      acqEngine_.clearRunnables();

      int numPositions = 1;
      if (settings.usePositionList) {
         try {
            //add position list runnables
            numPositions = MMStudio.getInstance().getPositionList().getPositions().length;
         } catch (MMScriptException ex) {
         }
      }
      //add runnable to execute at the end of each frame for running autofocus
      acqEngine_.attachRunnable(-1, numPositions - 1, -1, settings.slices.size() - 1, new Runnable() {
         @Override
         public void run() {
            runAutofocus_ = true;
         }
      });


        //add runnable to execute at the start of each frame
        //1) for turning off lasers at regular interval
        //2) for setting Z to account for focus drift
        for (int f = 0; f < settings.numFrames; f++) {
            final int frame = f;
            final int skipInterval = SettingsDialog.getEOM1SkipInterval();
            acqEngine_.attachRunnable(f, 0, 0, 0, new Runnable() {
                @Override
                public void run() {
                    //get Initial z position
                    if (frame == 0) {
                        try {
                            initialZPosition_ = core_.getPosition("Z");
                        } catch (Exception ex) {
                        }
                    } 
////                    else {
//                        try {
//                            focusOffset_ = initialZPosition_ - core_.getPosition("Z");
//                        } catch (Exception ex) {}
//                    }

                   //Set EOM on/off 
                   if (skipInterval != 1) {
                      try {
                         if (frame % skipInterval == 0) {
                            core_.setProperty("EOM1", "Block voltage", "false");
                         } else {
                            core_.setProperty("EOM1", "Block voltage", "true");
                         }
                      } catch (Exception e) {
                         ReportingUtils.showError("Couldn't change EOM voltage block");
                      }
                   }
                }
            });
        }
        
      //add runables to apply depth list settings--either by position or invariant
        int numPos = 0;
        try {
            numPos = gui_.getPositionList().getNumberOfPositions();
        } catch (Exception e) {
            ReportingUtils.showError("Couldnt get number of positions");
        }
      if (settings.usePositionList && numPos > 0) {
         //add position list runnables
         for (int p = 0; p < numPositions; p++) {
            final int posIndex = p;
            acqEngine_.attachRunnable(-1, p, -1, -1, new Runnable() {
               @Override
               public void run() {
                  twoPC_.applyDepthSetting(posIndex, -focusOffset_);
               }
            });
         }
      } else {
         //XY position invariant depth list runnable
         acqEngine_.attachRunnable(-1, -1, -1, -1, new Runnable() {
                @Override
                public void run() {
                    twoPC_.applyDepthSetting(-1, -focusOffset_);
                }
            });
        }
    }

    private void runAcquisition(SequenceSettings settings) throws Exception {
        focusOffset_ = 0;
        addRunnables(settings);
        //don't run autofocus before first position
        runAutofocus_  = false;

        // Start up the acquisition engine
        BlockingQueue<TaggedImage> engineOutputQueue = acqEngine_.run(settings, true, gui_.getPositionList(), 
                MMStudio.getInstance().getAutofocus());
        JSONObject summaryMetadata = acqEngine_.getSummaryMetadata();

        //write pixel overlap into metadata
        summaryMetadata.put("GridPixelOverlapX", SettingsDialog.getXOverlap());
        summaryMetadata.put("GridPixelOverlapY", SettingsDialog.getYOverlap());


        // Set up the DataProcessor<TaggedImage> sequence--no data processors for now
        // BlockingQueue<TaggedImage> procStackOutputQueue = ProcessorStack.run(engineOutputQueue, imageProcessors);

        // create storage     
        try {
            if (settings.save) {
                //MPTiff storage
                String acqDirectory = createAcqDirectory(summaryMetadata.getString("Directory"), summaryMetadata.getString("Prefix"));
                summaryMetadata.put("Prefix", acqDirectory);
                String acqPath = summaryMetadata.getString("Directory") + File.separator + acqDirectory;
                storage_ = new DoubleTaggedImageStorage(summaryMetadata, acqPath, prefs_);
            } else {
                //RAM storage
                storage_ = new DoubleTaggedImageStorage(summaryMetadata, null, prefs_);
            }

//            final int numChannels = MDUtils.getNumChannels(summaryMetadata);
//            final int numPositions = MDUtils.getNumPositions(summaryMetadata);
//            final int numSlices = MDUtils.getNumSlices(summaryMetadata);
            ImageCache imageCache = new MMImageCache(storage_) {
                @Override
                public JSONObject getLastImageTags() {
                    //So that display doesnt show a position scrollbar when imaging finished
                    JSONObject newTags = null;
                    try {
                        newTags = new JSONObject(super.getLastImageTags().toString());
                        MDUtils.setPositionIndex(newTags, 0);
                    } catch (JSONException ex) {
                        ReportingUtils.showError("Unexpected JSON Error");
                    }
                    return newTags;
                }
            };
            imageCache.setSummaryMetadata(summaryMetadata);


            DisplayPlus stitchedDisplay = new DisplayPlus(imageCache, this, summaryMetadata);

            DefaultTaggedImageSink sink = new DefaultTaggedImageSink(engineOutputQueue, imageCache);
            sink.start();


        } catch (IOException ex) {
            ReportingUtils.showError("Couldn't create image storage or start acquisition");
        }
    }

    protected String runAcquisition(SequenceSettings acquisitionSettings, AcquisitionManager acqManager) {
        //refresh GUI so that z returns to original position after acq
        MMStudio.getInstance().refreshGUI();
        if (acquisitionSettings.save) {
            File root = new File(acquisitionSettings.root);
            if (!root.canWrite()) {
                int result = JOptionPane.showConfirmDialog(null, "The specified root directory\n" + root.getAbsolutePath() + "\ndoes not exist. Create it?", "Directory not found.", JOptionPane.YES_NO_OPTION);
                if (result == JOptionPane.YES_OPTION) {
                    root.mkdirs();
                    if (!root.canWrite()) {
                        ReportingUtils.showError("Unable to save data to selected location: check that location exists.\nAcquisition canceled.");
                        return null;
                    }
                } else {
                    ReportingUtils.showMessage("Acquisition canceled.");
                    return null;
                }
            } else if (!this.enoughDiskSpace()) {
                ReportingUtils.showError("Not enough space on disk to save the requested image set; acquisition canceled.");
                return null;
            }
        }
        try {
            //run acquisition with our acquisition engine
            runAcquisition(acquisitionSettings);
        } catch (Exception ex) {
            ReportingUtils.showError("Probelem running acquisiton: " + ex.getMessage());
        }

        return "";
    }
    
        //Copied from MMAcquisition
    private String createAcqDirectory(String root, String prefix) throws Exception {
        File rootDir = JavaUtils.createDirectory(root);
        int curIndex = getCurrentMaxDirIndex(rootDir, prefix + "_");
        return prefix + "_" + (1 + curIndex);
    }

    private int getCurrentMaxDirIndex(File rootDir, String prefix) throws NumberFormatException {
        int maxNumber = 0;
        int number;
        String theName;
        for (File acqDir : rootDir.listFiles()) {
            theName = acqDir.getName();
            if (theName.startsWith(prefix)) {
                try {
                    //e.g.: "blah_32.ome.tiff"
                    Pattern p = Pattern.compile("\\Q" + prefix + "\\E" + "(\\d+).*+");
                    Matcher m = p.matcher(theName);
                    if (m.matches()) {
                        number = Integer.parseInt(m.group(1));
                        if (number >= maxNumber) {
                            maxNumber = number;
                        }
                    }
                } catch (NumberFormatException e) {
                } // Do nothing.
            }
        }
        return maxNumber;
    }

    @Override
    public void setPause(boolean pause) {
        if (pause) {
            acqEngine_.pause();
        } else {
            acqEngine_.resume();
        }
    }

    @Override
    public boolean isPaused() {
        return acqEngine_.isPaused();
    }

    @Override
    public boolean abortRequest() {
        if (isAcquisitionRunning()) {
            int result = JOptionPane.showConfirmDialog(null,
                    "Abort current acquisition task?",
                    "Micro-Manager", JOptionPane.YES_NO_OPTION,
                    JOptionPane.INFORMATION_MESSAGE);

            if (result == JOptionPane.YES_OPTION) {
                acqEngine_.stop();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isAcquisitionRunning() {
        return acqEngine_.isRunning();
    }

    @Override
    public long getNextWakeTime() {
        return acqEngine_.nextWakeTime();
    }

    @Override
    public boolean abortRequested() {
        return acqEngine_.stopHasBeenRequested();
    }

    @Override
    public boolean isFinished() {
        return acqEngine_.isFinished();
    }
}
