/*
 * This class stores data specific to tracks
 * 
 * Author: Nico Stuurman, nico.stuurman at ucsf.edu
 * 

Copyright (c) 2016-2017, Regents of the University of California
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

import edu.ucsf.valelab.gaussianfit.utils.ListUtils;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

/**
 * @author nico
 */
public class TrackData implements PointData {

   private final List<SpotData> spotList_;
   private int missingAtEnd_;
   private Point2D.Double center_;

   public TrackData() {
      spotList_ = new ArrayList<SpotData>();
      missingAtEnd_ = 0;
   }

   public void addMissing() {
      missingAtEnd_++;
   }

   public void resetMissing() {
      missingAtEnd_ = 0;
   }

   public boolean missingMoreThan(int thisMany) {
      return missingAtEnd_ > thisMany;
   }

   public int size() {
      return spotList_.size();
   }

   public SpotData get(int index) {
      return spotList_.get(index);
   }

   public void add(SpotData item) {
      spotList_.add(item);
   }

   public void add(TrackData otherTrack) {
      for (SpotData item : otherTrack.getList()) {
         spotList_.add(item);
      }
   }

   public List<SpotData> getList() {
      return spotList_;
   }

   public Point2D.Double getCenter() {
      if (center_ == null) {
         center_ = ListUtils.avgXYList(ListUtils.spotListToPointList(spotList_));
      }
      return center_;
   }

   @Override
   public Point2D.Double getPoint() {
      return getCenter();
   }

}
