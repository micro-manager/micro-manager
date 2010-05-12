package org.micromanager.slideexplorer;

import java.awt.Point;
import java.io.File;
import java.util.Random;

import ij.IJ;
import ij.ImagePlus;
import ij.io.FileSaver;
import ij.process.ImageProcessor;
import org.micromanager.utils.ImageUtils;

public class MultiTile {

    ImageProcessor proc_;
    int type_;
    int width_;
    int height_;
    String fileName_ = null;
    boolean cached_ = false;
    String tmpDirectory_ = "/tmp";

    MultiTile(int type, int width, int height) {
        type_ = type;
        width_ = width;
        height_ = height;
    }

    public synchronized ImageProcessor getImage() {
        getImageReady();
        return proc_;
    }

    public synchronized void getImageReady() {
        if (proc_ == null) {
            if (cached_ == false) {
                createCleanImage();
            } else {
                loadFromCache();
            }
        }
        proc_.setInterpolationMethod(ImageProcessor.BILINEAR);
    }

    public synchronized void setImage(ImageProcessor proc) {
        getImageReady();
        proc_.insert(proc, 0, 0);
    }

    private void createCleanImage() {
        proc_ = ImageUtils.makeProcessor(type_, width_, height_);
    }

    public synchronized void insertQuadrantImage(Point quad, ImageProcessor inProc) {
        // TODO: rewrite this as a fast algorithm. Should require only a single copy.
        getImageReady();
        inProc.setInterpolationMethod(ImageProcessor.BILINEAR);
        ImageProcessor inProcSmall = inProc.resize(width_ / 2, height_ / 2);
        proc_.insert(inProcSmall, quad.x * width_ / 2, quad.y * height_ / 2);
        cached_ = false;
    }

    public synchronized void dropFromMemory() {
        if (!cached_) {
            cacheOnDisk();
        }

        proc_ = null;
    }

    private void cacheOnDisk() {
        if (proc_ != null) {
            ImagePlus imgp = new ImagePlus("", proc_);
            FileSaver fs = new FileSaver(imgp);
            if (fileName_ == null) {
                fileName_ = tmpDirectory_ + "/" + Math.abs(randLong()) + ".tif";
            }
            fs.saveAsTiff(fileName_);
            cached_ = true;
        }
    }

    private void loadFromCache() {
        ImagePlus imgp = IJ.openImage(fileName_);
        if (imgp != null) {
            proc_ = imgp.getProcessor();
        } else { // Somehow the image got lost.
            proc_ = null;
            cached_ = false;
            getImageReady();
        }
    }

    public String toString() {
        return "Tile";
    }
    private static Random rand = null;

    public static long randLong() {
        if (rand == null) {
            rand = new Random();
        }
        return rand.nextLong();
    }

    public void wipeFromDisk() {
        if (fileName_ != null) {
            File file = new File(fileName_);
            if (file.exists()) {
                file.delete();
            }
        }
    }
}
