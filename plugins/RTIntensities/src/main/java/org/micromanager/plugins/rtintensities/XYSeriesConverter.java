package org.micromanager.plugins.rtintensities;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.jfree.data.xy.XYSeries;

public class XYSeriesConverter {
   /**
    * Converts multiple XYSeries to a CSV formatted String with metadata.
    * Includes metadata header and combines all x-values across series.
    *
    * @param seriesList Array of XYSeries to convert
    * @param metadata   Optional key-value pairs of metadata to include in header
    * @return CSV formatted string representation of all series
    */
   public static String toCSV(XYSeries[] seriesList, String... metadata) {
      if (seriesList == null || seriesList.length == 0) {
         throw new IllegalArgumentException("Series array cannot be null or empty");
      }

      StringBuilder csv = new StringBuilder();

      // Add metadata section
      csv.append("# Generated: ").append(LocalDateTime.now().format(
            DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append("\n");
      csv.append("# Number of series: ").append(seriesList.length).append("\n");

      // Add custom metadata if provided
      if (metadata != null) {
         for (int i = 0; i < metadata.length; i += 2) {
            if (i + 1 < metadata.length) {
               csv.append("# ").append(metadata[i]).append(": ")
                     .append(metadata[i + 1]).append("\n");
            }
         }
      }
      csv.append("#\n"); // Empty line to separate metadata from data

      // Collect all unique x values across all series
      Set<Double> allXValues = new TreeSet<>();
      for (XYSeries series : seriesList) {
         for (int i = 0; i < series.getItemCount(); i++) {
            allXValues.add(series.getX(i).doubleValue());
         }
      }

      // Create header row with series names
      csv.append("X");
      for (XYSeries series : seriesList) {
         csv.append(",").append(series.getKey());
      }
      csv.append("\n");

      // Add data rows
      for (Double x : allXValues) {
         csv.append(x);
         for (XYSeries series : seriesList) {
            csv.append(",");
            int index = series.indexOf(x);
            if (index >= 0) {
               csv.append(series.getY(index));
            }
            // If no y value exists for this x in this series, leave empty
         }
         csv.append("\n");
      }

      return csv.toString();
   }


   /**
    * Converts a CSV string (in the format produced by toCSV) back into an array of XYSeries.
    * Handles metadata headers and multiple series.
    *
    * @param csvContent The CSV string to parse
    * @return Map containing both the parsed XYSeries array and extracted metadata
    * @throws IOException If there's an error reading the CSV content
    */
   public static CSVParseResult fromCSV(String csvContent) throws IOException {
      if (csvContent == null || csvContent.trim().isEmpty()) {
         throw new IllegalArgumentException("CSV content cannot be null or empty");
      }

      Map<String, String> metadata = new HashMap<>();
      List<XYSeries> seriesList = new ArrayList<>();

      try (BufferedReader reader = new BufferedReader(new StringReader(csvContent))) {
         String line;

         // Parse metadata
         while ((line = reader.readLine()) != null) {
            if (line.startsWith("# ")) {
               String metadataLine = line.substring(2).trim();
               int colonIndex = metadataLine.indexOf(':');
               if (colonIndex > 0) {
                  String key = metadataLine.substring(0, colonIndex).trim();
                  String value = metadataLine.substring(colonIndex + 1).trim();
                  metadata.put(key, value);
               }
            } else if (line.equals("#")) {
               continue;
            } else {
               break; // End of metadata section
            }
         }

         // Parse header row to get series names
         String[] headers = line.split(",");
         // Skip first column (X values)
         for (int i = 1; i < headers.length; i++) {
            seriesList.add(new XYSeries(headers[i].trim()));
         }

         // Parse data rows
         while ((line = reader.readLine()) != null) {
            String[] values = line.split(",");
            double x = Double.parseDouble(values[0].trim());

            // Add values to each series
            for (int i = 0; i < seriesList.size(); i++) {
               int valueIndex = i + 1;
               if (valueIndex < values.length && !values[valueIndex].trim().isEmpty()) {
                  double y = Double.parseDouble(values[valueIndex].trim());
                  seriesList.get(i).add(x, y);
               }
            }
         }
      }

      return new CSVParseResult(
            seriesList.toArray(new XYSeries[0]),
            metadata
      );
   }

   /**
    * Container class to hold both the parsed XYSeries array and metadata
    */
   public static class CSVParseResult {
      private final XYSeries[] series;
      private final Map<String, String> metadata;

      public CSVParseResult(XYSeries[] series, Map<String, String> metadata) {
         this.series = series;
         this.metadata = metadata;
      }

      public XYSeries[] getSeries() {
         return series;
      }

      public Map<String, String> getMetadata() {
         return metadata;
      }
   }

}