///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// DESCRIPTION:  Describes a single stage position. The stage can have up to three
//               axes. 
//
// AUTHOR:       Nenad Amodaj, nenad@amodaj.com, March 8, 2007
//
// COPYRIGHT:    University of California, San Francisco, 2007
//               100X Imaging Inc, 2008
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
// CVS:          $Id: StagePosition.java 3828 2010-01-22 21:06:21Z arthur $
//

package org.micromanager;

import java.util.Objects;
import org.micromanager.data.internal.PropertyKey;
import org.micromanager.internal.utils.NumberUtils;

/**
 * Description of the position of a 1 or 2 axis stage.
 *
 * @author nico
 */
public class StagePosition {
   /**
    * For two-axis stages, the X position; for one-axis stages, the only stage
    * position. For example, if using a Z focus drive, its position would be
    * given by the "x" parameter.
    *
    * @deprecated Use methods instead.
    */
   @Deprecated
   public double x;

   /**
    * The Y position for two-axis stages.
    *
    * @deprecated Use methods instead.
    */
   @Deprecated
   public double y;

   /**
    * RESERVED: do not use.
    */
   @Deprecated
   public double z;

   /**
    * The stage device label.
    *
    * @deprecated Use {@link #set1DPosition} or {@link #set2DPosition} to set;
    *     use {@link #getStageDeviceLabel} to access.
    */
   @Deprecated
   public String stageName;

   /**
    * The number of stage axes. Must be 1 or 2.
    *
    * @deprecated Use {@link #set1DPosition} or {@link #set2DPosition} to set;
    *     use {@link #getNumberOfStageAxes}, {@link #is1DStagePosition}, or
    *     {@link #is2DStagePosition} to access.
    */
   @Deprecated
   public int numAxes;

   /**
    * Creates a new StapePosition for a 1 axis drive.
    *
    * @param stageDeviceLabel name of the stage/drive
    * @param z                position
    * @return StagePosition object based on the input
    */
   public static StagePosition create1D(String stageDeviceLabel, double z) {
      StagePosition ret = new StagePosition();
      ret.set1DPosition(stageDeviceLabel, z);
      return ret;
   }

   /**
    * Creates a new StapePosition for a 2 axis drive.
    *
    * @param stageDeviceLabel name of the stage/drive
    * @param x                position-x
    * @param y                position-y
    * @return StagePosition object based on the input
    */
   public static StagePosition create2D(String stageDeviceLabel,
                                        double x, double y) {
      StagePosition ret = new StagePosition();
      ret.set2DPosition(stageDeviceLabel, x, y);
      return ret;
   }

   /**
    * Default constructor.
    *
    * @deprecated Use {@link #create1D} or {@link #create2D} instead.
    */
   @Deprecated
   public StagePosition() {
      stageName = "Undefined";
      x = 0.0;
      y = 0.0;
      z = 0.0;
      numAxes = 1;
   }

   /**
    * Creates a copy of a StagePosition object.
    *
    * @param aPos StagePosition to be copied.  Should not be null/
    * @return Copy of the given StagePosition
    */
   public static StagePosition newInstance(StagePosition aPos) {
      StagePosition sp = new StagePosition();
      sp.x = aPos.x;
      sp.y = aPos.y;
      sp.z = aPos.z;
      sp.numAxes = aPos.numAxes;
      sp.stageName = aPos.stageName;
      return sp;
   }

   /**
    * Sets the position of an XY stage device in this StagePosition instance.
    *
    * @param stageDeviceLabel String identifying the stage
    * @param x                New x position
    * @param y                New Y position
    */
   public void set2DPosition(String stageDeviceLabel, double x, double y) {
      stageName = stageDeviceLabel;
      numAxes = 2;
      this.x = x;
      this.y = y;
      this.z = 0.0;
   }

   /**
    * Sets the position of a stage device in this StagePosition instance.
    *
    * @param stageDeviceLabel String identifying the stage
    * @param z                New position
    */
   public void set1DPosition(String stageDeviceLabel, double z) {
      stageName = stageDeviceLabel;
      numAxes = 1;
      this.x = z;
      this.y = this.z = 0.0;
   }

