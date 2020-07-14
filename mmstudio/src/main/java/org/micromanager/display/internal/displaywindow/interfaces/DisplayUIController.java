// Copyright (C) 2020 Contributors to the Micro-Manager project
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
package org.micromanager.display.internal.displaywindow.interfaces;

import ij.ImagePlus;
import java.io.Closeable;
import javax.swing.JFrame;
import org.micromanager.data.Coords;
import org.micromanager.display.DisplaySettings;
import org.micromanager.display.internal.displaywindow.imagej.MMImageCanvas;
import org.micromanager.display.internal.imagestats.ImagesAndStats;
import org.micromanager.internal.utils.MustCallOnEDT;
import org.micromanager.internal.utils.performance.PerformanceMonitor;

/**
 *
 * @author Nick Anthony (nickmanthony at hotmail.com)
 */

/*
This interface outlines the methods that must be implemented for a class to act
as a UI controller for the `DisplayController`. To quote a comment from `DisplayController`:
"The UI controller manages the actual JFrame and all the components in it,
including interaction with ImageJ. After being closed, set to null.
Must access on EDT"
*/
public interface DisplayUIController extends Closeable {
    @MustCallOnEDT
    void applyDisplaySettings(DisplaySettings settings);
    
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
    
    @MustCallOnEDT
    public void displayImages(ImagesAndStats images);
    
    public void updateSliders(ImagesAndStats images);
    
    public void setImageInfoLabel(ImagesAndStats images);
    
    public void setNewImageIndicator(boolean show);
    
    public void setPlaybackFpsIndicator(double fps);
    
    public void expandDisplayedRangeToInclude(Coords... coords);
    
    @Override
    public void close();
    
    MMImageCanvas getIJImageCanvas();
}


