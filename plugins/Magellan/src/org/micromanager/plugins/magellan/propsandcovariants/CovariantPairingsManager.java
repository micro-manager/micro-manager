///////////////////////////////////////////////////////////////////////////////
// AUTHOR:       Henry Pinkard, henry.pinkard@gmail.com
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
//
package propsandcovariants;

import acq.MultipleAcquisitionManager;
import gui.GUI;
import java.awt.FileDialog;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileNameExtensionFilter;
import main.Magellan;
import misc.GlobalSettings;
import misc.JavaUtils;
import misc.Log;
import surfacesandregions.SurfaceInterpolator;
import surfacesandregions.SurfaceManager;

/**
 *
 * Manager class for an unsorted list of paired values
 */
public class CovariantPairingsManager {

    private static final String COV_DIR = "COVARIANT_LOADING_DIR";
    private ArrayList<CovariantPairing> pairs_ = new ArrayList<CovariantPairing>();
    private MultipleAcquisitionManager multiAcqManager_;
    private static CovariantPairingsManager singleton_;
    private GUI gui_;
    private CovariantPairingsTableModel pairingsTableModel_;
    private CovariantPairValuesTableModel valuesTableModel_;

    public CovariantPairingsManager(GUI gui, MultipleAcquisitionManager multiAcqManager) {
        multiAcqManager_ = multiAcqManager;
        singleton_ = this;
        gui_ = gui;
    }

    public static CovariantPairingsManager getInstance() {
        return singleton_;
    }

    public boolean isPairActiveForCurrentAcq(int index) {
        return gui_.getActiveAcquisitionSettings().hasPairing(pairs_.get(index));
    }

    public void enablePairingForCurrentAcq(int index, boolean enable) {
        if (enable) {
            gui_.getActiveAcquisitionSettings().addPropPairing(pairs_.get(index));
        } else {
            gui_.getActiveAcquisitionSettings().removePropPairing(pairs_.get(index));
        }
        gui_.acquisitionSettingsChanged();
    }

    public void deleteValuePair(int pairingIndex, int valueIndex) {
        pairs_.get(pairingIndex).deleteValuePair(valueIndex);
    }

    public void addPair(CovariantPairing pair) {
        pairs_.add(pair);
        //enable this pair for the acquisitiuon currently showing
        enablePairingForCurrentAcq(pairs_.size() - 1, true);
        pairingsTableModel_.fireTableDataChanged();
        gui_.selectNewCovariantPair();
    }

    public void deletePair(CovariantPairing pair) {
        //Remove from all acquisiton settings that have a reference
        for (int i = 0; i < multiAcqManager_.getSize(); i++) {
            multiAcqManager_.getAcquisitionSettings(i).removePropPairing(pair);
        }
        //now remove from the list of pairings
        pairs_.remove(pair);
        pairingsTableModel_.fireTableDataChanged();
        //make sure values table gets updated if deleting last pair
    }

    public CovariantPairing getPair(int index) {
        return index < pairs_.size() ? pairs_.get(index) : null;
    }

    public int getNumPairings() {
        return pairs_.size();
    }

    public void registerCovariantPairingsTableModel(CovariantPairingsTableModel model) {
        pairingsTableModel_ = model;
    }

    public void registerCovariantValuesTableModel(CovariantPairValuesTableModel model) {
        valuesTableModel_ = model;
    }

