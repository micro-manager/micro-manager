package org.micromanager.surveyor;

import ij.process.ImageProcessor;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;


import mmcorej.CMMCore;
import org.micromanager.utils.ReportingUtils;

public class Controller {

    protected AffineTransform mapToStageTransform_;
    protected Point2D.Double mapOriginOnStage_;
    protected double angle_;
    protected boolean flip_;
    protected double surveyorPixelSize_;
    protected Hardware hardware_;
    protected Rectangle surveyorROI_;
    protected int trimx = 8;
    protected int trimy = 8;
    private double xOffset_ = 0;
    private double yOffset_ = 0;
    private double zOffset_ = Double.NEGATIVE_INFINITY;
    private double lastZPosition_;

    public Controller(CMMCore core) {
        hardware_ = new Hardware(core);
        surveyorROI_ = hardware_.getROI();
    }

    public synchronized Dimension getTileDimensions() {
        return new Dimension((int) ((surveyorROI_.width - trimx) / 2) * 2,
                (int) ((surveyorROI_.height - trimy) / 2) * 2);
    }

    public synchronized Dimension getCurrentRoiDimensions() {
        Rectangle currentRoi = hardware_.getROI();
        double currentPixelSize = hardware_.getCurrentPixelSize();
        double roiScalingFactor = currentPixelSize / surveyorPixelSize_;

        int width = (int) (currentRoi.width * roiScalingFactor);
        int height = (int) (currentRoi.height * roiScalingFactor);
        return new Dimension((width / 2) * 2,
                (height / 2) * 2);
    }

    /** Get a tile ready for caching and display at a particular map position. **/
    public synchronized ImageProcessor grabImageAtMapPosition(Point mapPosition) {
        goToMapPosition(mapPosition);
        ImageProcessor img = hardware_.acquireImage();
        Point measuredMapPosition = stageToMap(hardware_.getXYStagePosition());
        return trimImage(img, mapPosition, measuredMapPosition);
    }

    /** Trim the image to correct for sloppiness in stage position. **/
    private ImageProcessor trimImage(ImageProcessor img, Point mapPosition,
            Point measuredMapPosition) {
        int dx = mapPosition.x - measuredMapPosition.x;
        int dy = mapPosition.y - measuredMapPosition.y;
        Dimension tileDimensions = getTileDimensions();
        Rectangle croppingRoi = new Rectangle(trimx / 2 + dx, trimy / 2 + dy, tileDimensions.width, tileDimensions.height);
        img.setRoi(croppingRoi);
        return img.crop();
    }

    public synchronized void goToMapPosition(Point mapPosition) {
        hardware_.stageGo(mapToStage(mapPosition));
    }

    public synchronized Point getCurrentMapPosition() {
        return stageToMap(hardware_.getXYStagePosition());
    }

    public synchronized void specifyMapRelativeToStage(double angle, double pixelSize) {
        specifyMapRelativeToStage(hardware_.getXYStagePosition(), angle, pixelSize);
    }

    public synchronized void specifyMapRelativeToStage(Point2D.Double mapOriginOnStage, double angle, double pixelSize) {
        mapOriginOnStage_ = mapOriginOnStage;
        angle_ = angle;
        surveyorPixelSize_ = pixelSize;

        mapToStageTransform_ = new AffineTransform();
        mapToStageTransform_.translate(mapOriginOnStage_.x, mapOriginOnStage_.y);
        mapToStageTransform_.rotate(getAngle() * Math.PI/180.);
        if (flip_) {
            mapToStageTransform_.scale(-1, 1); // Mirror the x-axis.
        }
        mapToStageTransform_.scale(getPixelSize(), getPixelSize());
    }

    public int getImageType() {
        return hardware_.getImageType();
    }

    public void setMapOriginToCurrentStagePosition() {
        mapOriginOnStage_ = hardware_.getXYStagePosition();
        this.update();
    }

    public Point2D.Double mapToStage(Point pixel) {
        Point2D.Double mapDouble = new Point2D.Double(pixel.x, pixel.y);
        Point2D.Double stagePos = new Point2D.Double();
        mapToStageTransform_.transform(mapDouble, stagePos);
        System.out.println("xOffset_ = "+xOffset_ + ", yOffset_ = "+yOffset_);
        stagePos.x += xOffset_;
        stagePos.y += yOffset_;
        return stagePos;
    }

    public Point stageToMap(Point2D.Double stagePos) {
        Point2D.Double mapDouble = new Point2D.Double();
        stagePos.x -= xOffset_;
        stagePos.y -= yOffset_;
        try {
            mapToStageTransform_.inverseTransform(stagePos, mapDouble);
        } catch (NoninvertibleTransformException e) {
            ReportingUtils.showError(e);
        }
        return new Point((int) Math.round(mapDouble.x), (int) Math.round(mapDouble.y));
    }

    /**
     * @return the angle_
     */
    public double getAngle() {
        return angle_;
    }

    /**
     * @param angle_ the angle_ to set
     */
    public void setAngle(double angle) {
        angle_ = angle;
        update();
    }

    /**
     * @return the pixelSize_
     */
    public double getPixelSize() {
        return surveyorPixelSize_;
    }

    /**
     * @param pixelSize_ the pixelSize_ to set
     */
    public void setPixelSize(double pixelSize) {
        surveyorPixelSize_ = pixelSize;
        update();
    }

    public void rememberZPosition() {
        lastZPosition_ = hardware_.getZPosition();
    }

    public void setOffsets(double x, double y, double z) {
        if (zOffset_ != Double.NEGATIVE_INFINITY) {
            double dz = z - zOffset_;
            hardware_.setZPosition(dz + lastZPosition_);
            
        }

        xOffset_ = x;
        yOffset_ = y;
        zOffset_ = z;
        update();
        //ReportingUtils.showMessage("setOffsets to "+xOffset_+","+yOffset_);

    }

    private void update() {
        this.specifyMapRelativeToStage(mapOriginOnStage_, angle_, surveyorPixelSize_);
    }
}
