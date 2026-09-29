package org.micromanager.data;


import org.micromanager.PropertyMap;

/**
 * Metadata describing a multi-well plate, modelled after the OME Plate element.
 *
 * <p>There are three distinct identity fields:
 * <ul>
 *   <li>{@link #getPlateID()} — a machine-generated unique key (e.g. UUID) used internally
 *       by OME-XML to link Wells and WellSamples to this Plate. It is auto-generated and
 *       should not be entered or interpreted by users.</li>
 *   <li>{@link #getPlateName()} — a human-readable label chosen by the user for this
 *       specific plate run (e.g. "Batch-42-A").</li>
 *   <li>{@link #getPlateExternalIdentifier()} — a barcode, LIMS ID, or other reference
 *       that ties this plate to an external tracking system. This is the primary
 *       traceability field and is entered by the user.</li>
 * </ul>
 */
public interface MultiWellPlate {
   enum WellNamingConvention {
      LETTER,
      NUMBER
   }

   /**
    * Formats a 0-based (row, column) pair as a well label, e.g. (1, 5) -&gt; "B6"
    * when rows are named with letters and columns with numbers.
    *
    * <p>LETTER naming uses a bijective base-26 sequence: A-Z, then AA, AB, ...
    * NUMBER naming is simply the 1-based index.
    *
    * @param row 0-based row index
    * @param col 0-based column index
    * @param rowConvention naming convention for the row part
    * @param colConvention naming convention for the column part
    * @return the well label
    * @throws IllegalArgumentException if row or col is negative
    */
   static String wellLabel(int row, int col, WellNamingConvention rowConvention,
         WellNamingConvention colConvention) {
      if (row < 0 || col < 0) {
         throw new IllegalArgumentException(
               "Negative well index: row=" + row + " col=" + col);
      }
      return indexToLabel(row, rowConvention) + indexToLabel(col, colConvention);
   }

   /**
    * Parses a well label such as "B6" into a 0-based (row, column) pair.
    *
    * <p>The label is split according to the given naming conventions: a LETTER
    * part matches a run of letters, a NUMBER part a run of digits. This is the
    * inverse of {@link #wellLabel}.
    *
    * @param label the well label, e.g. "B6"
    * @param rowConvention naming convention for the row part
    * @param colConvention naming convention for the column part
    * @return a two-element array, {row, col}, both 0-based
    * @throws IllegalArgumentException if the label does not match the conventions
    */
   static int[] parseWellLabel(String label, WellNamingConvention rowConvention,
         WellNamingConvention colConvention) {
      if (label == null || label.isEmpty()) {
         throw new IllegalArgumentException("Empty well label");
      }
      // The row part is the leading run of characters valid for its convention;
      // the column part is whatever remains.
      int split = 0;
      while (split < label.length() && isValidFor(label.charAt(split), rowConvention)) {
         split++;
      }
      String rowPart = label.substring(0, split);
      String colPart = label.substring(split);
      if (rowPart.isEmpty() || colPart.isEmpty()) {
         throw new IllegalArgumentException("Malformed well label: " + label);
      }
      for (int i = 0; i < colPart.length(); i++) {
         if (!isValidFor(colPart.charAt(i), colConvention)) {
            throw new IllegalArgumentException("Malformed well label: " + label);
         }
      }
      return new int[] {labelToIndex(rowPart, rowConvention),
            labelToIndex(colPart, colConvention)};
   }

   /**
    * Returns true if c is a valid character for the given naming convention.
    */
   static boolean isValidFor(char c, WellNamingConvention convention) {
      return convention == WellNamingConvention.LETTER
            ? Character.isLetter(c) : Character.isDigit(c);
   }

   /**
    * Converts a 0-based index to its label under the given convention.
    */
   static String indexToLabel(int index, WellNamingConvention convention) {
      if (convention == WellNamingConvention.NUMBER) {
         return Integer.toString(index + 1);
      }
      // Bijective base-26: least significant letter first, then reverse.
      StringBuilder sb = new StringBuilder();
      int n = index + 1;
      while (n > 0) {
         sb.append((char) ('A' + (n - 1) % 26));
         n = (n - 1) / 26;
      }
      return sb.reverse().toString();
   }

