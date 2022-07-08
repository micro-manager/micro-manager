///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// DESCRIPTION:  Generalized mechanical position implementation - using multiple
//               stages.
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
// CVS:          $Id: MultiStagePosition.java 10143 2012-10-18 19:02:04Z nico $
//

package org.micromanager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import mmcorej.CMMCore;
import org.micromanager.data.internal.PropertyKey;
import org.micromanager.internal.utils.ReportingUtils;

/**
 * Definition of a position in space in terms of available stages/drives.
 *
 * <p>The current implementation uses the concept of "DefaultXYStage", and
 * "DefaultZStage".  This is problematic.  The concept of "default" stages
 * originates in the Micro-Manager core, that always has only 1 stage that is
 * the "active" one.  However, MultiStagePosition devices can record the position
 * of multiple stages, sometimes even lacking those of the "default" stages,
 * so it can not rely on the Micro-manager "Default" stages being in the list.</p>
 *
 * <p>It would be nice to be able to rely on the getX, getY, and getZ functions,
 * but these will return bogus values if the system's default stages are not
 * included.  To avoid surprises, you may need to parse the MultiStagePosition
 * yourself.</p>
 */
public final class MultiStagePosition {
   private final ArrayList<StagePosition> stagePosList_;
   private String label_;
   private String defaultZStage_;
   private String defaultXYStage_;
   private int gridRow_ = 0;
   private int gridCol_ = 0;
   private final Map<String, String> properties_;

   /**
    * Default constructor.
    */
   public MultiStagePosition() {
      stagePosList_ = new ArrayList<>();
      label_ = "Undefined";
      defaultZStage_ = "";
      defaultXYStage_ = "";
      properties_ = new HashMap<>();
   }

   /**
    * Convenient constructor where the motion system consists of one XY stage and one focus stage.
    *
    * @param xyStage name
    * @param x       - coordinate in microns
    * @param y       - coordinate in microns
    * @param zStage  name
    * @param z       - focus position in microns
    */
   public MultiStagePosition(String xyStage, double x, double y, String zStage, double z) {
      this();

      // create and add xy position
      StagePosition xyPos = StagePosition.create2D(xyStage, x, y);
      defaultXYStage_ = xyStage;
      add(xyPos);

      // create and add z position
      StagePosition zPos = StagePosition.create1D(zStage, z);
      defaultZStage_ = zStage;
      add(zPos);
   }

   /**
    * Copy constructor.
    *
    * @param aMps - another instance of the MultiStagePoswition class
    * @return multistageposition
    */
   public static MultiStagePosition newInstance(MultiStagePosition aMps) {
      MultiStagePosition mps = new MultiStagePosition();
      mps.label_ = aMps.label_;
      mps.defaultXYStage_ = aMps.defaultXYStage_;
      mps.defaultZStage_ = aMps.defaultZStage_;
      mps.properties_.putAll(aMps.properties_);
      for (StagePosition sp : aMps.stagePosList_) {
         mps.add(StagePosition.newInstance(sp));
      }
      return mps;
   }

   /**
    * Add one stage position point.
    *
    * @param sp Stageposition to be added
    */
   public void add(StagePosition sp) {
      stagePosList_.add(sp);
   }

   /**
    * Removed one stage position point.
    *
    * @param sp stage position point to be removed
    */
   public void remove(StagePosition sp) {
      stagePosList_.remove(sp);
   }

   /**
    * Number of stages.
    *
    * @return number of stages in this MultiStagePosition object
    */
   public int size() {
      return stagePosList_.size();
   }

   /**
    * Return stage position based on index.
    *
    * @param idx - position index
    * @return stageposition
    */
   public StagePosition get(int idx) {
      return stagePosList_.get(idx);
   }

   /**
    * Returns position for a specific stage.
    *
    * @param stageName Name of the stage
    * @return position of the specified stage
    */
   public StagePosition get(String stageName) {
      for (StagePosition sp : stagePosList_) {
         if (sp.getStageDeviceLabel().compareTo(stageName) == 0) {
            return sp;
         }
      }
      return null;
   }

   /**
    * Add a generalized property-value par to the position.
    *
    * @param key   Key of the property
    * @param value Value of the property
    */
   public void setProperty(String key, String value) {
      properties_.put(key, value);
   }

   /**
    * Return the array of property keys (names) associated with this position.
    *
    * @return array with property names
    */
   public String[] getPropertyNames() {
      return new ArrayList<>(properties_.keySet()).toArray(new String[properties_.size()]);
   }

   /**
    * Checks if the position has a particular property.
    *
    * @param key Key or property
    * @return true if the position has this key
    */
   public boolean hasProperty(String key) {
      return properties_.containsKey(key);
   }

   /**
    * Returns property value for a given key (name).
    *
    * @param key Key of the property
    * @return value associated with the key
    */
   public String getProperty(String key) {
      if (properties_.containsKey(key)) {
         return properties_.get(key);
      }
      return null;
   }

   /**
    * Returns position label.
    *
    * @return label associated with this MultiStagePosition
    */
   public String getLabel() {
      return label_;
   }