    public void loadPairingsFile(GUI gui) {

        File selectedFile = null;
        if (JavaUtils.isMac()) {
            FileDialog fd = new FileDialog(gui, "Save covariant pairing values", FileDialog.LOAD);
            fd.setFilenameFilter(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return name.endsWith(".txt") || name.endsWith(".TXT");
                }
            });
            fd.setVisible(true);
            if (fd.getFile() != null) {
                selectedFile = new File(fd.getDirectory() + File.separator + fd.getFile());
                selectedFile = new File(selectedFile.getAbsolutePath());
            }
            fd.dispose();
        } else {
            JFileChooser fc = new JFileChooser(GlobalSettings.getInstance().getStringInPrefs(COV_DIR, null));
            fc.setFileFilter(new FileNameExtensionFilter("Text file", "txt", "TXT"));
            fc.setDialogTitle("Save covariant pairing values");
            int returnVal = fc.showSaveDialog(gui);
            if (returnVal == JFileChooser.APPROVE_OPTION) {
                selectedFile = fc.getSelectedFile();
            }
        }
        if (selectedFile == null) {
            return; //canceled
        }
        //store directory
        GlobalSettings.getInstance().storeStringInPrefs(COV_DIR, selectedFile.getParent().toString());

        String fileContents = "";
        FileReader reader;
        try {
            reader = new FileReader(selectedFile);
        } catch (IOException ex) {
            Log.log("Problem opening file");
            return;
        }
        BufferedReader br = new BufferedReader(reader);
        try {
            StringBuilder sb = new StringBuilder();
            String line = br.readLine();
            while (line != null) {
                sb.append(line);
                sb.append("\n");
                line = br.readLine();
            }
            fileContents = sb.toString();
            br.close();
        } catch (IOException e) {
            Log.log("Problem reading file", true);
        }
        //Read file and reconstruct covariants
        for (String pairingText : fileContents.split("\n\n")) { //for each pairing
            String[] lines = pairingText.trim().split("\n");
            String independentName = lines[0].split(",")[0];
            String dependentName = lines[0].split(",")[1];
            //Generate covariants from names
            //Groups and surface data have specific prefixes. No prefix = property
            if (independentName.startsWith(SurfaceData.PREFIX)) {
                SurfaceDataImportDialog importer = new SurfaceDataImportDialog(null, false);
                String type = importer.getSurfaceDataType();
                if (type == null) {
                    continue; // user canceled
                }
                String[] surfnames = importer.getSurfaceNames();
                for (String name : surfnames) {

                    SurfaceInterpolator surface = SurfaceManager.getInstance().getSurfaceNamed(name);
                    if (surface == null) {
                        Log.log("Expected surface null", true);
                        throw new RuntimeException();
                    }
                    try {
                        SurfaceData ind = new SurfaceData(surface, type);
                        ind.initializeCurvedSurfaceData();
                        //Get reverse pairing (EOM voltage -> Relative power) to convert entered base EOM to bae power
                        CovariantPairing reversePairing = createCovariantAndAddValues(ind, dependentName, lines);
                        ind.setBasePowerFromBaseVoltage(reversePairing);
                    } catch (Exception e) {
                        e.printStackTrace();
                        Log.log("Expected type wrong", true);
                        throw new RuntimeException();
                    }
                }
            } else {
                try {
                    createCovariantAndAddValues(initCovariantFromString(independentName), dependentName, lines);
                } catch (Exception ex) {
                    continue;//problem with pairing, onto next
                }
            }
        }
    }

    private CovariantPairing createCovariantAndAddValues(Covariant independent, String dependentName, String[] lines) throws Exception {
        Covariant dependent = initCovariantFromString(dependentName);
        CovariantPairing pairing = new CovariantPairing(independent, dependent);
        CovariantPairing reversePairing = new CovariantPairing(independent, dependent);
        for (int i = 1; i < lines.length; i++) {
            String[] vals = lines[i].split(",");
            CovariantValue iVal = independent.getType() == CovariantType.STRING ? new CovariantValue(vals[0])
                    : independent.getType() == CovariantType.DOUBLE ? new CovariantValue(Double.parseDouble(vals[0]))
                    : new CovariantValue(Integer.parseInt(vals[0]));
            CovariantValue dVal = dependent.getType() == CovariantType.STRING ? new CovariantValue(vals[1])
                    : dependent.getType() == CovariantType.DOUBLE ? new CovariantValue(Double.parseDouble(vals[1]))
                    : new CovariantValue(Integer.parseInt(vals[1]));
            pairing.addValuePair(iVal, dVal);
            reversePairing.addValuePair(dVal, iVal);
        }
        this.addPair(pairing);
        return reversePairing;
    }

    private Covariant initCovariantFromString(String covariantName) throws Exception {
        Covariant cov;
        if (covariantName.startsWith(SinglePropertyOrGroup.GROUP_PREFIX)) {
            cov = new SinglePropertyOrGroup();
            String groupName = covariantName.substring(SinglePropertyOrGroup.GROUP_PREFIX.length());
            //check that group exists
            if (!Arrays.asList(Magellan.getCore().getAvailableConfigGroups().toArray()).contains(groupName)) {
                JOptionPane.showMessageDialog(null, "Group: \"" + groupName + "\"is not present in current config and will not be loaded");
                throw new Exception();
            }
            //group exists, initialize its representative object
            ((SinglePropertyOrGroup) cov).readGroupValuesFromConfig(groupName);
        } else if (covariantName.startsWith(SurfaceData.PREFIX)) {
            throw new RuntimeException(); //handled elsewhere 
        } else {
            //its a property
            cov = new SinglePropertyOrGroup();
            String device = covariantName.split("-")[0];
            String propName = covariantName.split("-")[1];
            if (!Magellan.getCore().hasProperty(device, propName)) {
                JOptionPane.showMessageDialog(null, "Cannot locate property: \"" + covariantName + "\". Skipping covariant pairing");
                throw new Exception();
            }
            ((SinglePropertyOrGroup) cov).readFromCore(device, propName, false);
        }
        return cov;
    }

    public void saveAllPairings(GUI gui) {
        File selectedFile = null;
        if (JavaUtils.isMac()) {
            FileDialog fd = new FileDialog(gui, "Save covariant pairing values", FileDialog.SAVE);
            fd.setVisible(true);
            if (fd.getFile() != null) {
                selectedFile = new File(fd.getDirectory() + File.separator + fd.getFile());
                selectedFile = new File(selectedFile.getAbsolutePath() + ".txt");
            }
            fd.dispose();
        } else {
            JFileChooser fc = new JFileChooser();
            fc.setDialogTitle("Save covariant pairing values");
            int returnVal = fc.showSaveDialog(gui);
            if (returnVal == JFileChooser.APPROVE_OPTION) {
                selectedFile = fc.getSelectedFile();
            }
        }

        if (selectedFile == null) {
            return; //canceled
        }
        String name = selectedFile.getName();
        if (!name.endsWith(".txt")) {
            name += ".txt";
        }
        selectedFile = new File(new File(selectedFile.getParent()).getPath() + File.separator + name);

        if (selectedFile.exists()) {
            int reply = JOptionPane.showConfirmDialog(null, "OVerwrite exisitng file?", "Confirm overwrite", JOptionPane.YES_NO_OPTION);
            if (reply == JOptionPane.NO_OPTION) {
                return;
            }
            selectedFile.delete();
        }

        try {
            selectedFile.createNewFile();
            FileWriter writer = new FileWriter(selectedFile);
            for (CovariantPairing pairing : pairs_) {
                writer.write(pairing.getIndependentName(false) + "," + pairing.getDependentName(false) + "\n");
                for (int i = 0; i < pairing.getNumPairings(); i++) {
                    writer.write(pairing.getValue(0, i).toString() + "," + pairing.getValue(1, i).toString() + "\n");
                }
                writer.write("\n\n");
            }
            writer.flush();
            writer.close();
        } catch (IOException ex) {
            Log.log("Couldn't write file");
            return;
        }
    }

    public void surfaceorRegionNameChanged() {
        pairingsTableModel_.fireTableDataChanged();
    }

    public void updatePairingNames() {
        pairingsTableModel_.fireTableDataChanged();
        valuesTableModel_.fireTableDataChanged();
    }

    public void deletePairsReferencingSurface(SurfaceInterpolator aThis) {
        //surface has been removed, so remove all pairings that refernece it
        ArrayList<CovariantPairing> toRemove = new ArrayList<CovariantPairing>();
        for (CovariantPairing pair : pairs_) {
            if (pair.getIndependentCovariant() instanceof SurfaceData
                    && ((SurfaceData) pair.getIndependentCovariant()).getSurface() == aThis) {
                toRemove.add(pair);
            }
        }
        for (CovariantPairing pair : toRemove) {
            deletePair(pair);
        }
    }
}
