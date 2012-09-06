package org.micromanager.slideexplorer;

import java.awt.Rectangle;
import java.awt.geom.Point2D;

import ij.process.ImageProcessor;

import org.micromanager.utils.ImageUtils;

import mmcorej.CMMCore;
import org.micromanager.utils.ReportingUtils;

public class Hardware {

    CMMCore core_;
    String stage_;

    Hardware(CMMCore core) {
        core_ = core;
        stage_ = core_.getXYStageDevice();
    }

    // Camera commands -----------------------
    ImageProcessor acquireImage() {
        try {
            core_.snapImage();
            Object img = core_.getImage();
            return ImageUtils.makeProcessor(core_, img);
        } catch (Exception e) {
            ReportingUtils.logError(e);
            return null;
        }
    }

    public Rectangle getROI() {
        int roi[][] = new int[4][1];
        try {
            core_.getROI(roi[0], roi[1], roi[2], roi[3]);
        } catch (Exception e) {
            ReportingUtils.logError(e);
        }
        return new Rectangle(roi[0][0], roi[1][0], roi[2][0], roi[3][0]);
    }

    public double getCurrentPixelSize() {
        return core_.getPixelSizeUm();
    }

    // Stage commands -------------------------------------------
    Point2D.Double getXYStagePosition() {
        String stage = core_.getXYStageDevice();
        if (stage.length() == 0) {
            return null;
        }

        double x[] = new double[1];
        double y[] = new double[1];
        try {
            core_.getXYPosition(stage, x, y);
            return new Point2D.Double(x[0], y[0]);
        } catch (Exception e) {
            ReportingUtils.logError(e);
        }
        return null;
    }

    void setXYStagePosition(double x, double y) {
        try {
            core_.setXYPosition(stage_, x, y);
        } catch (Exception e) {
            ReportingUtils.logError(e);
        }

    }

    void setXYStagePosition(Point2D.Double pos) {
        setXYStagePosition(pos.x, pos.y);
    }

    public void stageGo(Point2D.Double stagePos) {
        String xystage = core_.getXYStageDevice();
        Point2D.Double oldPos = getXYStagePosition();

        if ((oldPos.x != stagePos.x) || (oldPos.y != stagePos.y)) {
            try {
                while (core_.deviceBusy(xystage));
            } catch (Exception e) {
                ReportingUtils.logError(e);
            }
            setXYStagePosition(stagePos.x, stagePos.y);
            try {
                while (core_.deviceBusy(xystage)) {
                    //updateStagePositionRect();
                    core_.sleep(100);
                }
            } catch (Exception e) {
                ReportingUtils.logError(e);
            }
            //updateStagePositionRect();
        }
    }

    public int getImageType() {
        return ImageUtils.BppToImageType(core_.getBytesPerPixel());
    }

    public double getZPosition() {
        try {
            return core_.getPosition(core_.getFocusDevice());
        } catch (Exception ex) {
            ReportingUtils.logError(ex);
            return Double.POSITIVE_INFINITY;
        }
    }

    public void setZPosition(double pos) {
        try {
            core_.setPosition(core_.getFocusDevice(),pos);
        } catch (Exception ex) {
            ReportingUtils.logError(ex);
        }
    }
    
    void focusBy(double dz) {
        String focusDevice = core_.getFocusDevice();
        try {
            double z = core_.getPosition(focusDevice);
            core_.setPosition(focusDevice, z + dz);
            core_.waitForDevice(focusDevice);
        } catch (Exception ex) {
            ReportingUtils.logError(ex);
        }

    }


}
