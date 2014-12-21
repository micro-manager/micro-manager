/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.valelab.GaussianFit;

import edu.valelab.GaussianFit.data.SpotData;
import edu.valelab.GaussianFit.DataCollectionForm.Coordinates;
import edu.valelab.GaussianFit.data.RowData;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import java.awt.Point;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.swing.JOptionPane;

/**
 *
 * @author nico
 */
public class DriftCorrector {

   // storage of stage movement data
   class StageMovementData {

      Point2D.Double pos_;
      Point frameRange_;

      StageMovementData(Point2D.Double pos, Point frameRange) {
         pos_ = pos;
         frameRange_ = frameRange;
      }
   }

   public void unJitter(final RowData rowData, int maxFrames, int maxSpots) {

      // TODO: instead of a fixed number of frames, go for a certain number of spots
      // Number of frames could be limited as well
      final int maxNrFrames = maxFrames;
      final int maxNrSpots = maxSpots;

      if (rowData.spotList_.size() <= 1) {
         return;
      }


      ij.IJ.showStatus("Executing jitter correction");


      int mag = (int) (rowData.pixelSizeNm_ / 40.0);
      while (mag % 2 != 0) {
         mag += 1;
      }

      int width = mag * rowData.width_;
      int height = mag * rowData.height_;

      int size = width * height;


      // TODO: add 0 padding to deal with aberrant image sizes
      if ((width != height) || ((width & (width - 1)) != 0)) {
         JOptionPane.showMessageDialog(DataCollectionForm.getInstance(),
                 "Magnified image is not a square with a size that is a power of 2");
         ij.IJ.showStatus(" ");
         return;
      }

      // TODO: what if we should go through nrSlices instead of nrFrames?
      boolean useSlices = false;
      //int nrOfTests = rowData.nrFrames_ / framesToCombine;
      if (rowData.nrFrames_ <= 1) {
         useSlices = true;
      }
      final int nrImages = useSlices ? rowData.nrSlices_ : rowData.nrFrames_;

      /*
      nrOfTests = rowData.nrSlices_ / framesToCombine;
      if (rowData.nrSlices_ % framesToCombine > 0) {
      nrOfTests++;
      }
      } else {
      if (rowData.nrFrames_ % framesToCombine > 0) {
      nrOfTests++;
      }
      }
      
       */


      ArrayList<StageMovementData> stagePos = new ArrayList<StageMovementData>();

      double factor = (double) mag / rowData.pixelSizeNm_;
           
      // Assemble the reference image (the first one)
      ImageProcessor ipRef = new ByteProcessor(width, height);
      byte[] pixelsRef = new byte[width * height];
      ipRef.setPixels(pixelsRef);

      if (rowData.frameIndexSpotList_ == null) {
         rowData.index();
      }
      int spotNr = 0;
      int frameNr = 0;
      while (spotNr < maxNrSpots && frameNr < maxNrFrames && frameNr < nrImages) {
         List<SpotData> frameSpots = rowData.frameIndexSpotList_.get(frameNr);
         if (frameSpots != null) {
            for (SpotData spot: frameSpots) {
               int x = (int) (factor * spot.getXCenter());
               int y = (int) (factor * spot.getYCenter());
               int index = (y * width) + x;
               if (index < size && index > 0) {
                  if (pixelsRef[index] != -1) {
                     pixelsRef[index] += 1;
                  }
               }
               spotNr++;
            }
         }
         frameNr++;
      }

      JitterDetector jd = new JitterDetector(ipRef);

      Point2D.Double fp = new Point2D.Double(0.0, 0.0);
      jd.getJitter(ipRef, fp);

      
      // Assemble images for all subsequent frames and calculate cross-correlation with the first image
      Point2D.Double com = new Point2D.Double(0.0, 0.0);
      ImageProcessor ipTest = new ByteProcessor(width, height);
      byte[] pixelsTest = new byte[width * height];
      ipTest.setPixels(pixelsTest);

      int estimatedNrTests;
      int testNr = 1;

      while (frameNr < nrImages) {
         estimatedNrTests = nrImages / (frameNr / testNr) + 1;
         ij.IJ.showStatus("Executing jitter correction..." + testNr
                 + "/" + estimatedNrTests);
         ij.IJ.showProgress(testNr, estimatedNrTests);
         for (int p = 0; p < size; p++) {
            ipTest.set(p, 0);
         }

         int tmpFrameNr = 0;
         spotNr = 0;

         while (spotNr < maxNrSpots && tmpFrameNr < maxNrFrames && frameNr < nrImages) {
            List<SpotData> frameSpots = rowData.frameIndexSpotList_.get(frameNr); 
            if (frameSpots != null) {
               for (SpotData spot : frameSpots) {
                  int x = (int) (factor * spot.getXCenter());
                  int y = (int) (factor * spot.getYCenter());
                  int index = (y * width) + x;
                  if (index < size && index > 0) {
                     if (pixelsTest[index] != -1) {
                        pixelsTest[index] += 1;
                     }
                  }
                  spotNr++;
               }
            }
            tmpFrameNr++;
            frameNr++;
         }

         jd.getJitter(ipTest, com);
         double x = (fp.x - com.x) / factor;
         double y = (fp.y - com.y) / factor;
         if (rowData.timePoints_ != null) {
            rowData.timePoints_.get(frameNr);
         }
         stagePos.add(new StageMovementData(new Point2D.Double(x, y),
                 new Point(frameNr - tmpFrameNr, frameNr - 1)));
         System.out.println("X: " + x + " Y: " + y);
         testNr++;
      }


      // Assemble stage movement data into a track
      try {
         List<SpotData> stageMovementData = new ArrayList<SpotData>();
         SpotData sm = new SpotData(null, 1, 1, 1, 1, 1, 1, 1);
         sm.setData(0, 0, 0, 0, 0.0, 0, 0, 0, 0);
         stageMovementData.add(sm);

         //stagePos = movingAverage(stagePos, 5);

         for (int i = 0; i < stagePos.size(); i++) {
            StageMovementData smd = stagePos.get(i);
            SpotData s =
                    new SpotData(null, 1, 1, i + 2, 1, 1, 1, 1);
            s.setData(0, 0, smd.pos_.x, smd.pos_.y, 0.0, 0, 0, 0, 0);
            stageMovementData.add(s);
         }


         // Add stage movement data to overview window
         // First try to copy the time points
         ArrayList<Double> timePoints = null;

         RowData newRow = new RowData(
                 rowData.name_ + "-Jitter", rowData.title_, "", rowData.width_,
                 rowData.height_, rowData.pixelSizeNm_, rowData.zStackStepSizeNm_, 
                 rowData.shape_, rowData.halfSize_, rowData.nrChannels_, 
                 stageMovementData.size(), 1, 1, stageMovementData.size(), 
                 stageMovementData, timePoints, true, Coordinates.NM, 
                 false, 0, 0);
         DataCollectionForm.getInstance().rowData_.add(newRow);

         DataCollectionForm.getInstance().myTableModel_.fireTableRowsInserted(DataCollectionForm.getInstance().rowData_.size() - 1, DataCollectionForm.getInstance().rowData_.size());

         ij.IJ.showStatus("Assembling jitter corrected dataset...");
         ij.IJ.showProgress(1);

         List<SpotData> correctedData = new ArrayList<SpotData>();
         Iterator it = rowData.spotList_.iterator();

         testNr = 0;
         StageMovementData smd = stagePos.get(0);
         while (it.hasNext()) {
            SpotData gs = (SpotData) it.next();
            int test;
            if (useSlices) {
               test = gs.getSlice();
            } else {
               test = gs.getFrame();
            }
            if (test != testNr) {
               testNr = test - 1;
            }
            boolean found = false;
            if (testNr >= smd.frameRange_.x && testNr <= smd.frameRange_.y) {
               found = true;
            }
            if (!found) {
               for (int i = 0; i < stagePos.size() && !found; i++) {
                  smd = stagePos.get(i);
                  if (testNr >= smd.frameRange_.x && testNr <= smd.frameRange_.y) {
                     found = true;
                  }
               }
            }
            if (found) {
               Point2D.Double point = new Point2D.Double(gs.getXCenter() - smd.pos_.x,
                       gs.getYCenter() - smd.pos_.y);
               SpotData gsn = new SpotData(gs);
               gsn.setXCenter(point.x);
               gsn.setYCenter(point.y);
               correctedData.add(gsn);
            } else {
               correctedData.add(gs);
            }
         }

         // Add transformed data to data overview window
         DataCollectionForm.getInstance().addSpotData(
                 rowData.name_ + "-Jitter-Correct", rowData.title_, "", rowData.width_,
                 rowData.height_, rowData.pixelSizeNm_, rowData.zStackStepSizeNm_, 
                 rowData.shape_, rowData.halfSize_, rowData.nrChannels_, 
                 rowData.nrFrames_, rowData.nrSlices_, 1, rowData.maxNrSpots_, 
                 correctedData, null, false, Coordinates.NM, rowData.hasZ_, 
                 rowData.minZ_, rowData.maxZ_);
         ij.IJ.showStatus("Finished jitter correction");
      } catch (OutOfMemoryError oom) {
         System.gc();
         ij.IJ.error("Out of Memory");
      }
   }

  

   /**
    * Calculates movingAverage for a List of stageMovementData
    * @param stagePos ArrayList with StageMovementData
    * @param windowSize Window size for moving average
    * @return modified ArrayList of stagemovement data
    */
   private ArrayList<StageMovementData> movingAverage(ArrayList<StageMovementData> stagePos, int windowSize) {
      return stagePos;
      /*
      // calculate moving average for stageposition
      ArrayList<StageMovementData> stagePosMA = new ArrayList<StageMovementData>();
      for (int i = 0; i < stagePos.size() - windowSize; i++) {
         Point2D.Double avg = new Point2D.Double(0.0, 0.0);
         for (int j = 0; j < windowSize; j++) {
            avg.x += stagePos.get(i + j).pos_.x;
            avg.y += stagePos.get(i + j).pos_.y;
         }
         avg.x /= windowSize;
         avg.y /= windowSize;

         stagePosMA.add(new StageMovementData(avg, stagePos.get(i).frameRange_));
      }



      return stagePosMA;
       
       */
   }
}
