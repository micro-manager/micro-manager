/*
 * Project: ASI CRISP Control
 * License: BSD 3-clause, see LICENSE.md
 * Author: Brandon Simpson (brandon@asiimaging.com)
 * Copyright (c) 2014-2024, Applied Scientific Instrumentation
 */

package com.asiimaging.crisp.plot;

import java.util.ArrayList;
import java.util.List;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

/**
 * This class is a container for CRISP focus curve data.
 */
public class FocusDataSet {

   private static final String HEADER = "Time,Position,Error";

   private final ArrayList<FocusData> data;

   /**
    * Constructs an empty FocusDataSet to store focus curve data.
    */
   public FocusDataSet() {
      data = new ArrayList<>();
   }

   public void add(final FocusData focusData) {
      data.add(focusData);
   }

   public static FocusDataSet createFromCSV(final List<String> csv) {
      final FocusDataSet data = new FocusDataSet();
      data.fromCSV(csv);
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

   /**
    * Parses a concatenated String of focus curve data assembled from MS2000
    * focus curve data properties into a FocusDataSet.
    *
    * @param text the focus curve data
    */
   public void parseString(final String text) {
      // remove extra white space and split on "T:"
      final String extraSpacesRemoved = text.replaceAll("\\s+", " ");
      final String[] dataset = extraSpacesRemoved.split("T:");

      // prevent array reallocation
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
    * Create a FocusDataSet from a CSV file.
    *
    * @param array the CSV file
    */
   public void fromCSV(final List<String> array) {
      for (int i = 1; i < array.size(); i++) {
         final String[] values = array.get(i).split(",");
         data.add(new FocusData(
               Double.parseDouble(values[0]), // time
               Double.parseDouble(values[1]), // position
               Double.parseDouble(values[2])) // error
         );
      }
   }

   /**
    * Returns the FocusDataSet in CSV format.
    */
   public ArrayList<String> toCSV() {
      final ArrayList<String> csv = new ArrayList<>(data.size() + 1);
      csv.add(HEADER);
      for (final FocusData point : data) {
         csv.add(point.toStringCSV());
      }
      return csv;
   }

   /**
    * Print the FocusDataSet to the console.
    */
   public void print() {
      System.out.println("[FocusDataSet]");
      System.out.println(HEADER);
      for (final FocusData point : data) {
         System.out.println(point.toStringCSV());
      }
   }
}
