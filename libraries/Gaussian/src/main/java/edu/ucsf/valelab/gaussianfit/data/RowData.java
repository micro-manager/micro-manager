/*
 * This class holds the data for each row in the Gaussian tracking Data Window
 * 
 * Author: Nico Stuurman, nico.stuurman at ucsf.edu
 * 

Copyright (c) 2013-2017, Regents of the University of California
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

import edu.ucsf.valelab.gaussianfit.DataCollectionForm.Coordinates;
import edu.ucsf.valelab.gaussianfit.utils.ListUtils;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.micromanager.display.DisplayWindow;

/**
 * Data structure for spotlists
 */
public class RowData {

   public static class Builder {

      private String name_;
      private String title_;
      private DisplayWindow dw_;
      private String colCorrRef_ = "";
      private int width_;
      private int height_;
      private float pixelSizeNm_;
      private float zStackStepSizeNm_;
      private int shape_;
      private int halfSize_ = 6;
      private int nrChannels_ = 1;
      private int nrFrames_ = 1;
      private int nrSlices_ = 1;
      private int nrPositions_ = 1;
      private long maxNrSpots_;
      private List<SpotData> spotList_;
      private ArrayList<Double> timePoints_;
      private boolean isTrack_;
      private Coordinates coordinate_ = Coordinates.NM;
      private boolean hasZ_;
      private double minZ_;
      private double maxZ_;

      public RowData build() {
         return new RowData(this);
      }

      public Builder setName(String name) {
         name_ = name;
         return this;
      }

      public Builder setTitle(String title) {
         title_ = title;
         return this;
      }

      public Builder setDisplayWindow(DisplayWindow dw) {
         dw_ = dw;
         return this;
      }

      public Builder setColColorRef(String colCorrRef) {
         colCorrRef_ = colCorrRef;
         return this;
      }

      public Builder setWidth(int width) {
         width_ = width;
         return this;
      }

      public Builder setHeight(int height) {
         height_ = height;
         return this;
      }

      public Builder setPixelSizeNm(float pixelSizeNm) {
         pixelSizeNm_ = pixelSizeNm;
         return this;
      }

      public Builder setZStackStepSizeNm(float zStackStepSizeNm) {
         zStackStepSizeNm_ = zStackStepSizeNm;
         return this;
      }

      public Builder setShape(int shape) {
         shape_ = shape;
         return this;
      }

      public Builder setHalfSize(int halfSize) {
         halfSize_ = halfSize;
         return this;
      }

      public Builder setNrChannels(int nrChannels) {
         nrChannels_ = nrChannels;
         return this;
      }

      public Builder setNrFrames(int nrFrames) {
         nrFrames_ = nrFrames;
         return this;
      }

      public Builder setNrSlices(int nrSlices) {
         nrSlices_ = nrSlices;
         return this;
      }

      public Builder setNrPositions(int nrPositions) {
         nrPositions_ = nrPositions;
         return this;
      }

      public Builder setMaxNrSpots(long maxNrSpots) {
         maxNrSpots_ = maxNrSpots;
         return this;
      }

      public Builder setSpotList(List<SpotData> spotList) {
         spotList_ = spotList;
         return this;
      }

      public Builder setTimePoints(ArrayList<Double> timePoints) {
         timePoints_ = timePoints;
         return this;
      }

      public Builder setIsTrack(boolean isTrack) {
         isTrack_ = isTrack;
         return this;
      }

      public Builder setCoordinate(Coordinates coordinate) {
         coordinate_ = coordinate;
         return this;
      }

      public Builder setHasZ(boolean hasZ) {
         hasZ_ = hasZ;
         return this;
      }

      public Builder setMinZ(double minZ) {
         minZ_ = minZ;
         return this;
      }

      public Builder setMaxZ(double maxZ) {
         maxZ_ = maxZ;
         return this;
      }

   }

   public final List<SpotData> spotList_;
   private Map<Integer, List<SpotData>> frameIndexSpotList_;
   private Map<ImageIndex, List<SpotData>> indexedSpotList_;
   public final ArrayList<Double> timePoints_;
   private String name_;             // name as it appears in the DataCollection table
   public final String title_;      // ImagePlus title of the image
   public final DisplayWindow dw_;  // Micro-Manager window, may be null
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
   public final long maxNrSpots_;
   public final boolean isTrack_;
   public final double stdX_;
   public final double stdY_;
   public final double std_;
   public final int ID_;
   public final Coordinates coordinate_;
   public final boolean hasZ_;
   public final double minZ_;
   public final double maxZ_;
   public final double totalNrPhotons_;
   public final String channels_;

   private static int rowDataID_ = 1;

