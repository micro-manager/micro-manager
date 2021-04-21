///////////////////////////////////////////////////////////////////////////////
// PROJECT:       Micro-Manager
// SUBSYSTEM:     mmstudio
// -----------------------------------------------------------------------------
//
// DESCRIPTION:  Container for the scanning pattern
//
// AUTHOR:       Nenad Amodaj, nenad@amodaj.com, March 8, 2007
//
// COPYRIGHT:    University of California, San Francisco, 2007
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
// CVS:          $Id: PositionList.java 10970 2013-05-08 16:58:06Z nico $
//
package org.micromanager;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.micromanager.data.internal.PropertyKey;
import org.micromanager.internal.propertymap.NonPropertyMapJSONFormats;

/** Navigation list of positions for the Stages. Used for multi site acquisition support. */
public class PositionList implements Iterable<MultiStagePosition> {
  private final ArrayList<MultiStagePosition> positions_;

  private final HashSet<ChangeListener> listeners_ = new HashSet<ChangeListener>();

  /** Constructor of navigation list of positions for the Stages. */
  public PositionList() {
    positions_ = new ArrayList<MultiStagePosition>();
  }

  /**
   * This looks like a static copy constructor
   *
   * @param aPl input positionlist
   * @return new instance of a PositionList with identical positions to the input
   */
  public static PositionList newInstance(PositionList aPl) {
    PositionList pl = new PositionList();
    Iterator<MultiStagePosition> it = aPl.positions_.iterator();
    while (it.hasNext()) pl.addPosition(MultiStagePosition.newInstance(it.next()));
    return pl;
  }

  /**
   * Adds a changeListener to this PositionList
   *
   * @param listener Will fire when the list changes
   */
  public void addChangeListener(ChangeListener listener) {
    listeners_.add(listener);
  }

  /**
   * Removes the given listener Nothing will happen if listener was not added first
   *
   * @param listener
   */
  public void removeChangeListener(ChangeListener listener) {
    listeners_.remove(listener);
  }

  /** Notifies all changeListeners to the list that something changed */
  public void notifyChangeListeners() {
    for (ChangeListener listener : listeners_) {
      listener.stateChanged(new ChangeEvent(this));
    }
  }

  /**
   * Returns multi-stage position associated with the position index.
   *
   * @param idx - position index
   * @return multi-stage position
   */
  public MultiStagePosition getPosition(int idx) {
    if (idx < 0 || idx >= positions_.size()) return null;

    return positions_.get(idx);
  }

  /**
   * Returns a copy of the multi-stage position associated with the position index.
   *
   * @param idx - position index
   * @return multi-stage position
   */
  public MultiStagePosition getPositionCopy(int idx) {
    if (idx < 0 || idx >= positions_.size()) return null;

    return MultiStagePosition.newInstance(positions_.get(idx));
  }

  /**
   * Returns position index associated with the position name.
   *
   * @param posLabel - label (name) of the position
   * @return index, or -1 when the name was not found
   */
  public int getPositionIndex(String posLabel) {
    for (int i = 0; i < positions_.size(); i++) {
      if (positions_.get(i).getLabel().compareTo(posLabel) == 0) return i;
    }
    return -1;
  }

  /**
   * Adds a new position to the list.
   *
   * @param pos - multi-stage position
   */
  public void addPosition(MultiStagePosition pos) {
    String label = pos.getLabel();
    if (!isLabelUnique(label)) {
      pos.setLabel(generateLabel(label));
    }
    positions_.add(pos);
    notifyChangeListeners();
  }

  /**
   * Insert a position into the list.
   *
   * @param in0 - place in the list where the position should be inserted
   * @param pos - multi-stage position
   */
  public void addPosition(int in0, MultiStagePosition pos) {
    String label = pos.getLabel();
    if (!isLabelUnique(label)) {
      pos.setLabel(generateLabel(label));
    }
    positions_.add(in0, pos);
    notifyChangeListeners();
  }

  /**
   * Replaces position in the list with the new position
   *
   * @param index index of the position to be replaced
   * @param pos - multi-stage position
   */
  public void replacePosition(int index, MultiStagePosition pos) {
    if (index >= 0 && index < positions_.size()) {
      positions_.set(index, pos);
      notifyChangeListeners();
    }
  }

  /**
   * Returns the number of positions contained within the list
   *
   * @return number of positions contained in the position list
   */
  public int getNumberOfPositions() {
    return positions_.size();
  }

  /** Empties the list. */
  public void clearAllPositions() {
    positions_.clear();
    notifyChangeListeners();
  }

  /**
   * Removes a specific position based on the index
   *
   * @param idx - position index
   */
  public void removePosition(int idx) {
    if (idx >= 0 && idx < positions_.size()) {
      positions_.remove(idx);
    }
    notifyChangeListeners();
  }

  /**
   * Initialize the entire array by passing an array of multi-stage positions
   *
   * @param posArray - array of multi-stage positions
   */
  public void setPositions(MultiStagePosition[] posArray) {
    positions_.clear();
    positions_.addAll(Arrays.asList(posArray));
    notifyChangeListeners();
  }