   public String getStageDeviceLabel() {
      return stageName;
   }

   public int getNumberOfStageAxes() {
      return numAxes;
   }

   public boolean is2DStagePosition() {
      return numAxes == 2;
   }

   public boolean is1DStagePosition() {
      return numAxes == 1;
   }

   public double get2DPositionX() {
      return x;
   }

   public double get2DPositionY() {
      return y;
   }

   public double get1DPosition() {
      return x;
   }

   /**
    * Verbose description of this StagePosition.
    *
    * @return Description of this StagePosition as a String.
    */
   public String getVerbose() {
      if (numAxes == 1) {
         return stageName + "(" + NumberUtils.doubleToDisplayString(x) + ")";
      } else if (numAxes == 2) {
         return stageName + "(" + NumberUtils.doubleToDisplayString(x)
               + "," + NumberUtils.doubleToDisplayString(y) + ")";
      } else {
         return stageName + "(" + NumberUtils.doubleToDisplayString(x)
               + "," + NumberUtils.doubleToDisplayString(y)
               + "," + NumberUtils.doubleToDisplayString(z) + ")";
      }

   }

   /**
    * Compare us against the provided StagePosition and return true only if
    * we are equal in all respects.
    *
    * @param alt Other StagePosition to compare against.
    * @return true if every field in alt equals our corresponding field.
    */
   @Override
   public boolean equals(Object alt) {
      if (!(alt instanceof StagePosition)) {
         return false;
      }
      StagePosition spAlt = (StagePosition) alt;
      return spAlt.hashCode() == this.hashCode();
   }

   @Override
   public int hashCode() {
      int hash = 7;
      hash = 37 * hash
            + (int) (Double.doubleToLongBits(this.x) ^ (Double.doubleToLongBits(this.x) >>> 32));
      hash = 37 * hash
            + (int) (Double.doubleToLongBits(this.y) ^ (Double.doubleToLongBits(this.y) >>> 32));
      hash = 37 * hash
            + (int) (Double.doubleToLongBits(this.z) ^ (Double.doubleToLongBits(this.z) >>> 32));
      hash = 37 * hash + Objects.hashCode(this.stageName);
      hash = 37 * hash + this.numAxes;
      return hash;
   }

   @Override
   public String toString() {
      return getVerbose();
   }

   /**
    * Returns this StagePosition instance as a PropertyMap.
    *
    * @return PropertyMap representation of this StagePosition.
    */
   public PropertyMap toPropertyMap() {
      int n = Math.max(0, Math.min(3, numAxes));
      double[] pos = new double[n];
      if (n >= 1) {
         pos[0] = x;
      }
      if (n >= 2) {
         pos[1] = y;
      }
      if (n >= 3) {
         pos[2] = z;
      }
      return PropertyMaps.builder()
            .putString(PropertyKey.STAGE_POSITION__DEVICE.key(), stageName)
            .putDoubleList(PropertyKey.STAGE_POSITION__POSITION_UM.key(), pos)
            .build();
   }

   /**
    * Returns the given PrropertyMap as a StagePosition.
    *
    * @param pmap Input PropertyMap
    * @return StagePosition representation of this PropertyMap.
    */
   public static StagePosition fromPropertyMap(PropertyMap pmap) {
      int n = pmap.getDoubleList(PropertyKey.STAGE_POSITION__POSITION_UM.key()).length;
      switch (n) {
         case 1:
            return StagePosition.create1D(pmap.getString(
                  PropertyKey.STAGE_POSITION__DEVICE.key(), null),
                  pmap.getDoubleList(PropertyKey.STAGE_POSITION__POSITION_UM.key())[0]);
         case 2:
            return StagePosition.create2D(pmap.getString(
                  PropertyKey.STAGE_POSITION__DEVICE.key(), null),
                  pmap.getDoubleList(PropertyKey.STAGE_POSITION__POSITION_UM.key())[0],
                  pmap.getDoubleList(PropertyKey.STAGE_POSITION__POSITION_UM.key())[1]);
         default:
            throw new IllegalArgumentException(String.format(
                  "Invalid stage position (%d-axis stage not supported)", n));
      }
   }
}