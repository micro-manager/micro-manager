/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.internal.positionlist.utils;

import java.text.DecimalFormat;
import javax.swing.JOptionPane;
import mmcorej.CMMCore;
import mmcorej.MMCoreJ;
import org.micromanager.StagePosition;
import org.micromanager.MultiStagePosition;
import org.micromanager.PositionList;
import org.micromanager.internal.utils.NumberUtils;
import org.micromanager.internal.utils.ReportingUtils;

/**
 *
 * @author N2-LiveCell
 */
public final class TileCreator {
    private static CMMCore core_;
    static public enum OverlapUnitEnum {UM, PX, PERCENT};
    private static final DecimalFormat FMT_POS = new DecimalFormat("000");
   
    public TileCreator(CMMCore core){
        core_ = core;
    }
    /*
    * Create the tile list based on user input, pixelsize, and imagesize
    */
    public PositionList createTiles(double overlap, OverlapUnitEnum overlapUnit, MultiStagePosition[] endPoints, double pixelSizeUm, String labelPrefix, String zStage, ZGenerator zGen) {
         // Make sure at least two corners were set
         if (endPoints.length < 2) {
            ReportingUtils.showError("At least two corners should be set");
            return null;
         }
         
         //Make sure all Points have the same stage
         String xyStage = endPoints[0].getDefaultXYStage();
         for (int i=1; i<endPoints.length; i++){
             if (!xyStage.equals(endPoints[i].getDefaultXYStage())){
                 ReportingUtils.showError("All positions given to TileCreator must use the same xy stage");
                 return null;
             }
         }
         
        boolean hasZPlane = (endPoints.length >= 3) && (!zStage.equals(""));
                  
         // Calculate a bounding rectangle around the defaultXYStage positions
         // TODO: develop method to deal with multiple axis
         double minX = Double.POSITIVE_INFINITY;
         double minY = Double.POSITIVE_INFINITY;
         double maxX = Double.NEGATIVE_INFINITY;
         double maxY = Double.NEGATIVE_INFINITY;
         double meanZ = 0.0;
         StagePosition sp;
         for (int i = 0; i < endPoints.length; i++) {
            sp = endPoints[i].get(xyStage);
            if (sp.x < minX) {
               minX = sp.x;
            }
            if (sp.x > maxX) {
               maxX = sp.x;
            }
            if (sp.y < minY) {
               minY = sp.y;
            }
            if (sp.y > maxY) {
               maxY = sp.y;
            }
            if (hasZPlane) {
               sp = endPoints[i].get(zStage);
               meanZ += sp.x;
            }
         }

         meanZ = meanZ / endPoints.length;

         // if there are at least three set points, use them to define a 
         // focus plane: a, b, c such that z = f(x, y) = a*x + b*y + c.

         double zPlaneA = 0.0, zPlaneB = 0.0, zPlaneC = 0.0;

         if (hasZPlane) {
            double x1 = 0.0, y1 = 0.0, z1 = 0.0;
            double x2 = 0.0, y2 = 0.0, z2 = 0.0;
            double x3 = 0.0, y3 = 0.0, z3 = 0.0;

            boolean sp1Set = false;
            boolean sp2Set = false;
            boolean sp3Set = false;

            // if there are four points set, we should either (a) choose the
            // three that are least co-linear, or (b) use a linear regression to
            // fit a focus plane that minimizes the errors at the four selected
            // positions.  this code does neither - it just uses the first three
            // positions it finds.

            for (int i = 0; i < endPoints.length; i++) {
               if (!sp1Set) {
                  x1 = endPoints[i].get(xyStage).x;
                  y1 = endPoints[i].get(xyStage).y;
                  z1 = endPoints[i].get(zStage).x;
                  sp1Set = true;
               } else if (!sp2Set) {
                  x2 = endPoints[i].get(xyStage).x;
                  y2 = endPoints[i].get(xyStage).y;
                  z2 = endPoints[i].get(zStage).x;
                  sp2Set = true;
               } else if (!sp3Set) {
                  x3 = endPoints[i].get(xyStage).x;
                  y3 = endPoints[i].get(xyStage).y;
                  z3 = endPoints[i].get(zStage).x;
                  sp3Set = true;
               }
            }

            // define vectors 1-->2, 1-->3

            double x12 = x2 - x1;
            double y12 = y2 - y1;
            double z12 = z2 - z1;

            double x13 = x3 - x1;
            double y13 = y3 - y1;
            double z13 = z3 - z1;

            // first, make sure the points aren't co-linear: the angle between
            // vectors 1-->2 and 1-->3 must be "sufficiently" large

            double dot_prod = x12 * x13 + y12 * y13 + z12 * z13;
            double magnitude12 = x12 * x12 + y12 * y12 + z12 * z12;
            magnitude12 = Math.sqrt(magnitude12);
            double magnitude13 = x13 * x13 + y13 * y13 + z13 * z13;
            magnitude13 = Math.sqrt(magnitude13);

            double cosTheta = dot_prod / (magnitude12 * magnitude13);
            double theta = Math.acos(cosTheta);  // in RADIANS

            // "sufficiently" large here is 0.5 radians, or about 30 degrees
            if (theta < 0.5
                    || theta > (2 * Math.PI - 0.5)
                    || (theta > (Math.PI - 0.5) && theta < (Math.PI + 0.5))) {
               hasZPlane = false;
            }
            if (Double.isNaN(theta)) {
               hasZPlane = false;
            }

            // intermediates: ax + by + cz + d = 0

            double a = y12 * z13 - y13 * z12;
            double b = z12 * x13 - z13 * x12;
            double c = x12 * y13 - x13 * y12;
            double d = -1 * (a * x1 + b * y1 + c * z1);

            // shuffle to z = f(x, y) = zPlaneA * x + zPlaneB * y + zPlaneC

            zPlaneA = a / (-1 * c);
            zPlaneB = b / (-1 * c);
            zPlaneC = d / (-1 * c);
         }

         double[] ans = getImageSize(pixelSizeUm);
         double imageSizeXUm = ans[0];
         double imageSizeYUm = ans[1];

         ans = getTileSize(overlap, overlapUnit, pixelSizeUm);
         double tileSizeXUm = ans[0];
         double tileSizeYUm = ans[1];

         double overlapXUm = imageSizeXUm - tileSizeXUm;
         double overlapYUm = imageSizeYUm - tileSizeYUm;

         // bounding box size
         double boundingXUm = maxX - minX + imageSizeXUm;
         double boundingYUm = maxY - minY + imageSizeYUm;

         // calculate number of images in X and Y
         int nrImagesX = (int) Math.ceil((boundingXUm - overlapXUm) / tileSizeXUm);
         int nrImagesY = (int) Math.ceil((boundingYUm - overlapYUm) / tileSizeYUm);
         
         if (nrImagesX < 1 || nrImagesY < 1) {
            ReportingUtils.showError("Zero or negative number of images requested. " + "Is the overlap larger than the Image Width or Height?");
            return null;
         }

         double totalSizeXUm = nrImagesX * tileSizeXUm + overlapXUm;
         double totalSizeYUm = nrImagesY * tileSizeYUm + overlapYUm;

         double offsetXUm = (totalSizeXUm - boundingXUm) / 2;
         double offsetYUm = (totalSizeYUm - boundingYUm) / 2;

         PositionList posList = new PositionList();
         // todo handle mirrorX mirrorY
         for (int y = 0; y < nrImagesY; y++) {
            for (int x = 0; x < nrImagesX; x++) {
               // on even rows go left to right, on odd rows right to left
               int tmpX = x;
               if ((y & 1) == 1) {
                  tmpX = nrImagesX - x - 1;
               }
               MultiStagePosition msp = new MultiStagePosition();

               // Add XY position
               // xyStage is not null; we've checked above.
               msp.setDefaultXYStage(xyStage);
               StagePosition spXY = StagePosition.create2D(xyStage, 
                       minX - offsetXUm + (tmpX * tileSizeXUm), //X
                       minY - offsetYUm + (y * tileSizeYUm));   //Y
               msp.add(spXY);

               // Add Z position
               if (!zStage.equals("")) {
                  msp.setDefaultZStage(zStage);
                  double z;
                  if (hasZPlane) {
                     z = zPlaneA * spXY.x + zPlaneB * spXY.y + zPlaneC;
                  } else {
                     z = meanZ;
                  }
                  StagePosition spZ = StagePosition.create1D(zStage, z);
                  msp.add(spZ);
               }

               // Add 'metadata'
               msp.setLabel(labelPrefix + "-Pos" + FMT_POS.format(tmpX) + "_" + FMT_POS.format(y));
               msp.setGridCoordinates(y, tmpX);
               msp.setProperty("Source", "TileCreator");

               if (overlapUnit == OverlapUnitEnum.UM || overlapUnit == OverlapUnitEnum.PX) {
                  msp.setProperty("OverlapUm", NumberUtils.doubleToCoreString(overlapXUm));
                  int overlapPix = (int) Math.floor(overlapXUm / pixelSizeUm);

                  msp.setProperty("OverlapPixels", NumberUtils.intToCoreString(overlapPix));
               } else { // overlapUnit_ == OverlapUnit.PERCENT
                  // overlapUmX != overlapUmY; store both
                  msp.setProperty("OverlapUmX", NumberUtils.doubleToCoreString(overlapXUm));
                  msp.setProperty("OverlapUmY", NumberUtils.doubleToCoreString(overlapYUm));
                  int overlapPixX = (int) Math.floor(overlapXUm / pixelSizeUm);
                  int overlapPixY = (int) Math.floor(overlapYUm / pixelSizeUm);
                  msp.setProperty("OverlapPixelsX", NumberUtils.intToCoreString(overlapPixX));
                  msp.setProperty("OverlapPixelsY", NumberUtils.intToCoreString(overlapPixY));
               }
               posList.addPosition(msp);
            }
         }
        return posList;
    }
   