   private RowData(Builder b) {
      name_ = b.name_;
      title_ = b.title_;
      dw_ = b.dw_;
      colCorrRef_ = b.colCorrRef_;
      width_ = b.width_;
      height_ = b.height_;
      pixelSizeNm_ = b.pixelSizeNm_;
      zStackStepSizeNm_ = b.zStackStepSizeNm_;
      shape_ = b.shape_;
      halfSize_ = b.halfSize_;
      nrChannels_ = b.nrChannels_;
      nrFrames_ = b.nrFrames_;
      nrSlices_ = b.nrSlices_;
      nrPositions_ = b.nrPositions_;
      maxNrSpots_ = b.maxNrSpots_;
      spotList_ = new ArrayList<SpotData>(b.spotList_);
      if (b.timePoints_ != null) {
         timePoints_ = new ArrayList<Double>(b.timePoints_);
      } else {
         timePoints_ = null;
      }
      isTrack_ = b.isTrack_;
      coordinate_ = b.coordinate_;
      hasZ_ = b.hasZ_;
      minZ_ = b.minZ_;
      maxZ_ = b.maxZ_;

      double stdX = 0.0;
      double stdY = 0.0;
      double std = 0.0;
      double nrPhotons = 0.0;
      String tmpChannelStr = "";
      if (isTrack_) {
         ArrayList<Point2D.Double> xyList = ListUtils.spotListToPointList(spotList_);
         Point2D.Double avgPoint = ListUtils.avgXYList(xyList);
         Point2D.Double stdPoint = ListUtils.stdDevsXYList(xyList, avgPoint);
         stdX = stdPoint.x;
         stdY = stdPoint.y;
         std = Math.sqrt(stdX * stdX + stdY * stdY);
         for (SpotData spot : spotList_) {
            nrPhotons += spot.getIntensity();
         }
         List<Integer> channelList = new ArrayList<Integer>();
         for (SpotData spot : spotList_) {
            if (!channelList.contains(spot.getChannel())) {
               channelList.add(spot.getChannel());
            }
         }
         for (Integer i : channelList) {
            tmpChannelStr += i + ", ";
         }
         tmpChannelStr = tmpChannelStr.substring(0, tmpChannelStr.length() - 2);

      }
      channels_ = tmpChannelStr;
      stdX_ = stdX;
      stdY_ = stdY;
      std_ = std;
      totalNrPhotons_ = nrPhotons;
      ID_ = rowDataID_;
      rowDataID_++;
   }

   public RowData.Builder copy() {
      RowData.Builder builder = new Builder();
      builder.setName(name_).setTitle(title_).setDisplayWindow(dw_).
            setColColorRef(colCorrRef_).setWidth(width_).setHeight(height_).
            setPixelSizeNm(pixelSizeNm_).setZStackStepSizeNm(zStackStepSizeNm_).
            setShape(shape_).setHalfSize(halfSize_).setNrChannels(nrChannels_).
            setNrFrames(nrFrames_).setNrSlices(nrSlices_).
            setNrPositions(nrPositions_).setMaxNrSpots(maxNrSpots_).
            setSpotList(spotList_).setTimePoints(timePoints_).
            setIsTrack(isTrack_).setCoordinate(coordinate_).setHasZ(hasZ_).
            setMinZ(minZ_).setMaxZ(maxZ_);
      return builder;
   }

   /**
    * Populates the list frameIndexSpotList which gives access to spots by frame
    */
   public void index() {
      boolean useFrames = nrFrames_ > nrSlices_;
      int nr = nrSlices_;
      if (useFrames) {
         nr = nrFrames_;
      }

      frameIndexSpotList_ = new HashMap<Integer, List<SpotData>>(nr);
      indexedSpotList_ = new HashMap<ImageIndex, List<SpotData>>();

      for (SpotData spot : spotList_) {
         int frameIndex = spot.getSlice();
         if (useFrames) {
            frameIndex = spot.getFrame();
         }
         if (frameIndexSpotList_.get(frameIndex) == null) {
            frameIndexSpotList_.put(frameIndex, new ArrayList<SpotData>());
         }
         frameIndexSpotList_.get(frameIndex).add(spot);

         ImageIndex ii = new ImageIndex(spot.getFrame(), spot.getSlice(),
               spot.getChannel(), spot.getPosition());
         if (indexedSpotList_.get(ii) == null) {
            indexedSpotList_.put(ii, new ArrayList<SpotData>());
         }
         indexedSpotList_.get(ii).add(spot);
      }
   }

   public Map<Integer, List<SpotData>> getSpotListIndexedByFrame() {
      if (frameIndexSpotList_ == null) {
         index();
      }
      return frameIndexSpotList_;
   }


   public List<SpotData> get(int frame, int slice, int channel, int position) {
      ImageIndex ii = new ImageIndex(frame, slice, channel, position);
      if (indexedSpotList_ == null) {
         index();
      }
      return indexedSpotList_.get(ii);
   }

   /**
    * Return the first spot with desired properties or null if not found Uses brute force method
    * (because I got null pointer exceptions using the indexes
    *
    * @param frame   in which the desired spot is located
    * @param channel in which the desired spot is located
    * @param xPos    of the desired spot
    * @param yPos    of the desired spot
    * @return desired spot or null if not found
    */
   public SpotData get(int frame, int channel, double xPos, double yPos) {
      for (SpotData spot : spotList_) {
         if (spot.getFrame() == frame && spot.getChannel() == channel
               && spot.getXCenter() == xPos && spot.getYCenter() == yPos) {
            return spot;
         }
      }

      return null;
   }

   public String getName() {
      return name_;
   }

   public void setName(String name) {
      name_ = name;
   }

   public boolean useSeconds() {
      boolean useS = false;
      if (timePoints_ != null) {
         if (timePoints_.get(timePoints_.size() - 1)
               - timePoints_.get(0) > 10000) {
            useS = true;
         }
      }
      return useS;
   }

   public boolean hasTimeInfo() {
      boolean hasTimeInfo = false;
      if (timePoints_ != null) {
         if (timePoints_.get(timePoints_.size() - 1)
               - timePoints_.get(0) > 0) {
            hasTimeInfo = true;
         }
      }
      return hasTimeInfo;
   }

}