   /**
    * Sets position label (such as well name, etc.).
    *
    * @param lab new MultiStagePosition Label
    */
   public void setLabel(String lab) {
      label_ = lab;
   }

   /**
    * Defines which stage serves as focus control.
    *
    * @param stage new focus stage
    */
   public void setDefaultZStage(String stage) {
      defaultZStage_ = stage;
   }

   /**
    * Returns the Default Z stage, i.e. the one that will be used if not named explicitly.
    *
    * @return Default Z stage (defined by the user in MMCore)
    */
   public String getDefaultZStage() {
      return defaultZStage_;
   }

   /**
    * Return the default XY stage, i,e, the one used if no stage is named explicitly.
    *
    * @return Default XY stage (defined by the user in MMCore)
    */
   public String getDefaultXYStage() {
      return defaultXYStage_;
   }

   /**
    * Defines which stage serves as the default XY motion control device.
    *
    * @param stage new default XY stage
    */
   public void setDefaultXYStage(String stage) {
      defaultXYStage_ = stage;
   }

   /**
    * Moves all stages to the specified positions.
    *
    * @param msp  position to move to
    * @param core - microscope API
    * @throws Exception If there is an error moving the stage.
    */
   public static void goToPosition(MultiStagePosition msp, CMMCore core) throws Exception {
      for (int i = 0; i < msp.size(); i++) {
         StagePosition sp = msp.get(i);
         if (sp.getNumberOfStageAxes() == 1) {
            core.setPosition(sp.getStageDeviceLabel(), sp.get2DPositionX());
         } else if (sp.getNumberOfStageAxes() == 2) {
            core.setXYPosition(sp.getStageDeviceLabel(),
                  sp.get2DPositionX(), sp.get2DPositionY());
         }

         // wait for one device at the time
         // TODO: this should not be here
         core.waitForDevice(sp.getStageDeviceLabel());
      }

   }

   /**
    * Returns "X" coordinate of the position.
    *
    * @return X position of the default XY stage
    */
   public double getX() {
      // TODO: implement more efficient position calculation
      for (StagePosition sp : stagePosList_) {
         if (sp.getNumberOfStageAxes() == 2
               && sp.getStageDeviceLabel().compareTo(defaultXYStage_) == 0) {
            return sp.get2DPositionX();
         }
      }
      return 0.0;
   }

   /**
    * Returns "Y" coordinate of the position.
    *
    * @return Y position of the default XY stage
    */
   public double getY() {
      for (StagePosition sp : stagePosList_) {
         if (sp.getNumberOfStageAxes() == 2
               && sp.getStageDeviceLabel().compareTo(defaultXYStage_) == 0) {
            return sp.get2DPositionY();
         }
      }
      return 0.0;
   }

   /**
    * Returns "Z" - focus coordinate of the position.
    *
    * @return Position of the default Focus stage
    */
   public double getZ() {
      for (StagePosition sp : stagePosList_) {
         if (sp.getNumberOfStageAxes() == 1
               && sp.getStageDeviceLabel().compareTo(defaultZStage_) == 0) {
            return sp.get1DPosition();
         }
      }
      return 0.0;
   }

   /**
    * Sets grid parameters for the rectangular grid.
    *
    * @param row Row number of the grid coordinate
    * @param col Column number of the grid coordinate
    */
   public void setGridCoordinates(int row, int col) {
      gridRow_ = row;
      gridCol_ = col;
   }

   /**
    * Returns rectangular grid row.
    *
    * @return row
    */
   public int getGridRow() {
      return gridRow_;
   }

   /**
    * Returns rectangular grid column.
    *
    * @return column
    */
   public int getGridColumn() {
      return gridCol_;
   }

   /**
    * Compares this MultiStagePosition to another, and returns true if they
    * are equal in all aspects.
    *
    * @param alt The MultiStagePosition to compare against.
    * @return true if every field of this MultiStagePosition matches every
    *     field of the provided MultiStagePosition, false otherwise.
    */
   @Override
   public boolean equals(Object alt) {
      if (!(alt instanceof MultiStagePosition)) {
         return false;
      }
      MultiStagePosition multiAlt = (MultiStagePosition) alt;
      if (!(label_.equals(multiAlt.getLabel())
            && defaultZStage_.equals(multiAlt.getDefaultZStage())
            && defaultXYStage_.equals(multiAlt.getDefaultXYStage())
            && gridRow_ == multiAlt.getGridRow()
            && gridCol_ == multiAlt.getGridColumn()
            && stagePosList_.size() == multiAlt.size())) {
         return false;
      }
      for (int i = 0; i < stagePosList_.size(); ++i) {
         if (!stagePosList_.get(i).equals(multiAlt.get(i))) {
            return false;
         }
      }
      for (String key : properties_.keySet()) {
         if (!properties_.get(key).equals(multiAlt.getProperty(key))) {
            return false;
         }
      }
      // And ensure they don't have any keys we don't.
      for (String key : multiAlt.getPropertyNames()) {
         String property = multiAlt.getProperty(key);
         if (property != null && property.equals(getProperty(key))) {
            return false;
         }
      }
      return true;
   }

