/*
Author: Nico Stuurman, nico.stuurman@ucsf.edu

Copyright (c) 201?-2017, Regents of the University of California
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

package edu.ucsf.valelab.gaussianfit.datasettransformations;

import edu.ucsf.valelab.gaussianfit.DataCollectionForm;
import edu.ucsf.valelab.gaussianfit.data.RowData;
import edu.ucsf.valelab.gaussianfit.data.SpotData;
import edu.ucsf.valelab.gaussianfit.utils.GaussianUtils;
import edu.ucsf.valelab.gaussianfit.utils.ListUtils;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author nico
 */
public class TrackOperator {

   /**
    * Creates a new dataset that is centered around the average of the X and Y data. In other words,
    * the average of both X and Y is calculated and subtracted from each datapoint
    *
    * @param rowData - Data to be centered
    */
   public static void centerTrack(RowData rowData) {

      if (rowData.spotList_.size() <= 1) {
         return;
      }

      ArrayList<Point2D.Double> xyPoints = ListUtils.spotListToPointList(rowData.spotList_);
      Point2D.Double avgPoint = ListUtils.avgXYList(xyPoints);

      for (Point2D.Double xy : xyPoints) {
         xy.x = xy.x - avgPoint.x;
         xy.y = xy.y - avgPoint.y;
      }

      // create a copy of the dataset and copy in the corrected data
      List<SpotData> transformedResultList =
            Collections.synchronizedList(new ArrayList<SpotData>());

      for (int i = 0; i < xyPoints.size(); i++) {
         SpotData oriSpot = rowData.spotList_.get(i);
         SpotData spot = new SpotData(oriSpot);
         spot.setData(oriSpot.getIntensity(), oriSpot.getBackground(),
               xyPoints.get(i).getX(), xyPoints.get(i).getY(), 0.0, oriSpot.getWidth(),
               oriSpot.getA(), oriSpot.getTheta(), oriSpot.getSigma());
         transformedResultList.add(spot);
      }

      // Add transformed data to data overview window
      RowData.Builder builder = rowData.copy();
      builder.setName(rowData.getName() + " Centered").
            setSpotList(transformedResultList);
      DataCollectionForm.getInstance().addSpotData(builder);

   }

   /**
    * Calculates the axis of motion of a given dataset and normalizes the data to that axis.
    *
    * @param rowData
    */
   public static void straightenTrack(RowData rowData) {

      if (rowData.spotList_.size() <= 1) {
         return;
      }

      ArrayList<Point2D.Double> xyPoints = new ArrayList<Point2D.Double>();
      for (SpotData gs : rowData.spotList_) {
         Point2D.Double point = new Point2D.Double(gs.getXCenter(), gs.getYCenter());
         xyPoints.add(point);
      }

      // Calculate direction of travel and transform data set along this axis
      ArrayList<Point2D.Double> xyCorrPoints = GaussianUtils.pcaRotate(xyPoints);
      List<SpotData> transformedResultList =
            Collections.synchronizedList(new ArrayList<SpotData>());

      for (int i = 0; i < xyPoints.size(); i++) {
         SpotData oriSpot = rowData.spotList_.get(i);
         SpotData spot = new SpotData(oriSpot);
         spot.setData(oriSpot.getIntensity(), oriSpot.getBackground(),
               xyCorrPoints.get(i).getX(), xyCorrPoints.get(i).getY(), 0.0, oriSpot.getWidth(),
               oriSpot.getA(), oriSpot.getTheta(), oriSpot.getSigma());
         transformedResultList.add(spot);
      }

      // Add transformed data to data overview window
      RowData.Builder builder = rowData.copy();
      builder.setName(rowData.getName() + "Straightened").
            setSpotList(transformedResultList);
      DataCollectionForm.getInstance().addSpotData(builder);

   }


}