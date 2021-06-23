/*
Copyright (c) 2010-2017, Regents of the University of California
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.
2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

The views and conclusions contained in the software and documentation are those
of the authors and should not be interpreted as representing official policies,
either expressed or implied, of the FreeBSD Project.
 */

package edu.ucsf.valelab.gaussianfit.data;

import java.awt.geom.Point2D;

/**
 * @author nico
 */
public class GsSpotPair implements PointData {

   private final SpotData firstSpot_;
   private final SpotData secondSpot_;
   private final Point2D.Double firstPoint_;
   private final Point2D.Double secondPoint_;
   private boolean partOfTrack_;

   public GsSpotPair(SpotData fgs, SpotData sgs, Point2D.Double fp, Point2D.Double sp) {
      firstSpot_ = fgs;
      secondSpot_ = sgs;
      firstPoint_ = fp;
      secondPoint_ = sp;
      partOfTrack_ = false;
   }

   public SpotData getFirstSpot() {
      return firstSpot_;
   }

   public SpotData getSecondSpot() {
      return secondSpot_;
   }

   public Point2D.Double getFirstPoint() {
      return firstPoint_;
   }

   public Point2D.Double getSecondPoint() {
      return secondPoint_;
   }

   public boolean partOfTrack() {
      return partOfTrack_;
   }

   public void useInTrack(boolean use) {
      partOfTrack_ = use;
   }

   public GsSpotPair copy() {
      return new GsSpotPair(firstSpot_, secondSpot_, firstPoint_, secondPoint_);
   }

   @Override
   public Point2D.Double getPoint() {
      return firstSpot_.getPoint();
   }

}
