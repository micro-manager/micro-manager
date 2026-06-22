package org.micromanager.explorer;

import java.util.Arrays;
import java.util.List;

/**
 * Describes the physical geometry of a sample vessel (coverslip or multi-well plate).
 * Used by the Explorer to draw an outline overlay and auto-zoom to the vessel extent.
 *
 * <p>Simple coverslips carry only overall width/height. Multi-well plates additionally
 * specify the well layout (position, size, and shape of each well).
 *
 * <p>Built-in types are available via {@link #builtIn()}. The class is immutable and
 * can be extended with additional types without modifying existing code.
 */
public final class VesselType {

   /** Identifies which physical point on the vessel the operator placed the stage on. */
   public enum AnchorType {
      TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, CENTER
   }

   private final String name;
   private final double widthUm;
   private final double heightUm;
   // Multi-well fields — all zero for simple coverslips.
   private final int wellCols;
   private final int wellRows;
   private final double wellWidthUm;
   private final double wellHeightUm;
   private final double wellSpacingXUm;
   private final double wellSpacingYUm;
   private final double firstWellXUm; // distance from vessel left edge to first well center
   private final double firstWellYUm; // distance from vessel top edge to first well center
   private final boolean wellsCircular;

   /**
    * Full constructor — use for multi-well plates.
    * Set wellCols/wellRows to 0 for simple rectangle vessels.
    */
   public VesselType(String name, double widthUm, double heightUm,
                     int wellCols, int wellRows,
                     double wellWidthUm, double wellHeightUm,
                     double wellSpacingXUm, double wellSpacingYUm,
                     double firstWellXUm, double firstWellYUm,
                     boolean wellsCircular) {
      this.name = name;
      this.widthUm = widthUm;
      this.heightUm = heightUm;
      this.wellCols = wellCols;
      this.wellRows = wellRows;
      this.wellWidthUm = wellWidthUm;
      this.wellHeightUm = wellHeightUm;
      this.wellSpacingXUm = wellSpacingXUm;
      this.wellSpacingYUm = wellSpacingYUm;
      this.firstWellXUm = firstWellXUm;
      this.firstWellYUm = firstWellYUm;
      this.wellsCircular = wellsCircular;
   }

   /** Convenience constructor for a simple rectangular vessel (no individual wells). */
   public VesselType(String name, double widthUm, double heightUm) {
      this(name, widthUm, heightUm, 0, 0, 0, 0, 0, 0, 0, 0, false);
   }

   // ── Built-in types ──────────────────────────────────────────────────────────

   public static final VesselType NONE =
         new VesselType("None", 0, 0);
   public static final VesselType CS_18X18 =
         new VesselType("Coverslip 18x18mm", 18000, 18000);
   public static final VesselType CS_22X22 =
         new VesselType("Coverslip 22x22mm", 22000, 22000);
   public static final VesselType CS_22X60 =
         new VesselType("Coverslip 22x60mm", 60000, 22000);
   // Multi-well plate dimensions from SBS/ANSI standard (sourced from HCS SBSPlate.java).
   public static final VesselType WELL_6 =
         new VesselType("6-Well Plate", 127760, 85470,
               3, 2, 34800, 34800, 39120, 39120, 24760, 23160, true);
   public static final VesselType WELL_24 =
         new VesselType("24-Well Plate", 127500, 85250,
               6, 4, 15540, 15540, 19300, 19300, 17050, 13670, true);
   public static final VesselType WELL_96 =
         new VesselType("96-Well Plate", 127760, 85480,
               12, 8, 8000, 8000, 9000, 9000, 14380, 11240, true);
   public static final VesselType WELL_384 =
         new VesselType("384-Well Plate", 127760, 85480,
               24, 16, 4000, 4000, 4500, 4500, 12130, 8990, false);

   /** Returns all built-in vessel types in display order. */
   public static List<VesselType> builtIn() {
      return Arrays.asList(
            NONE, CS_18X18, CS_22X22, CS_22X60,
            WELL_6, WELL_24, WELL_96, WELL_384);
   }

   // ── Static well-label utilities ──────────────────────────────────────────────

   /** Returns the row label for the given 0-based row index: 0 → "A", 1 → "B", … */
   public static String getRowLabel(int row) {
      return String.valueOf((char) ('A' + row));
   }

   /** Returns the well label for 0-based row/col: (0,0) → "A1", (1,11) → "B12", … */
   public static String getWellLabel(int row, int col) {
      return getRowLabel(row) + (col + 1);
   }

   // ── Accessors ────────────────────────────────────────────────────────────────

   public String getName() {
      return name;
   }

   public double getWidthUm() {
      return widthUm;
   }

   public double getHeightUm() {
      return heightUm;
   }

   public boolean isNone() {
      return widthUm <= 0;
   }

   public boolean isMultiWell() {
      return wellCols > 0 && wellRows > 0;
   }

   public int getWellCols() {
      return wellCols;
   }

   public int getWellRows() {
      return wellRows;
   }

   public double getWellWidthUm() {
      return wellWidthUm;
   }

   public double getWellHeightUm() {
      return wellHeightUm;
   }

   public double getWellSpacingXUm() {
      return wellSpacingXUm;
   }

   public double getWellSpacingYUm() {
      return wellSpacingYUm;
   }

   public double getFirstWellXUm() {
      return firstWellXUm;
   }

   public double getFirstWellYUm() {
      return firstWellYUm;
   }

   public boolean isWellsCircular() {
      return wellsCircular;
   }

   @Override
   public String toString() {
      return name;
   }
}
