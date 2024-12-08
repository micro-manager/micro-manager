/**
 * @author kthorn
 */

package org.micromanager.lightsheetcontrol;

import java.io.Serializable;

/**
 * Simple representation of a 2 stage position.
 */
public class ReferencePoint implements Serializable {
   private static final long serialVersionUID = 1;
   private final double stage1Position;
   private final double stage2Position;

   public ReferencePoint(double position1, double position2)  {
      this.stage1Position = position1;
      this.stage2Position = position2;
   }

   public double getStagePosition1() {
      return stage1Position;
   }

   public double getStagePosition2() {
      return stage2Position;
   }

   @Override
   public String toString() {
      return stage1Position + " / " + stage2Position;
   }

}