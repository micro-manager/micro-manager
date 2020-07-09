/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.display.internal.displaywindow.interfaces;

import ij.ImagePlus;
import java.io.Closeable;
import javax.swing.JFrame;
import org.micromanager.data.Coords;
import org.micromanager.display.DisplaySettings;
import org.micromanager.display.internal.imagestats.ImagesAndStats;
import org.micromanager.internal.utils.MustCallOnEDT;
import org.micromanager.internal.utils.performance.PerformanceMonitor;

/**
 *
 * @author nicke
 */
public interface Controllable extends Closeable {
    @MustCallOnEDT
    void applyDisplaySettings(DisplaySettings settings);
    
    //
    // Interface exposed for use by ImageJBridge and its associated objects,
    // presenting what ImageJ should think the current state of the display is.
    //
    double getDisplayIntervalQuantile(double q);
    
    @MustCallOnEDT
    ImagePlus getIJImagePlus();
    
    @MustCallOnEDT
    void overlaysChanged();
    
    void resetDisplayIntervalEstimate();
    
    void setPerformanceMonitor(PerformanceMonitor perfMon);

    void updateTitle();
    
    void zoomIn();

    void zoomOut();
    
    @MustCallOnEDT
    JFrame getFrame();
    
    @MustCallOnEDT
    void setVisible(boolean visible);
    
    @MustCallOnEDT
    void toFront();
    
    public void displayImages(ImagesAndStats images);
    
    public void updateSliders(ImagesAndStats images);
    
    public void setImageInfoLabel(ImagesAndStats images);
    
    public void setNewImageIndicator(boolean show);
    
    public void setPlaybackFpsIndicator(double fps);
    
    public void expandDisplayedRangeToInclude(Coords... coords);
    
    @Override
    public void close();
}


