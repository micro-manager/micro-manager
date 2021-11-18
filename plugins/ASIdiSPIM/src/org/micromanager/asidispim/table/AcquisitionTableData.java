package org.micromanager.asidispim.table;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import org.micromanager.api.PositionList;
import org.micromanager.asidispim.Data.AcquisitionSettings;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * The class contains the data needed to run multiple acquisitions.<P>
 * 
 * The metadata field contains an ArrayList of AcquisitionMetadata objects in the order 
 * that the acquisitions will be run, and the AcquisitionTableTransferHandler manipulates 
 * this list to change the order.<P>
 * 
 * The settings field maps unique acquisition names to AcquisitionSettings objects, 
 * order doesn't matter because that is handled by the metadata field.<P>
 * 
 * The positions field maps unique position list names to PositionList objects, 
 * this field uses a LinkedHashMap to remember the insertion order, this allows 
 * the plugin to load json settings and retain the same order in the JList.<P>
 * 
 * The version field should be increased when the internal representation changes 
 * enough to impact loading json table data into the plugin.
 */
public class AcquisitionTableData {

    /**The initial capacity of the ArrayList and HashMaps. */
	private static final int DEFAULT_CAPACITY = 10;

	/**The version of the acquisition table data. */
	public final String version = "1.0.0";
	
    /**A list of metadata used by the acquisition table. */
    private final List<AcquisitionMetadata> metadata_;
    
    /**Maps unique acquisition names to acquisition settings. */
    private final HashMap<String, AcquisitionSettings> settings_;
    
    /**Maps unique position list names to position lists. */
    private final LinkedHashMap<String, PositionList> positions_;
    
    public AcquisitionTableData() {
        metadata_ = new ArrayList<AcquisitionMetadata>(DEFAULT_CAPACITY);
        settings_ = new HashMap<String, AcquisitionSettings>(DEFAULT_CAPACITY);
        positions_ = new LinkedHashMap<String, PositionList>(DEFAULT_CAPACITY);
    }
    
    /**
     * Removes all elements from the AcquisitionTableData object.
     */
    public void clearAllData() {
        metadata_.clear();
        settings_.clear();
        positions_.clear();
    }
    
    /**
     * Returns the number of acquisitions.
     * 
     * @return the number of acquisitions
     */
    public int getNumAcquisitions() {
    	return metadata_.size();
    }
    
    // === acquisition settings ===
    
    /**
     * Add an acquisition to the metadata list and settings HashMap.
     * 
     * @param name the unique acquisition name
     * @param acqSettings the settings object
     */
    public void addAcquisitionSettings(final String name, final AcquisitionSettings acqSettings) {
        metadata_.add(new AcquisitionMetadata(name));
        settings_.put(name, acqSettings);
    }
    
    /**
     * Remove an acquisition from the metadata list and settings HashMap.
     * 
     * @param name the unique acquisition name
     * @param index the index in the metadata list
     */
    public void removeAcquisitionSettings(final String name, final int index) {
        if (settings_.get(name) != null) {
            settings_.remove(name);
            metadata_.remove(index);
        }
    }

    /**
     * Rename the acquisition to the new name.
     * 
     * @param oldName the current name
     * @param newName the new name
     */
    public void renameAcquisitionSettings(final String oldName, final String newName) {
        final AcquisitionSettings acqSettings = settings_.remove(oldName);
        if (acqSettings != null) {
            settings_.put(newName, acqSettings);
        }
    }
    
    /**
     * Add a new AcquisitionSettings object to the settings HashMap.
     * 
     * @param name the unique acquisition name
     * @param acqSettings the settings object
     */
    public void updateAcquisitionSettings(final String name, final AcquisitionSettings acqSettings) {
        settings_.put(name, acqSettings);
    }
    
    /**
     * Returns the AcquisitionSettings given by the name argument.
     * 
     * @param name the unique acquisition settings name
     * @return the acquisition settings
     */
    public AcquisitionSettings getAcquisitionSettings(final String name) {
        return settings_.get(name);
    }
    
    // === position lists ===
    
    /**
     * Add a new PositionList to the positions LinkedHashMap.
     * 
     * @param name the unique position list name
     * @param positionList the position list object
     */
    public void addPositionList(final String name, final PositionList positionList) {
        positions_.put(name, positionList);
    }
    
    /**
     * Remove the PositionList from the positions LinkedHashMap.
     * 
     * @param name the unique position list name
     */
    public void removePositionList(final String name) {
        if (positions_.get(name) != null) {
            positions_.remove(name);
        }
    }

