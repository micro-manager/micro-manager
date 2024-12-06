/**
 *
 * @author kthorn
 */

package org.micromanager.lightsheetcontrol;

class ReferencePoint {
   public double stage1Position;
   public double stage2Position;

   public ReferencePoint(double position1, double position2)  {
      this.stage1Position = position1;
      this.stage2Position = position2;
   }

   public String name() {
      return stage1Position + " / " + stage2Position;
   }

}