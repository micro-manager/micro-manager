///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------

//AUTHOR:       Nico Stuurman, nico@cmp.ucsf.edu, January 10, 2008
//              Nick Anthony, nicholas.anthony@northwestern.edu, Octoboer 18, 2018

//COPYRIGHT:    University of California, San Francisco, 2008 - 2018

//LICENSE:      This file is distributed under the BSD license.
//License text is included with the source distribution.

//This file is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty
//of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.

//IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.


package org.micromanager.internal.positionlist.utils;

import java.text.DecimalFormat;
import javax.swing.JOptionPane;
import mmcorej.CMMCore;
import mmcorej.MMCoreJ;
import mmcorej.StrVector;
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
    private final CMMCore core_;
    static public enum OverlapUnitEnum {UM, PX, PERCENT};
    private static final DecimalFormat FMT_POS = new DecimalFormat("000");
   
    public TileCreator(CMMCore core){
        core_ = core;
    }
    /*
    * Create the tile list based on user input, pixelsize, and imagesize
    */
    public PositionList createTiles(double overlap, OverlapUnitEnum overlapUnit, MultiStagePosition[] endPoints, double pixelSizeUm, String labelPrefix, String xyStage, StrVector zStages, ZGenerator.Type zType) {
         // Make sure at least two corners were set
         if (endPoints.length < 2) {
            ReportingUtils.showError("At least two corners should be set");
            return null;
         }
        //Make sure all Points have the same stage
        for (int i=1; i<endPoints.length; i++){
            if (!xyStage.equals(endPoints[i].getDefaultXYStage())){
                ReportingUtils.showError("All positions given to TileCreator must use the same xy stage");
                return null;
            }
        }  

        ZGenerator zGen = null;
        if (zStages==null){
            zStages = new StrVector();
        }
        if (zStages.size()>0){
            PositionList posList = new PositionList();
            posList.setPositions(endPoints);
            switch (zType) {
                case SHEPINTERPOLATE:
                   zGen = new ZGeneratorShepard(posList);
                   break;
                case AVERAGE:
                default:
                   zGen = new ZGeneratorAverage(posList);
                   break;
           }
        }
     
                  
         // Calculate a bounding rectangle around the defaultXYStage positions
         // TODO: develop method to deal with multiple axis
         StagePosition[] coords = boundingBox(endPoints, xyStage);
         double maxX = coords[1].get2DPositionX();
         double minX = coords[0].get2DPositionX();
         double maxY = coords[1].get2DPositionY();
         double minY = coords[0].get2DPositionY();

         double[] ans = getImageSize(pixelSizeUm);
         double imageSizeXUm = ans[0];
         double imageSizeYUm = ans[1];

         ans = getTileSize(overlap, overlapUnit, pixelSizeUm);
         double tileSizeXUm = ans[0];
         double tileSizeYUm = ans[1];

         double overlapXUm = imageSizeXUm - tileSizeXUm;
         double overlapYUm = imageSizeYUm - tileSizeYUm;

         // bounding box size accounting for the fact that the edges of the image extend past the max/min values set.
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

         //Since an evenly spaced grid will likely not perfectly fit the bounding box
         //that was specified we use this offset so that our grid is still centered properly.
         //This slightly widens the field that is scanned. Sometime this can result in setting
         //a position that is outside of the stage's range of motion. This causes issues when it comes time to stitch.
         //This approach could also result in damage if the user specified a region that is near something delicate and then
         //this code expands that range. It may be good to try a different approach.
         double offsetXUm = (totalSizeXUm - boundingXUm) / 2;   
         double offsetYUm = (totalSizeYUm - boundingYUm) / 2;

         PositionList posList = new PositionList();
         // todo handle mirrorX mirrorY
         for (int y = 0; y < nrImagesY; y++) {
            for (int x = 0; x < nrImagesX; x++) {
               // on even rows go left to right, on odd rows right to left
               int tmpX = x;
               if ((y & 1) == 1) { //If y is odd then we will go backwards in x
                  tmpX = nrImagesX - x - 1;
               }
               MultiStagePosition msp = new MultiStagePosition();

               // Add XY position
               // xyStage is not null; we've checked above.
               msp.setDefaultXYStage(xyStage);
               double X = minX - offsetXUm + (tmpX * tileSizeXUm);
               double Y = minY - offsetYUm + (y * tileSizeYUm);
               StagePosition spXY = StagePosition.create2D(xyStage, X, Y);
               msp.add(spXY);

                // Add Z position
                if (zGen!=null) {
                    msp.setDefaultZStage(zStages.get(0));
                    //loop over Z coordinates and add the correct positions for any we are using
                    for (int a = 0; a < zStages.size(); a++) {
                        StagePosition newSP = StagePosition.create1D(zStages.get(a), 
                            zGen.getZ(X, Y, zStages.get(a)));
                        msp.add(newSP);
                    }
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
        //Returns true if the the camera device adapter indicates that it's x and y axis should be swapped.
        boolean correction, transposeXY;
        String camera = core_.getCameraDevice();
        if (camera == null) {
           JOptionPane.showMessageDialog(null, "This function does not work without a camera");
           return false;
        }
        try {
           String tmp = core_.getProperty(camera, "TransposeCorrection");
           correction = !tmp.equals("0");
           tmp = core_.getProperty(camera, MMCoreJ.getG_Keyword_Transpose_SwapXY());
           transposeXY = !tmp.equals("0");
        } catch (Exception exc) {
           ReportingUtils.showError(exc);
           return false;
        }
        return !correction && transposeXY;
    }

    public double[] getTileSize(double overlap, OverlapUnitEnum overlapUnit, double pixSizeUm) {
        //Returns the x and y sizes of the image after subtracting the overlap.
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
         //Returns the x and y sizes of the image not accounting for the overlap.
        boolean swapXY = isSwappedXY();
        double imageSizeXUm = swapXY ? pixSizeUm * core_.getImageHeight() : 
                                       pixSizeUm * core_.getImageWidth();
        double imageSizeYUm = swapXY ? pixSizeUm * core_.getImageWidth() :
                                       pixSizeUm * core_.getImageHeight();

        return new double[] {imageSizeXUm, imageSizeYUm};
   }
    
    public StagePosition[] boundingBox(MultiStagePosition[] endPoints, String xyStage){
        //Returns the minimum and maximum coordinates found in the set of input coordinates.
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        StagePosition sp;
        for (int i = 0; i < endPoints.length; i++) {
           sp = endPoints[i].get(xyStage);
           if (sp.get2DPositionX() < minX) {
              minX = sp.get2DPositionX();
           }
           if (sp.get2DPositionX() > maxX) {
              maxX = sp.get2DPositionX();
           }
           if (sp.get2DPositionY() < minY) {
              minY = sp.get2DPositionY();
           }
           if (sp.get2DPositionY() > maxY) {
              maxY = sp.get2DPositionY();
           }
        }
        StagePosition minCoords = StagePosition.create2D(xyStage, minX, minY);
        StagePosition maxCoords = StagePosition.create2D(xyStage, maxX, maxY);
        StagePosition[] arr = new StagePosition[2];
        arr[0] = minCoords;
        arr[1] = maxCoords;
        return arr;
    }
}