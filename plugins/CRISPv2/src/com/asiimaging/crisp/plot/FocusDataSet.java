///////////////////////////////////////////////////////////////////////////////
// AUTHOR:       Brandon Simpson
//
// COPYRIGHT:    Applied Scientific Instrumentation, 2020
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

package com.asiimaging.crisp.plot;

import java.util.ArrayList;
import java.util.List;

import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

/**
 * This class is a container for CRISP focus curve data.
 * 
 */
public class FocusDataSet {

    private final ArrayList<FocusData> data;
    
    /**
     * Constructs an empty FocusDataSet to store focus curve data.
     */
    public FocusDataSet() {
        data = new ArrayList<FocusData>();
    }
    
//    @Override
//    public String toString() {
//        return ""; // TODO:
//    }

    public static FocusDataSet createFromCSV(final List<String> csv) {
        final FocusDataSet data = new FocusDataSet();
        data.parseCSV(csv);
        return data;
    }
    
    public XYDataset createXYSeries() {
        final XYSeriesCollection dataset = new XYSeriesCollection();
        final XYSeries series = new XYSeries("Focus Curve Data", false);
        for (final FocusData point : data) {
            series.add(point.getPosition(), point.getError());
        }
        dataset.addSeries(series);
        return dataset;
    }
    
    public void parseString(final String text) {
        // remove extra white space and split on "T:"
        final String extraSpacesRemoved = text.replaceAll("\\s+", " ");
        final String[] dataset = extraSpacesRemoved.split("T:");
        
        // prevent array reallocations
        data.ensureCapacity(dataset.length);
        data.clear();
        
        // skip the first element, add data to the ArrayList
        for (int i = 1; i < dataset.length; i++) {
            final String[] values = dataset[i].trim().split(" ");
            data.add(new FocusData(
                Double.parseDouble(values[0]), // time
                Double.parseDouble(values[1]), // position
                Double.parseDouble(values[2])) // error
            );
        }
    }
    
    // TODO meant to parse well formed input from file being opened... doc the format
    // TODO maybe use a method to verify array?
    /**
     * 
     * 
     * @param array
     */
    public void parseCSV(final List<String> array) {
        for (int i = 1; i < array.size(); i++) {
            final String[] values = array.get(i).split(",");
            data.add(new FocusData(
                Double.parseDouble(values[0]), // time
                Double.parseDouble(values[1]), // position
                Double.parseDouble(values[2])) // error
            );
        }
    }
    
    public void print() {
        System.out.println("[FocusDataSet]");
        for (final FocusData point : data) {
            System.out.println(point.toStringCSV());
        }
    }
}
