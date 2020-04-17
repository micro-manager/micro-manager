package org.micromanager.slideexplorer;

import java.awt.Rectangle;
import java.awt.geom.Point2D;

import ij.process.ImageProcessor;
import java.util.List;

import org.micromanager.internal.utils.imageanalysis.ImageUtils;

import mmcorej.CMMCore;
import org.micromanager.Studio;
import org.micromanager.data.Image;


public class Hardware {

    CMMCore core_;
    String stage_;
    Studio studio_;

    Hardware(Studio studio, CMMCore core) {
        core_ = core;
        stage_ = core_.getXYStageDevice();
        studio_ = studio;
    }

    // Camera commands -----------------------
    ImageProcessor acquireImage() {
        try {
            List<Image> snaps = studio_.live().snap(false);
            //core_.snapImage();
            //Object img = core_.getImage();
            Image snap = snaps.get(0);
            return studio_.data().ij().createProcessor(snap);
            //return ImageUtils.makeProcessor(core_, img);
        } catch (Exception e) {
            studio_.logs().logError(e);
            return null;
        }
    }

    public Rectangle getROI() {
        int roi[][] = new int[4][1];
        try {
            core_.getROI(roi[0], roi[1], roi[2], roi[3]);
        } catch (Exception e) {
            studio_.logs().logError(e);
        }
        return new Rectangle(roi[0][0], roi[1][0], roi[2][0], roi[3][0]);
    }

    public double getCurrentPixelSize() {
        return core_.getPixelSizeUm();
    }

    // Stage commands -------------------------------------------
    Point2D.Double getXYPosition() {
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
            studio_.logs().logError(e);
        }
        return null;
    }

    void setXYPosition(double x, double y) {
        try {
            core_.setXYPosition(stage_, x, y);
        } catch (Exception e) {
            studio_.logs().logError(e);
        }

    }

    void setXYPosition(Point2D.Double pos) {
        setXYPosition(pos.x, pos.y);
    }

    public void stageGo(Point2D.Double stagePos) {
        String xystage = core_.getXYStageDevice();
        Point2D.Double oldPos = getXYPosition();

        if ((oldPos.x != stagePos.x) || (oldPos.y != stagePos.y)) {
            try {
                while (core_.deviceBusy(xystage));
            } catch (Exception e) {
                studio_.logs().logError(e);
            }
            setXYPosition(stagePos.x, stagePos.y);
            try {
                while (core_.deviceBusy(xystage)) {
                    //updateStagePositionRect();
                    core_.sleep(100);
                }
            } catch (Exception e) {
                studio_.logs().logError(e);
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
            studio_.logs().logError(ex);
            return Double.POSITIVE_INFINITY;
        }
    }

    public void setZPosition(double pos) {
        try {
            core_.setPosition(core_.getFocusDevice(),pos);
        } catch (Exception ex) {
            studio_.logs().logError(ex);
        }
    }
    
    void focusBy(double dz) {
        String focusDevice = core_.getFocusDevice();
        try {
            double z = core_.getPosition(focusDevice);
            core_.setPosition(focusDevice, z + dz);
            core_.waitForDevice(focusDevice);
        } catch (Exception ex) {
            studio_.logs().logError(ex);
        }

    }


}