   /**
    * Converts a label part to its 0-based index under the given convention.
    */
   static int labelToIndex(String part, WellNamingConvention convention) {
      if (convention == WellNamingConvention.NUMBER) {
         try {
            int n = Integer.parseInt(part);
            if (n < 1) {
               throw new IllegalArgumentException("Well index below 1: " + part);
            }
            return n - 1;
         } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Malformed numeric well part: " + part);
         }
      }
      int n = 0;
      String upper = part.toUpperCase();
      for (int i = 0; i < upper.length(); i++) {
         char c = upper.charAt(i);
         if (c < 'A' || c > 'Z') {
            throw new IllegalArgumentException("Malformed letter well part: " + part);
         }
         n = n * 26 + (c - 'A' + 1);
      }
      return n - 1;
   }

   interface FromPropertyMapBuilder {
      MultiWellPlate build(PropertyMap map);
   }

   /**
    * Builder for MultiWellPlate, see {@link MultiWellPlate} for function descriptions.
    */
   interface Builder {
      Builder plateID(String plateID);

      Builder plateName(String plateName);

      Builder plateDescription(String plateDescription);

      Builder plateExternalIdentifier(String plateExternalIdentifier);

      Builder plateRows(int plateRows);

      Builder plateColumns(int plateColumns);

      Builder plateRowNamingConvention(WellNamingConvention plateRowNamingConvention);

      Builder plateColumnNamingConvention(WellNamingConvention plateColumnNamingConvention);

      Builder plateWellOriginXUm(double plateWellOriginXUm);

      Builder plateWellOriginYUm(double plateWellOriginYUm);

      Builder plateStatus(String plateStatus);

      MultiWellPlate build();
   }

   /**
    * Get the number of Columns of the Plate.
    *
    * @return the number of Columns of the Plate
    */
   Integer getPlateColumns();

   /**
    * Returns a description of the Plate.
    *
    * @return a description of the Plate
    */
   String getPlateDescription();

   /**
    * Returns the external identifier (e.g. barcode or LIMS ID) for this plate.
    *
    * <p>This field is intended for traceability: it ties the plate to an entry in an
    * external system such as a Laboratory Information Management System (LIMS) or a
    * physical barcode label on the plate. Unlike {@link #getPlateID()}, which is
    * auto-generated, this value is entered by the user and may be left blank if no
    * external tracking is used.
    *
    * @return the external identifier string, or null/empty if not set
    */
   String getPlateExternalIdentifier();

   /**
    * Returns the system-level unique identifier for this plate instance.
    *
    * <p>This ID is intended for machine use: it must be unique within the OME-XML document
    * so that Wells and WellSamples can reference this Plate unambiguously. It is
    * auto-generated (e.g. a UUID) when plate metadata is created and should not be
    * entered or interpreted by users. For a human-readable label use
    * {@link #getPlateName()}; for a barcode or LIMS reference use
    * {@link #getPlateExternalIdentifier()}.
    *
    * @return the auto-generated unique identifier for this plate
    */
   String getPlateID();

   /**
    * Returns a human-readable name for this plate run.
    *
    * <p>The Name identifies the plate to the user (e.g. "Batch-42-A"). It is
    * distinct from {@link #getPlateID()}, which is a machine-generated key,
    * and from {@link #getPlateExternalIdentifier()}, which is a barcode or LIMS
    * reference. If a plate name is not available, OME/OMERO will fall back to
    * "Start time - End time", or finally to the plate ID.
    *
    * @return a human-readable name of the plate, or null/empty if not set
    */
   String getPlateName();

   /**
    * Returns the RowNamingConvention property of Plate, either LETTER or NUMBER.
    *
    * @return the RowNamingConvention property of Plate
    */
   WellNamingConvention getPlateRowNamingConvention();

   /**
    * Returns the ColumnNamingConvention property of Plate, either LETTER or NUMBER.
    *
    * @return the ColumnNamingConvention property of Plate
    */
   WellNamingConvention getPlateColumnNamingConvention();

   /**
    * Returns the number of Rows of the Plate.
    *
    * @return the number of Rows of the Plate
    */
   Integer getPlateRows();

   /**
    * Returns the status of the plate, i.e. a textual annotation of the current
    * state of the plate with respect to the experiment work-flow; e.g.
    * 1. Seed cell: done; 2. Transfection: done;  3. Gel doc: todo.
    *
    * @return the status of the plate
    */
   String getPlateStatus();

   /**
    * This defines the X position to use for the origin of the
    *             fields (individual images) taken in a well. It is used
    *             with the X in the WellSample to display the fields
    *             in the correct position relative to each other. Each Well
    *             in the plate has the same well origin. We use microns as the unit.
    *             In the OMERO clients by convention we display the WellOrigin
    *             in the center of the view.
    *
    * @return the origin for the well sample positions in the well
    */
   Double getPlateWellOriginXUm();

   /**
    * This defines the Y position to use for the origin of the
    *             fields (individual images) taken in a well. It is used
    *             with the Y in the WellSample to display the fields
    *             in the correct position relative to each other. Each Well
    *             in the plate has the same well origin. We use microns as the unit.
    *             In the OMERO clients by convention we display the WellOrigin
    *             in the center of the view.
    *
    * @return the origin for the well sample positions in the well
    */
   Double getPlateWellOriginYUm();

   PropertyMap toPropertyMap();

}