    private boolean isSwappedXY() {
        boolean correction, transposeXY, mirrorX, mirrorY;
        String camera = core_.getCameraDevice();
        if (camera == null) {
           JOptionPane.showMessageDialog(null, "This function does not work without a camera");
           return false;
        }

        try {
           String tmp = core_.getProperty(camera, "TransposeCorrection");
           correction = !tmp.equals("0");
           tmp = core_.getProperty(camera, MMCoreJ.getG_Keyword_Transpose_MirrorX());
           mirrorX = !tmp.equals("0");
           tmp = core_.getProperty(camera, MMCoreJ.getG_Keyword_Transpose_MirrorY());
           mirrorY = !tmp.equals("0");
           tmp = core_.getProperty(camera, MMCoreJ.getG_Keyword_Transpose_SwapXY());
           transposeXY = !tmp.equals("0");
        } catch (Exception exc) {
           ReportingUtils.showError(exc);
           return false;
        }

        return !correction && transposeXY;
    }

    public double[] getTileSize(double overlap, OverlapUnitEnum overlapUnit, double pixSizeUm) {
        double overlapUmX;
        double overlapUmY;

        if(overlapUnit == OverlapUnitEnum.UM)
            overlapUmX = overlapUmY = overlap;
        else if(overlapUnit == OverlapUnitEnum.PERCENT) {
            overlapUmX = pixSizeUm * (overlap / 100) * core_.getImageWidth();
            overlapUmY = pixSizeUm * (overlap / 100) * core_.getImageHeight();
        } else { // overlapUnit_ == OverlapUnit.PX
            overlapUmX = overlap * pixSizeUm;
            overlapUmY = overlap * pixSizeUm;
        }

        // if camera does not correct image orientation, we'll correct for it here:
        boolean swapXY = isSwappedXY();

        double tileSizeXUm = swapXY ? 
                             pixSizeUm * core_.getImageHeight() - overlapUmY :
                             pixSizeUm * core_.getImageWidth() - overlapUmX;

        double tileSizeYUm = swapXY ? 
                             pixSizeUm * core_.getImageWidth() - overlapUmX :
                             pixSizeUm * core_.getImageHeight() - overlapUmY;

        return new double[] {tileSizeXUm, tileSizeYUm};
    }

    public double[] getImageSize(double pixSizeUm) {     
        boolean swapXY = isSwappedXY();
        double imageSizeXUm = swapXY ? pixSizeUm * core_.getImageHeight() : 
                                       pixSizeUm * core_.getImageWidth();
        double imageSizeYUm = swapXY ? pixSizeUm * core_.getImageWidth() :
                                       pixSizeUm * core_.getImageHeight();

        return new double[] {imageSizeXUm, imageSizeYUm};
   }
}