  /**
   * Returns an array of positions contained in the list.
   *
   * @return position array
   */
  public MultiStagePosition[] getPositions() {
    MultiStagePosition[] list = new MultiStagePosition[positions_.size()];
    for (int i = 0; i < positions_.size(); i++) {
      list[i] = positions_.get(i);
    }

    return list;
  }

  /**
   * Assigns a label to the position index
   *
   * @param idx - position index
   * @param label - new label (name)
   */
  public void setLabel(int idx, String label) {
    if (idx < 0 || idx >= positions_.size()) return;

    positions_.get(idx).setLabel(label);
    notifyChangeListeners();
  }

  /**
   * Creates a PropertyMap from this PositionList
   *
   * @return PropertyMap representing this PositionList
   */
  public PropertyMap toPropertyMap() {
    List<PropertyMap> msps = new ArrayList<PropertyMap>();
    for (MultiStagePosition msp : positions_) {
      msps.add(msp.toPropertyMap());
    }
    return PropertyMaps.builder()
        .putPropertyMapList(PropertyKey.STAGE_POSITIONS.key(), msps)
        .build();
  }

  /**
   * replaces the content of this PositionList with the content of the given PropertyMap
   *
   * @param map PropertyMap whose content will replace this PositionList
   * @throws IOException
   */
  public void replaceWithPropertyMap(PropertyMap map) throws IOException {
    positions_.clear();
    if (!map.containsPropertyMapList(PropertyKey.STAGE_POSITIONS.key())) {
      notifyChangeListeners();
      return;
    }
    for (PropertyMap mspMap : map.getPropertyMapList(PropertyKey.STAGE_POSITIONS.key())) {
      MultiStagePosition msp = MultiStagePosition.fromPropertyMap(mspMap);
      if (mspMap.containsKey(PropertyKey.MULTI_STAGE_POSITION__PROPERTIES.key())) {
        PropertyMap propertyMap =
            mspMap.getPropertyMap(PropertyKey.MULTI_STAGE_POSITION__PROPERTIES.key(), null);
        for (String key : propertyMap.keySet()) {
          String val = propertyMap.getString(key, "");
          msp.setProperty(key, val);
        }
      }
      positions_.add(msp);
    }
    notifyChangeListeners();
  }

  /**
   * Helper method to generate unique label when inserting a new position. Not recommended for use -
   * planned to become obsolete.
   *
   * @return Unique label
   */
  public String generateLabel() {
    return generateLabel("Pos");
  }

  /**
   * Generates a label for a new Position
   *
   * @param proposal user-supplied suggestion
   * @return new, unique label that will be accepted by this list
   */
  public String generateLabel(String proposal) {
    String label = proposal + positions_.size();

    // verify the uniqueness
    int i = 1;
    while (!isLabelUnique(label)) {
      label = proposal + (positions_.size() + i++);
    }

    return label;
  }

  /**
   * Verify that the new label is unique
   *
   * @param label - proposed label
   * @return true if label does not exist
   */
  public boolean isLabelUnique(String label) {
    for (MultiStagePosition positions_1 : positions_) {
      if (positions_1.getLabel().compareTo(label) == 0) {
        return false;
      }
    }
    return true;
  }

  /**
   * Save list to a file.
   *
   * @param file destination; recommended suffix is ".json".
   * @throws IOException
   */
  public void save(File file) throws IOException {
    toPropertyMap().saveJSON(file, true, true);
  }

  /**
   * Save list to a file.
   *
   * @param path destination; recommended suffix is ".json".
   * @throws IOException
   */
  public void save(String path) throws IOException {
    save(new File(path));
  }

  /**
   * Load position list from a file.
   *
   * @param file source JSON file, usually ".txt" or ".json".
   * @throws IOException
   */
  public void load(File file) throws IOException {
    String text = Files.toString(file, Charsets.UTF_8);
    PropertyMap pmap;
    try {
      pmap = PropertyMaps.fromJSON(text);
    } catch (IOException e) {
      pmap = NonPropertyMapJSONFormats.positionList().fromJSON(text);
    }
    replaceWithPropertyMap(pmap);

    notifyChangeListeners();
  }

  /**
   * Load position list from a file.
   *
   * @param path source JSON file, usually ".txt" or ".json".
   * @throws IOException
   */
  public void load(String path) throws IOException {
    load(new File(path));
  }

  @Override
  public Iterator<MultiStagePosition> iterator() {
    return new PosListIterator(this);
  }

  /**
   * Provides an iterator for a PositionList. The remove() method is not implemented. You can access
   * this iterator by calling the iterator() method of PositionList, or by trying to iterator over
   * it.
   */
  public static class PosListIterator implements Iterator<MultiStagePosition> {
    private final PositionList list_;
    private int index_ = 0;

    public PosListIterator(PositionList list) {
      list_ = list;
    }

    @Override
    public boolean hasNext() {
      return index_ < list_.getNumberOfPositions();
    }

    @Override
    public MultiStagePosition next() throws NoSuchElementException {
      MultiStagePosition result = list_.getPosition(index_);
      if (result == null) {
        throw new NoSuchElementException();
      }
      index_++;
      return result;
    }

    @Override
    public void remove() {
      // Not implemented.
    }
  }
}
