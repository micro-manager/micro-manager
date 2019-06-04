
package org.micromanager.projector;

import java.awt.Polygon;
import java.awt.geom.AffineTransform;
import java.util.Map;

/**
 * Class to hold information relating camera and projection device.
 * Contains the affine transforms to relate one to the other.
 * Also contains information about the camera binning and ROI during calibration 
 * that can be used to correct if the current ROI/binning is different.
 * 
 * @author Nico
 */
public class Mapping {
    private final Map<Polygon, AffineTransform> transformMap_;
    private final long cameraWidth_;
    private final long cameraHeight_;
    private final int cameraBinning_;
    
    private Mapping(Map<Polygon, AffineTransform> transformMap, long cameraWidth,
            long cameraHeight, int cameraBinning) {
        transformMap_ = transformMap;
        cameraWidth_ = cameraWidth;
        cameraHeight_ = cameraHeight;
        cameraBinning_ = cameraBinning;
    }
    
    public static class Builder {

        private Map<Polygon, AffineTransform> transformMap_;
        private long cameraWidth_;
        private long cameraHeight_;
        private int cameraBinning_ = 1;

        public Builder() {        }
        public Builder setMap(Map<Polygon, AffineTransform> transformMap) {
            transformMap_ = transformMap;
            return this;
        }
        public Builder setWidth(final long cameraWidth) {
            cameraWidth_ = cameraWidth;
            return this;
        }
        public Builder setHeight(final long cameraHeight) {
            cameraHeight_ = cameraHeight;
            return this;
        }
        public Builder setBinning(final int cameraBinning) {
            cameraBinning_ = cameraBinning;
            return this;
        }
        public Mapping build() {
            return new Mapping(transformMap_, cameraWidth_, cameraHeight_, cameraBinning_);
        }
    }
    
    public Map<Polygon, AffineTransform> getMap() {
        return transformMap_;
    }
    public long getCameraWidth() {
        return cameraWidth_;
    }
    public long getCameraHeight() {
        return cameraHeight_;
    }
    public int getBinning() {
        return cameraBinning_;
    }

}
