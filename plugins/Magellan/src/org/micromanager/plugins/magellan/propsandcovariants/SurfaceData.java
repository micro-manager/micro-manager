///////////////////////////////////////////////////////////////////////////////
// AUTHOR:       Henry Pinkard, henry.pinkard@gmail.com
//
// COPYRIGHT:    University of California, San Francisco, 2015
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
package org.micromanager.plugins.magellan.propsandcovariants;

import org.micromanager.plugins.magellan.acq.AcquisitionEvent;
import org.micromanager.plugins.magellan.coordinates.AffineUtils;
import org.micromanager.plugins.magellan.coordinates.XYStagePosition;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.micromanager.plugins.magellan.main.Magellan;
import org.micromanager.plugins.magellan.misc.Log;
import org.micromanager.plugins.magellan.surfacesandregions.CurvedSurfaceCovariantCreationDialog;
import org.micromanager.plugins.magellan.surfacesandregions.SingleResolutionInterpolation;
import org.micromanager.plugins.magellan.surfacesandregions.SurfaceInterpolator;

/**
 * Category about interpolated surface (e.g. distance below surface) to be used
 * in covaried settings
 */
public class SurfaceData implements Covariant {

    //all data must start with this prefix so they can be reconstructed when read from a text file on disk
    public static String PREFIX = "Surface data: ";
    //number of test points per dimension for finding minimum distance to surface
    private static final int NUM_XY_TEST_POINTS = 9;
    private static final int FOV_LASER_MODULATION_RESOLUTION = 16;
    //number of test points per dimension for finding minimum distance to surface within angle
//   private static final int NUM_XY_TEST_POINTS_ANGLE = 5;
    public static String SPACER = "--";
    public static String DISTANCE_BELOW_SURFACE_CENTER = "Vertical distance below at XY position center";
    public static String DISTANCE_BELOW_SURFACE_MINIMUM = "Minimum vertical distance below at XY position";
    public static String DISTANCE_BELOW_SURFACE_MAXIMUM = "Maximum vertical distance below at XY position";
    public static String CURVED_SURFACE_RELATIVE_POWER = "Relative power for curved surface";
    public static String NEURAL_NET_CONTROL = "Neural net controlled excitaiton";
    private String category_;
    private SurfaceInterpolator surface_;
    //used for curved surface calculations
    private int radiusOfCurvature_, meanFreePath_;
    private int baseVoltage_;
    private double basePower_;
    //used for neural net control
    private LaserPredNet nn1_, nn2_;

    public SurfaceData(SurfaceInterpolator surface, String type) throws Exception {
        category_ = type;
        surface_ = surface;
        if (!Arrays.asList(enumerateDataTypes()).contains(type)) {
            //not a recognized type
            throw new Exception();
        }
    }
    
    public LaserPredNet getNN(int index) {
       if (index == 0) { 
          return nn1_;
       } else {
          return nn2_;
       }
    }
    
    public boolean isNeuralNetControl() {
       return category_.equals(NEURAL_NET_CONTROL);
    }
    
    public boolean isCurvedSurfaceCalculation() {
       return category_.equals(CURVED_SURFACE_RELATIVE_POWER);
    }

    public void initializeCurvedSurfaceData() throws Exception {
        if (category_.equals(CURVED_SURFACE_RELATIVE_POWER)) {
            CurvedSurfaceCovariantCreationDialog creator = new CurvedSurfaceCovariantCreationDialog();
            creator.waitForCreationOrCancel();
            if (creator.wasCanceled()) {
                throw new Exception("Surface data canceled");
            }
            radiusOfCurvature_ = creator.getRadiusOfCurvature();
            meanFreePath_ = creator.getMFP();
            baseVoltage_ = creator.getBaseVoltage();
        }
    }
    
    public void initializeNeuralNetControl() throws Exception {
        if (category_.equals(NEURAL_NET_CONTROL)) {
            //TODO: get power
            nn1_ = new LaserPredNet("./maitaimodel.csv");
            nn2_ = new LaserPredNet("./chameleonmodel.csv");
        }
    }

    public void setBasePowerFromBaseVoltage(CovariantPairing reversePairing) {
        basePower_ = reversePairing.getInterpolatedNumericalValue(new CovariantValue(baseVoltage_));
    }

    public SurfaceInterpolator getSurface() {
        return surface_;
    }

    public static String[] enumerateDataTypes() {
        return new String[]{NEURAL_NET_CONTROL, CURVED_SURFACE_RELATIVE_POWER, DISTANCE_BELOW_SURFACE_CENTER, DISTANCE_BELOW_SURFACE_MINIMUM,
            DISTANCE_BELOW_SURFACE_MAXIMUM};
    }

    @Override
    public String toString() {
        return getName();
    }

