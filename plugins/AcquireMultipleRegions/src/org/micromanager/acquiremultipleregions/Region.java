/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.acquiremultipleregions;

import java.io.File;
import org.micromanager.acquiremultipleregions.AcquireMultipleRegionsForm.AxisList;
import org.micromanager.api.MultiStagePosition;
import org.micromanager.api.PositionList;
import org.micromanager.api.StagePosition;

/**
 *
 * @author kthorn
 */
public class Region {
    public PositionList positions;
    public String directory;
    public String filename;

    public Region (PositionList PL, String directory, String filename) {
        this.positions = PositionList.newInstance(PL);
        this.directory = directory;
        this.filename = filename;
    }
    
    /*
    Return a name for the region, by concatenating Directory and Filename
    */
    public String name() {
        File loc = new File(directory, filename);
        String fullfile = loc.getPath();
        return fullfile;        
    }
    
    public PositionList boundingBox() {
        //returns the bounding box of the region as a MicroManager PositionList
        //the first index is the min coordinates, the second is the max coordinates
        MultiStagePosition minCoords;
        MultiStagePosition maxCoords;
        PositionList bBox = new PositionList();
        MultiStagePosition startCoords = positions.getPosition(0);
        String XYStage = startCoords.getDefaultXYStage();
        String ZStage = startCoords.getDefaultZStage();
        double minX = startCoords.getX();
        double minY = startCoords.getY();
        double Z = startCoords.getZ(); //don't worry about min and max of Z
        double maxX = minX;
        double maxY = minY;
        for (int i=1; i< positions.getNumberOfPositions(); i++){
            MultiStagePosition p = positions.getPosition(i);
            minX = Math.min(p.getX(), minX);
            minY = Math.min(p.getY(), minY);
            maxX = Math.max(p.getX(), maxX);
            maxY = Math.max(p.getY(), maxY);            
        }
        minCoords = new MultiStagePosition(XYStage, minX, minY, ZStage, Z);
        maxCoords = new MultiStagePosition(XYStage, maxX, maxY, ZStage, Z);
        bBox.addPosition(minCoords);
        bBox.addPosition(maxCoords);
        return bBox;
    }
    
    public MultiStagePosition center() {
        double centerX;
        double centerY;
        MultiStagePosition centerPos;
        PositionList PL = boundingBox();
        MultiStagePosition minCoords = PL.getPosition(0);
        MultiStagePosition maxCoords = PL.getPosition(1);
        centerX = (minCoords.getX() + maxCoords.getX()) / 2;
        centerY = (minCoords.getY() + maxCoords.getY()) / 2;
        centerPos = new MultiStagePosition(minCoords.getDefaultXYStage(), 
                centerX, centerY, minCoords.getDefaultZStage(), minCoords.getZ());
        return centerPos;
    }
    
    public int getNumXTiles(double xStepSize){
        PositionList bBox;
        double minX, maxX;
        int numXImages;
        
        bBox = this.boundingBox();
        minX = bBox.getPosition(0).getX();
        maxX = bBox.getPosition(1).getX();
        numXImages = (int) Math.ceil(Math.abs(maxX - minX) / xStepSize) + 1; // +1 for fencepost problem
        return numXImages;
    }
    
    public int getNumYTiles(double yStepSize){
        PositionList bBox;
        double minY, maxY;
        int numYImages;
        
        bBox = this.boundingBox();
        minY = bBox.getPosition(0).getY();
        maxY = bBox.getPosition(1).getY();
        numYImages = (int) Math.ceil(Math.abs(maxY - minY) / yStepSize) + 1; // +1 for fencepost problem
        return numYImages;        
    }
    
    
    /**
     * Calculates a tiling grid of positions to cover the regions bounding
     * box in steps of xStepSize and yStepSize. Positions for single axis stages
     * are set to the mean value of those stages in the input coordinates
     *
     * @param   xStepSize   the step size in the X dimension
     * @param   yStepSize   the step size in the Y dimension
     * @param   axisList    1-D axes to include
     * @return  a PositionList comprising the tiling positions
     * 
     **/
    public PositionList tileGrid(double xStepSize, double yStepSize, AxisList axisList) {
        //generate tiling grid to cover bounding box in steps of xStepSize 
        //and yStepSize
        PositionList bBox, PL;
        MultiStagePosition MSP0, averageMSP;
        StagePosition SP, newSP;
        double minX, maxX, minY, C;
        int numXImages, numYImages;
        
        bBox = this.boundingBox();
        minX = bBox.getPosition(0).getX();
        minY = bBox.getPosition(0).getY(); 
        numXImages = this.getNumXTiles(xStepSize);
        numYImages = this.getNumYTiles(yStepSize);        
        //update maxX to cover an integer number of fields
        maxX = minX + (numXImages-1) * xStepSize;
        
        //Loop over single axis stages and calculate their mean value
        //Eventually we may want to interpolate single axis positions as a function 
        //of X and Y, but this is a good starting point.        
        MSP0 =  positions.getPosition(0);
        averageMSP = new MultiStagePosition();
        averageMSP.setDefaultXYStage(MSP0.getDefaultXYStage());
        averageMSP.setDefaultZStage(MSP0.getDefaultZStage());        
        for (int a=0; a<MSP0.size(); a++){
            SP = MSP0.get(a);
            if (SP.numAxes == 1 && axisList.use(SP.stageName)){
               C = SP.x;
               //Calculate sum of positions for current axis
               for (int p=1; p<positions.getNumberOfPositions(); p++){
                   C = C + positions.getPosition(p).get(a).x;                
               }
               newSP = new StagePosition();
               newSP.numAxes = 1;
               newSP.stageName = SP.stageName;
               newSP.x = C / positions.getNumberOfPositions(); //average
               averageMSP.add(newSP);
            }
        }        
        
        //initial conditions
        double startX = minX;
        double Y = minY;
        double direction = 1;
        PL = new PositionList();
        for (int yidx = 0; yidx < numYImages; yidx++){
            for (int xidx = 0; xidx < numXImages; xidx++){
		double X = startX + direction * xidx * xStepSize;
                newSP = new StagePosition();
                newSP.numAxes = 2;
                newSP.stageName = averageMSP.getDefaultXYStage();
                newSP.x = X;
                newSP.y = Y;
                MultiStagePosition MSP = MultiStagePosition.newInstance(averageMSP);
                MSP.add(newSP);
		PL.addPosition(MSP);
            }
            //Update Y coordinate
            Y = Y + yStepSize;
            //Acquire images by zig-zagging.
            if (direction == 1) {
                startX = maxX;
                direction = -1;
            } else{
                startX = minX;
                direction = 1;
            }
        }
        return PL;        
    }
}