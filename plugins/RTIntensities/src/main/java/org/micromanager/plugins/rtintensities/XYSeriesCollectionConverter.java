package org.micromanager.plugins.rtintensities;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.micromanager.internal.utils.ReportingUtils;


public class XYSeriesCollectionConverter {
   /**
    * Converts an XYSeriesCollection to a CSV formatted String with metadata.
    * Includes metadata header and combines all x-values across series.
    *
    * @param collection The XYSeriesCollection to convert
    * @param metadata Optional key-value pairs of metadata to include in header
    * @return CSV formatted string representation of all series
    */
   public static String toCSV(XYSeriesCollection collection, String... metadata) {
      if (collection == null) {
         throw new IllegalArgumentException("Collection cannot be null");
      }

      StringBuilder csv = new StringBuilder();

      // Add metadata section
      csv.append("# Generated: ").append(LocalDateTime.now()
                  .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append("\n");
      csv.append("# Number of series: ").append(collection.getSeriesCount()).append("\n");

      // Add custom metadata if provided
      if (metadata != null) {
         for (int i = 0; i < metadata.length; i += 2) {
            if (i + 1 < metadata.length) {
               csv.append("# ").append(metadata[i]).append(": ").append(metadata[i + 1])
                     .append("\n");
            }
         }
      }
      csv.append("#\n"); // Empty line to separate metadata from data

      // Collect all unique x values across all series
      Set<Double> allXValues = new TreeSet<>();
      for (int seriesIndex = 0; seriesIndex < collection.getSeriesCount(); seriesIndex++) {
         XYSeries series = collection.getSeries(seriesIndex);
         for (int itemIndex = 0; itemIndex < series.getItemCount(); itemIndex++) {
            allXValues.add(series.getX(itemIndex).doubleValue());
         }
      }

      // Create header row with series names
      csv.append("Time(ms)");
      for (int i = 0; i < collection.getSeriesCount(); i++) {
         csv.append(",").append(collection.getSeries(i).getKey());
      }
      csv.append("\n");

      // Add data rows
      for (Double x : allXValues) {
         csv.append(x);
         for (int seriesIndex = 0; seriesIndex < collection.getSeriesCount(); seriesIndex++) {
            XYSeries series = collection.getSeries(seriesIndex);
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
    * Parses a CSV string (in the format produced by toCSV) back into an XYSeriesCollection.
    *
    * @param csvContent The CSV string to parse
    * @return CSVParseResult containing the parsed collection and metadata
    * @throws IOException If there's an error reading the CSV content
    */
   public static CSVParseResult fromCSV(String csvContent) throws IOException {
      if (csvContent == null || csvContent.trim().isEmpty()) {
         throw new IllegalArgumentException("CSV content cannot be null or empty");
      }

      Map<String, String> metadata = new HashMap<>();
      XYSeriesCollection collection = new XYSeriesCollection();

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
            collection.addSeries(new XYSeries(headers[i].trim()));
         }

         // Parse data rows
         while ((line = reader.readLine()) != null) {
            String[] values = line.split(",");
            double x = Double.parseDouble(values[0].trim());

            // Add values to each series
            for (int i = 0; i < collection.getSeriesCount(); i++) {
               int valueIndex = i + 1;
               if (valueIndex < values.length && !values[valueIndex].trim().isEmpty()) {
                  double y = Double.parseDouble(values[valueIndex].trim());
                  collection.getSeries(i).add(x, y);
               }
            }
         }
      } catch (IOException e) {
         ReportingUtils.logError(e);
      }

      return new CSVParseResult(collection, metadata);
   }

   /**
    * Container class to hold both the parsed XYSeriesCollection and metadata.
    */
   public static class CSVParseResult {
      private final XYSeriesCollection collection;
      private final Map<String, String> metadata;

      public CSVParseResult(XYSeriesCollection collection, Map<String, String> metadata) {
         this.collection = collection;
         this.metadata = metadata;
      }

      public XYSeriesCollection getCollection() {
         return collection;
      }

      public Map<String, String> getMetadata() {
         return metadata;
      }
   }

}