    @Override
    public String getAbbreviatedName() {
        if (category_.equals(DISTANCE_BELOW_SURFACE_CENTER)) {
            return "Vertical distance to " + surface_.getName();
        } else if (category_.equals(DISTANCE_BELOW_SURFACE_MINIMUM)) {
            return "Min vertical distance to " + surface_.getName();
        } else if (category_.equals(DISTANCE_BELOW_SURFACE_MAXIMUM)) {
           return "Min distance to " + surface_.getName();
        } else if (category_.equals(CURVED_SURFACE_RELATIVE_POWER)) {
           return "Relative power for " + surface_.getName() + " R" + radiusOfCurvature_ + " MFP" + meanFreePath_ + " Base" + basePower_;
        } else if (category_.equals(NEURAL_NET_CONTROL)) {
           return "Relative power for " + surface_.getName() + " brightness1: " + nn1_.getBrightness() + "  brightness2: " + nn2_.getBrightness();
        } else {
           Log.log("Unknown Surface data type");
            throw new RuntimeException();
        }
    }

    @Override
    public String getName() {
        return PREFIX + surface_.getName() + SPACER + category_;
    }

    @Override
    public boolean isValid(CovariantValue potentialValue) {
        return potentialValue.getType() == CovariantType.DOUBLE;
    }

    @Override
    public CovariantValue[] getAllowedValues() {
        //not applicable because all numerical for now
        return null;
    }

    @Override
    public boolean isDiscrete() {
        return false;
    }

    @Override
    public boolean hasLimits() {
        return false;
    }

    @Override
    public CovariantValue getLowerLimit() {
        return null;
    }

    @Override
    public CovariantValue getUpperLimit() {
        return null;
    }

    @Override
    public CovariantType getType() {
        return CovariantType.DOUBLE;
    }

    @Override
    public CovariantValue getValidValue(List<CovariantValue> vals) {
        double d = 0;
        while (true) {
            if (!vals.contains(new CovariantValue(d))) {
                return new CovariantValue(d);
            }
            d++;
        }
    }
    
    public double[] curvedSurfacePower(AcquisitionEvent event, double multiplier) {
        XYStagePosition xyPos = event.xyPosition_;
        double zPosition = event.zPosition_;
        Point2D.Double[] corners = xyPos.getFullTileCorners();
        //square is aligned with axes in pixel space, so convert to pixel space to generate test points
        double xSpan = corners[2].getX() - corners[0].getX();
        double ySpan = corners[2].getY() - corners[0].getY();
        Point2D.Double pixelSpan = new Point2D.Double();
        AffineTransform transform = AffineUtils.getAffineTransform(surface_.getCurrentPixelSizeConfig(), 0, 0);
        try {
            transform.inverseTransform(new Point2D.Double(xSpan, ySpan), pixelSpan);
        } catch (NoninvertibleTransformException ex) {
            Log.log("Problem inverting affine transform");
        }

        double[] relativePower = new double[FOV_LASER_MODULATION_RESOLUTION * FOV_LASER_MODULATION_RESOLUTION];
        for (int xInd = 0; xInd < FOV_LASER_MODULATION_RESOLUTION; xInd++) {
            for (int yInd = 0; yInd < FOV_LASER_MODULATION_RESOLUTION; yInd++) {

                double x = ((0.5 + pixelSpan.x) / (double) FOV_LASER_MODULATION_RESOLUTION) * xInd;
                double y = ((0.5 + pixelSpan.y) / (double) FOV_LASER_MODULATION_RESOLUTION) * yInd;
                //convert these abritray pixel coordinates back to stage coordinates
                double[] transformMaxtrix = new double[6];
                transform.getMatrix(transformMaxtrix);
                transformMaxtrix[4] = corners[0].getX();
                transformMaxtrix[5] = corners[0].getY();
                //create new transform with translation applied
                transform = new AffineTransform(transformMaxtrix);
                Point2D.Double stageCoords = new Point2D.Double();
                transform.transform(new Point2D.Double(x, y), stageCoords);

                //Index in the way Teensy expects data
                int flatIndex = xInd + FOV_LASER_MODULATION_RESOLUTION * yInd;
                try {
                    //test point for inclusion of position
                    if (!surface_.waitForCurentInterpolation().isInterpDefined(stageCoords.x, stageCoords.y)) {
                        //if position is outside of convex hull, use minimum laser power
                        relativePower[flatIndex] = basePower_;
                    } else {
                        float interpVal = surface_.waitForCurentInterpolation().getInterpolatedValue(stageCoords.x, stageCoords.y);
                        float normalAngle = surface_.waitForCurentInterpolation().getNormalAngleToVertical(stageCoords.x, stageCoords.y);
                        relativePower[flatIndex] = basePower_ * CurvedSurfaceCalculations.getRelativePower(meanFreePath_,
                                Math.max(0, zPosition - interpVal), normalAngle, radiusOfCurvature_) * multiplier;
                    }
                } catch (InterruptedException ex) {
                    Log.log("Couldn't calculate curved surface power");
                    Log.log(ex);
                    return null;
                }
            }
        }
        return relativePower;
    }

