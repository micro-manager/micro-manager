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
