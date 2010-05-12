package org.micromanager.slideexplorer;

import ij.process.ImageProcessor;
import mmcorej.CMMCore;

import org.micromanager.image5d.Image5D;
import org.micromanager.utils.ImageUtils;
import org.micromanager.utils.ReportingUtils;

public class Image5DMosaic extends Image5D {

    private CMMCore mmc;

    
    public Image5DMosaic(String acqName_, int type, int width, int height,
            int size, int numSlices, int actualFrames, boolean b) {
        super(acqName_, type, width, height, size, numSlices, actualFrames, b);
    }


    void placePatch(int channel, int slice, int frame, int x, int y, Object patchPixels, int patchWidth, int patchHeight) {
        setCurrentPosition(x, y, channel - 1, slice - 1, frame - 1);
        ImageProcessor patchProc = ImageUtils.makeProcessor(this.getType(), patchWidth, patchHeight, patchPixels);
        getProcessor().insert(patchProc, (int) x, (int) y);
    }

    void snapAndPlacePatch(int channel, int slice, int frame, int x, int y) {
        try {
            mmc.snapImage();
            Object pix = mmc.getImage();
            int w = (int) mmc.getImageWidth();
            int h = (int) mmc.getImageHeight();
            placePatch(channel, slice, frame, x, y, pix, w, h);

        } catch (Exception e) {
            ReportingUtils.logError(e);
            return;
        }
    }

    void testPatchesInImage5D() {
        for (int t = 1; t <= this.getNFrames(); t++) {
            for (int x = 0; x <= 400; x += 50) {
                for (int y = 0; y <= 400; y += 50) {
                    for (int z = 1; z <= getNSlices(); z++) {
                        for (int c = 1; c <= getNChannels(); c++) {
                            snapAndPlacePatch(c, z, t, x, y);
                            setCurrentPosition(0, 0, c - 1, z - 1, t - 1);
                        }
                    }
                }
            }
        }
    }

}
// To get the image to appear, use:
// i5d.updateImageAndDraw();