   /**
     *
     * @param corners
     * @param min true to get min, false to get max
     * @return {minDistance,maxDistance, minNormalAngle, maxNormalAngle)
     */
    private double[] distanceAndNormalCalc(Point2D.Double[] corners, double zVal) throws InterruptedException {
        //check a grid of points spanning entire position        
        //square is aligned with axes in pixel space, so convert to pixel space to generate test points
        double xSpan = corners[2].getX() - corners[0].getX();
        double ySpan = corners[2].getY() - corners[0].getY();
        Point2D.Double pixelSpan = new Point2D.Double();
        AffineTransform transform = AffineUtils.getAffineTransform(surface_.getCurrentPixelSizeConfig(), 0, 0);
        try {
            transform.inverseTransform(new Point2D.Double(xSpan, ySpan), pixelSpan);
        } catch (NoninvertibleTransformException ex) {
            Log.log("Problem inverting affine transform");
        }
        double minDistance = Integer.MAX_VALUE;
        double maxDistance = 0;
        double minNormalAngle = 90;
        double maxNormalAngle = 0;
        for (double x = 0; x <= pixelSpan.x; x += pixelSpan.x / (double) NUM_XY_TEST_POINTS) {
            for (double y = 0; y <= pixelSpan.y; y += pixelSpan.y / (double) NUM_XY_TEST_POINTS) {
                //convert these abritray pixel coordinates back to stage coordinates
                double[] transformMaxtrix = new double[6];
                transform.getMatrix(transformMaxtrix);
                transformMaxtrix[4] = corners[0].getX();
                transformMaxtrix[5] = corners[0].getY();
                //create new transform with translation applied
                transform = new AffineTransform(transformMaxtrix);
                Point2D.Double stageCoords = new Point2D.Double();
                transform.transform(new Point2D.Double(x, y), stageCoords);
                //test point for inclusion of position
                if (!surface_.waitForCurentInterpolation().isInterpDefined(stageCoords.x, stageCoords.y)) {
                    //if position is outside of convex hull, assume min distance is 0
                    minDistance = 0;
                    //get extrapolated value for max distance
                    float interpVal = surface_.getExtrapolatedValue(stageCoords.x, stageCoords.y);
                    maxDistance = Math.max(zVal - interpVal, maxDistance);
                    //only take actual values for normals
                } else {
                    float interpVal = surface_.waitForCurentInterpolation().getInterpolatedValue(stageCoords.x, stageCoords.y);
                    float normalAngle = surface_.waitForCurentInterpolation().getNormalAngleToVertical(stageCoords.x, stageCoords.y);
                    minDistance = Math.min(Math.max(0, zVal - interpVal), minDistance);
                    maxDistance = Math.max(zVal - interpVal, maxDistance);
                    minNormalAngle = Math.min(minNormalAngle, normalAngle);
                    maxNormalAngle = Math.max(maxNormalAngle, normalAngle);
                }
            }
        }
        return new double[]{minDistance, maxDistance, minNormalAngle, maxNormalAngle};
    }

    @Override
    public CovariantValue getCurrentValue(AcquisitionEvent event) throws Exception {
        XYStagePosition xyPos = event.xyPosition_;
        if (category_.equals(DISTANCE_BELOW_SURFACE_CENTER)) {
            //if interpolation is undefined at position center, assume distance below is 0
            Point2D.Double center = xyPos.getCenter();
            SingleResolutionInterpolation interp = surface_.waitForCurentInterpolation();
            if (interp.isInterpDefined(center.x, center.y)) {
                return new CovariantValue(event.zPosition_ - interp.getInterpolatedValue(center.x, center.y));
            }
            return new CovariantValue(0.0);

        } else if (category_.equals(DISTANCE_BELOW_SURFACE_MINIMUM)) {
            return new CovariantValue(distanceAndNormalCalc(xyPos.getFullTileCorners(), event.zPosition_)[0]);
        } else if (category_.equals(DISTANCE_BELOW_SURFACE_MAXIMUM)) {
            return new CovariantValue(distanceAndNormalCalc(xyPos.getFullTileCorners(), event.zPosition_)[1]);
        } else {
            Log.log("Unknown Surface data type", true);
            throw new RuntimeException();
        }
    }

    @Override
    public void updateHardwareToValue(CovariantValue dVal) {
        Log.log("No hardware associated with Surface data", true);
        throw new RuntimeException();
    }
}