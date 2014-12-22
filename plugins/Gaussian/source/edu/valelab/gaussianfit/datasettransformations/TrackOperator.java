
package edu.valelab.gaussianfit.datasettransformations;

import edu.valelab.gaussianfit.DataCollectionForm;
import edu.valelab.gaussianfit.data.RowData;
import edu.valelab.gaussianfit.data.SpotData;
import edu.valelab.gaussianfit.utils.GaussianUtils;
import edu.valelab.gaussianfit.utils.ListUtils;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 *
 * @author nico
 */
public class TrackOperator {
   
    /**
    * Creates a new dataset that is centered around the average of the X and Y data.
    * In other words, the average of both X and Y is calculated and subtracted from each datapoint
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
      DataCollectionForm.getInstance().addSpotData(rowData.name_ + " Centered", 
              rowData.title_, "", rowData.width_,
              rowData.height_, rowData.pixelSizeNm_, rowData.zStackStepSizeNm_, 
              rowData.shape_, rowData.halfSize_, rowData.nrChannels_, 
              rowData.nrFrames_, rowData.nrSlices_, 1, rowData.maxNrSpots_, 
              transformedResultList, rowData.timePoints_, true, DataCollectionForm.Coordinates.NM, 
              false, 0.0, 0.0);
      
   }
   
   /**
    * Calculates the axis of motion of a given dataset and normalizes the data
    * to that axis.
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
      DataCollectionForm.getInstance().addSpotData(rowData.name_ + "Straightened", 
              rowData.title_, "", rowData.width_,
              rowData.height_, rowData.pixelSizeNm_, rowData.zStackStepSizeNm_, 
              rowData.shape_, rowData.halfSize_, rowData.nrChannels_, rowData.nrFrames_,
              rowData.nrSlices_, 1, rowData.maxNrSpots_, transformedResultList,
              rowData.timePoints_, true, DataCollectionForm.Coordinates.NM, false, 0.0, 0.0);
   }
   
   
   
}