   @Override
   public int hashCode() {
      int hash = 3;
      hash = 79 * hash + Objects.hashCode(this.stagePosList_);
      hash = 79 * hash + Objects.hashCode(this.label_);
      hash = 79 * hash + Objects.hashCode(this.defaultZStage_);
      hash = 79 * hash + Objects.hashCode(this.defaultXYStage_);
      hash = 79 * hash + this.gridRow_;
      hash = 79 * hash + this.gridCol_;
      hash = 79 * hash + Objects.hashCode(this.properties_);
      return hash;
   }

   @Override
   public String toString() {
      StringBuilder spos = new StringBuilder();
      for (StagePosition sp : stagePosList_) {
         spos.append(sp.getStageDeviceLabel()).append(" ");
         if (sp.is1DStagePosition()) {
            spos.append(sp.get1DPosition());
         }
         if (sp.is2DStagePosition()) {
            spos.append(sp.get2DPositionX()).append(" ").append(sp.get2DPositionY());
         }
         spos.append(" ");
      }
      return String.format("<MultiStagePosition %s with default XY stage \"%s\", "
                  + "default Z stage \"%s\"; grid %d/%d, properties: %s, positions: %s>",
            label_, defaultXYStage_, defaultZStage_, gridCol_, gridRow_,
            properties_, spos);
   }

   /**
    * Returns a propertymap describing this MultiStagePosition.
    *
    * @return PropertyMap describing this MultiStagePosition
    */
   public PropertyMap toPropertyMap() {
      PropertyMap.Builder properties = PropertyMaps.builder();
      for (Map.Entry<String, String> e : properties_.entrySet()) {
         properties.putString(e.getKey(), e.getValue());
      }
      List<PropertyMap> positions = new ArrayList<>();
      for (StagePosition sp : stagePosList_) {
         positions.add(sp.toPropertyMap());
      }
      return PropertyMaps.builder()
            .putString(PropertyKey.MULTI_STAGE_POSITION__LABEL.key(), label_)
            .putString(PropertyKey.MULTI_STAGE_POSITION__DEFAULT_XY_STAGE.key(),
                  defaultXYStage_)
            .putString(PropertyKey.MULTI_STAGE_POSITION__DEFAULT_Z_STAGE.key(),
                  defaultZStage_)
            .putInteger(PropertyKey.MULTI_STAGE_POSITION__GRID_ROW.key(),
                  gridRow_)
            .putInteger(PropertyKey.MULTI_STAGE_POSITION__GRID_COLUMN.key(),
                  gridCol_)
            .putPropertyMap(PropertyKey.MULTI_STAGE_POSITION__PROPERTIES.key(),
                  properties.build())
            .putPropertyMapList(
                  PropertyKey.MULTI_STAGE_POSITION__DEVICE_POSITIONS.key(),
                  positions).build();
   }

   /**
    * Generates a multistagePosition from the given PropertyMap.
    *
    * @param pmap PropertyMap
    * @return MultiStagePosition
    */
   public static MultiStagePosition fromPropertyMap(PropertyMap pmap) {
      MultiStagePosition ret = new MultiStagePosition();
      ret.label_ = pmap.getString(PropertyKey.MULTI_STAGE_POSITION__LABEL.key(),
            null);
      ret.defaultXYStage_ = pmap.getString(
            PropertyKey.MULTI_STAGE_POSITION__DEFAULT_XY_STAGE.key(), null);
      ret.defaultZStage_ = pmap.getString(
            PropertyKey.MULTI_STAGE_POSITION__DEFAULT_Z_STAGE.key(), null);
      ret.gridRow_ = pmap.getInteger(
            PropertyKey.MULTI_STAGE_POSITION__GRID_ROW.key(), 0);
      ret.gridCol_ = pmap.getInteger(
            PropertyKey.MULTI_STAGE_POSITION__GRID_COLUMN.key(), 0);
      for (String key : pmap.getPropertyMap(
            PropertyKey.MULTI_STAGE_POSITION__PROPERTIES.key(),
            PropertyMaps.emptyPropertyMap()).keySet()) {
         ret.properties_.put(key, pmap.getString(key, ""));
      }
      for (PropertyMap spmap : pmap.getPropertyMapList(
            PropertyKey.MULTI_STAGE_POSITION__DEVICE_POSITIONS.key())) {
         // Opening certain datasets created with certain version of 2.0-beta
         // give the error: 
         // "java.lang.IllegalArgumentException: Invalid stage position
         // (0-axis stage not supported)""
         // catch and log.  This needs a better approach...
         try {
            ret.stagePosList_.add(StagePosition.fromPropertyMap(spmap));
         } catch (IllegalArgumentException iae) {
            // this can lead to a deluge of output.  Still probably better to keep...
            ReportingUtils.logError(iae, iae.getMessage() + spmap.toJSON());
         }
      }
      return ret;
   }
}