    /**
     * Rename the position list to the new name.
     * 
     * @param oldName the current name
     * @param newName the new name
     */
    public void renamePositionList(final String oldName, final String newName) {
        final PositionList positionList = positions_.remove(oldName);
        if (positionList != null) {
            positions_.put(newName, positionList);
        }
    }
    
    /**
     * Returns an unordered array of all position list names.
     * 
     * @return an unordered array of all position list names
     */
    public String[] getAcquisitionNames() {
        final Set<String> keys = settings_.keySet();
        return keys.toArray(new String[keys.size()]);
    }
    
//    public String[] getAcquisitionNamesOrder() {
//        final int size = metadata.size();
//        final String[] names = new String[size];
//        for (int i = 0; i < size; i++) {
//            names[i] = metadata.get(i).acquisitionName;
//        }
//        return names;
//    }
    
    /**
     * Returns array of all acquisition settings names.
     * 
     * @return an array of all acquisition settings names.
     */
    public String[] getPositionListNames() {
        final Set<String> keys = positions_.keySet();
        return keys.toArray(new String[keys.size()]);
    }
    
    // === acquisition metadata ===
    
    public List<AcquisitionMetadata> getMetadataList() {
        return metadata_;
    }
    
    // TODO: error checking
    public AcquisitionMetadata getMetadataByIndex(final int index) {
        return metadata_.get(index);
    }

    // === save & load data ===
    
    /**
     * Convert the AcquisitionTableData to a json String.
     * 
     * @param prettyPrint true to pretty print the json
     * @return a json String
     */
    public String toJson(boolean prettyPrint) {
        if (prettyPrint) {
            final Gson gson = new GsonBuilder().setPrettyPrinting().create();
            return gson.toJson(this);
        } else {
            return new Gson().toJson(this);
        }
    }
    
    /**
     * Returns a AcquisitionTableData instance constructed from a json String.
     * 
     * @param json a json String
     * @return an AcquisitionTableData instance
     */
    public static AcquisitionTableData fromJson(final String json) {
        return new Gson().fromJson(json, AcquisitionTableData.class);
    }
    
    /**
     * Returns true if the acquisition name exists in the table data.
     * 
     * @param name the name to check
     * @return true if it exists
     */
    public boolean acqNameExists(final String name) {
        return (settings_.get(name) != null) ? true : false;
    }
    
    /**
     * Returns the size of the positions field.
     * 
     * @return the size of positions
     */
    public int getPositionsSize() {
        return positions_.size();
    }
    
    /**
     * Returns the size of the settings field.
     * 
     * @return the size of settings
     */
    public int getAcquisitionsSize() {
        return settings_.size();
    }
    
    /**
     * Returns the PositionList given by the name argument.
     * 
     * @param name the unique position list name
     * @return the position list
     */
    public PositionList getPositionList(final String name) {
        return positions_.get(name);
    }
    
    /**
     * Sets the positionListName in the metadata, used when selecting values 
     * with the AcquistionTablePositionList.
     * 
     * @param row the selected row
     * @param name the new name
     */
    public void setMetadataPositionListName(final int row, final String name) {
        final AcquisitionMetadata data = metadata_.get(row);
        data.setPositionListName(name);
    }
    
    // -----------
    // Debug Tools
    // -----------
    
    public void printMetadataNames() {
        report("Metadata Acq Names =>");
        for (final AcquisitionMetadata data : metadata_) {
            report(data.getAcquisitionName());
        }
    }
    
    public void printPositionNames() {
        report("PositionList HashMap Keys =>");
        for (final String name : positions_.keySet()) {
            report(name);
        }
    }
    
    public void printSettingNames() {
        report("AcquisitionSettings HashMap Keys =>");
        for (final String name : settings_.keySet()) {
            report(name);
        }
    }
    
    /**
     * A useful method for validating that everything is in sync between the metadata and
     * settings fields. There should be a key in the settings HashMap that corresponds 
     * to each acquistionName field in the metadata ArrayList.
     * 
     * @return true if everything is okay
     */
    public boolean validateKeysWithMetadata() {
        // validate size
        final int size = metadata_.size();
        if (size != settings_.size()) {
            report("Validate Error: Size of metadata does not equal size of settings.");
            return false;
        }
        // validate keys
        for (final AcquisitionMetadata data : metadata_) {
            final String name = data.getAcquisitionName();
            if (settings_.get(name) == null) {
                report("Validate Error: Missing key \"" + name + "\" in settings HashMap.");
                return false;
            }
        }
        report("Validate Success: Metadata and settings are in sync.");
        return true;
    }
    
    // you can change this method to report however you like (log file, etc)
    private void report(final String message) {
        System.out.println(message);
    }
    
}
