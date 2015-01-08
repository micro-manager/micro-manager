/*
 * This class holds the data for each row in the Gaussian tracking Data Window
 * 
 * Nico Stuurman, nico.stuurman at ucsf.edu
 * 
 * Copyright UCSF, 2013
 * 
 * Licensed under BSD license version 2.0
 * 
 */
package edu.valelab.gaussianfit.data;

import edu.valelab.gaussianfit.DataCollectionForm.Coordinates;
import edu.valelab.gaussianfit.utils.ListUtils;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
    * Data structure for spotlists
    */
   public class RowData {
     
      
      public final List<SpotData> spotList_;
      public Map<Integer, List<SpotData>> frameIndexSpotList_;
      private Map<ImageIndex, List<SpotData>> indexedSpotList_;
      public final ArrayList<Double> timePoints_;
      public String name_;             // name as it appears in the DataCollection table
      public final String title_;      // ImagePlus title of the image
      public final String colCorrRef_; // id of the dataset used for color correction
      public final int width_;
      public final int height_;
      public final float pixelSizeNm_;
      public final float zStackStepSizeNm_;
      public final int shape_;
      public final int halfSize_;
      public final int nrChannels_;
      public final int nrFrames_;
      public final int nrSlices_;
      public final int nrPositions_;
      public int maxNrSpots_;
      public final boolean isTrack_;
      public final double stdX_;
      public final double stdY_;
      public final int ID_;
      public final Coordinates coordinate_;
      public final boolean hasZ_;
      public final double minZ_;
      public final double maxZ_;
      public final double totalNrPhotons_;  
      
      private static int rowDataID_ = 1;
      

     public RowData(RowData oldRow) {
         
         name_ = oldRow.name_;
         title_ = oldRow.title_;
         colCorrRef_ = oldRow.colCorrRef_;
         width_ =  oldRow.width_;
         height_ = oldRow.height_;
         pixelSizeNm_ = oldRow.pixelSizeNm_ ; 
         zStackStepSizeNm_ = oldRow.zStackStepSizeNm_;
         shape_ = oldRow.shape_;
         halfSize_ = oldRow.halfSize_;
         nrChannels_ = oldRow.nrChannels_;
         nrFrames_ = oldRow.nrFrames_;
         nrSlices_ = oldRow.nrSlices_;
         nrPositions_ = oldRow.nrPositions_;
         maxNrSpots_ = oldRow.maxNrSpots_;
         spotList_ = new ArrayList<SpotData> (oldRow.spotList_);
         if (oldRow.timePoints_ != null)
            timePoints_ = new ArrayList<Double> (oldRow.timePoints_);
         else
            timePoints_ = null;
         isTrack_ = oldRow.isTrack_;
         coordinate_ = oldRow.coordinate_; 
         hasZ_ = oldRow.hasZ_;
         minZ_ = oldRow.minZ_;
         maxZ_ = oldRow.maxZ_;
                 
         double stdX = 0.0;
         double stdY = 0.0;
         double nrPhotons = 0.0;
         if (isTrack_) {
            ArrayList<Point2D.Double> xyList = ListUtils.spotListToPointList(spotList_);
            Point2D.Double avgPoint = ListUtils.avgXYList(xyList);
            Point2D.Double stdPoint = ListUtils.stdDevXYList(xyList, avgPoint);
            stdX = stdPoint.x;
            stdY = stdPoint.y;
            for (SpotData spot : spotList_) {
               nrPhotons += spot.getIntensity();
            }
         }
         stdX_ = stdX;
         stdY_ = stdY;
         totalNrPhotons_ = nrPhotons;
         ID_ = rowDataID_;
         rowDataID_++;
      
      }

      public RowData(String name,
              String title,
              String colCorrRef,
              int width,
              int height,
              float pixelSizeUm, 
              float zStackStepSizeNm,
              int shape,
              int halfSize, 
              int nrChannels,
              int nrFrames,
              int nrSlices,
              int nrPositions,
              int maxNrSpots, 
              List<SpotData> spotList,
              ArrayList<Double> timePoints,
              boolean isTrack, 
              Coordinates coordinate, 
              boolean hasZ, 
              double minZ, 
              double maxZ) {
         name_ = name;
         title_ = title;
         colCorrRef_ = colCorrRef;
         width_ = width;
         height_ = height;
         pixelSizeNm_ = pixelSizeUm;
         zStackStepSizeNm_ = zStackStepSizeNm;
         spotList_ = spotList;
         shape_ = shape;
         halfSize_ = halfSize;
         nrChannels_ = nrChannels;
         nrFrames_ = nrFrames;
         nrSlices_ = nrSlices;
         nrPositions_ = nrPositions;
         maxNrSpots_ = maxNrSpots;
         timePoints_ = timePoints;
         isTrack_ = isTrack;
         double stdX = 0.0;
         double stdY = 0.0;
         double nrPhotons = 0.0;
         if (isTrack_) {
            ArrayList<Point2D.Double> xyList = ListUtils.spotListToPointList(spotList_);
            Point2D.Double avgPoint = ListUtils.avgXYList(xyList);
            Point2D.Double stdPoint = ListUtils.stdDevXYList(xyList, avgPoint);
            stdX = stdPoint.x;
            stdY = stdPoint.y;
            for (SpotData spot : spotList_) {
               nrPhotons += spot.getIntensity();
            }
         }
         stdX_ = stdX;
         stdY_ = stdY;
         totalNrPhotons_ = nrPhotons;
         coordinate_ = coordinate;
         hasZ_ = hasZ;
         minZ_ = minZ;
         maxZ_ = maxZ;
         ID_ = rowDataID_;
         rowDataID_++;
      }
      
      
      
      /**
       * Populates the list frameIndexSpotList which gives access to spots by frame
       */
      public void index() {
         boolean useFrames = nrFrames_ > nrSlices_;
         int nr = nrSlices_;
         if (useFrames)
            nr = nrFrames_;
         
         frameIndexSpotList_ = new HashMap<Integer, List<SpotData>>(nr);
         indexedSpotList_ = new HashMap<ImageIndex, List<SpotData>>();
         
         for (SpotData spot : spotList_) {
            int frameIndex = spot.getSlice();
            if (useFrames)
               frameIndex = spot.getFrame();
            if (frameIndexSpotList_.get(frameIndex) == null)
               frameIndexSpotList_.put(frameIndex, new ArrayList<SpotData>());
            frameIndexSpotList_.get(frameIndex).add(spot);  
            
            ImageIndex ii = new ImageIndex (spot.getFrame(), spot.getSlice(), 
                    spot.getChannel(), spot.getPosition() );
            if (indexedSpotList_.get(ii) == null) {
               indexedSpotList_.put(ii, new ArrayList<SpotData>());
            }
            indexedSpotList_.get(ii).add(spot);           
         }  
      }
      
      public List<SpotData> get(int frame, int slice, int channel, int position) {
         ImageIndex ii = new ImageIndex(frame, slice, channel, position);
         return indexedSpotList_.get(ii);
      }
      
      /**
       * Return the first spot with desired properties or null if not found
       * Uses brute force method (because I got null pointer exceptions using
       * the indexes
       * @param frame in which the desired spot is located
       * @param channel in which the desired spot is located
       * @param xPos of the desired spot
       * @param yPos of the desired spot
       * @return desired spot or null if not found
       */
      public SpotData get(int frame, int channel, double xPos, double yPos) {
         for (SpotData spot : spotList_) {
            if (spot.getFrame() == frame && spot.getChannel() == channel &&
                    spot.getXCenter() == xPos && spot.getYCenter() == yPos) {
               return spot;
            }
         }
 
         return null;
      }

   }