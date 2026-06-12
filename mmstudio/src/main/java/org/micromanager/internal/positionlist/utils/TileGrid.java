///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------

//AUTHOR:       Nico Stuurman, nico@cmp.ucsf.edu

//COPYRIGHT:    University of California, San Francisco, 2008 - 2024

//LICENSE:      This file is distributed under the BSD license.
//License text is included with the source distribution.

//This file is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty
//of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.

//IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.


package org.micromanager.internal.positionlist.utils;

/**
 * Immutable description of the tile grid geometry computed by
 * {@link TileCreator#computeGrid}.  Holds the number of tiles in each
 * direction and the step vectors needed to map a tile (column, row) to its
 * center in stage microns.  Shared by {@code createTiles} (to generate the
 * position list) and the Refine Z overview (to draw cells and find tile
 * centers), so both stay in sync.
 *
 * <p>The "step vectors" express how stage coordinates change when moving one
 * tile to the right (stepX*) or one tile down (stepY*).  With an affine
 * transform these may contain rotation/reflection, hence the four components.
 */
public final class TileGrid {
   public final int nrImagesX;
   public final int nrImagesY;
   public final double centerX;     // stage um, bounding-box midpoint
   public final double centerY;
   public final double stepXdx;     // stage-X component of "one step right"
   public final double stepXdy;     // stage-Y component of "one step right"
   public final double stepYdx;     // stage-X component of "one step down"
   public final double stepYdy;     // stage-Y component of "one step down"
   public final double tileSizeXUm;
   public final double tileSizeYUm;
   public final double overlapXUm;
   public final double overlapYUm;
   public final String xyStage;

   /**
    * Constructs a TileGrid.  All values are in stage microns except the tile
    * counts.
    *
    * @param nrImagesX number of tiles in X
    * @param nrImagesY number of tiles in Y
    * @param centerX stage X of the grid center
    * @param centerY stage Y of the grid center
    * @param stepXdx stage-X component of moving one tile right
    * @param stepXdy stage-Y component of moving one tile right
    * @param stepYdx stage-X component of moving one tile down
    * @param stepYdy stage-Y component of moving one tile down
    * @param tileSizeXUm tile width in microns
    * @param tileSizeYUm tile height in microns
    * @param overlapXUm overlap in X in microns
    * @param overlapYUm overlap in Y in microns
    * @param xyStage name of the XY stage
    */
   public TileGrid(int nrImagesX, int nrImagesY, double centerX, double centerY,
                   double stepXdx, double stepXdy, double stepYdx, double stepYdy,
                   double tileSizeXUm, double tileSizeYUm, double overlapXUm,
                   double overlapYUm, String xyStage) {
      this.nrImagesX = nrImagesX;
      this.nrImagesY = nrImagesY;
      this.centerX = centerX;
      this.centerY = centerY;
      this.stepXdx = stepXdx;
      this.stepXdy = stepXdy;
      this.stepYdx = stepYdx;
      this.stepYdy = stepYdy;
      this.tileSizeXUm = tileSizeXUm;
      this.tileSizeYUm = tileSizeYUm;
      this.overlapXUm = overlapXUm;
      this.overlapYUm = overlapYUm;
      this.xyStage = xyStage;
   }

   /**
    * Returns the logical (snaked) column for screen column x on row y.
    * Even rows go left-to-right; odd rows go right-to-left.  Matches the
    * ordering used by {@link TileCreator#createTiles}.
    *
    * @param x screen column index
    * @param y row index
    * @return logical column index
    */
   public int snakeColumn(int x, int y) {
      if ((y & 1) == 1) {
         return nrImagesX - x - 1;
      }
      return x;
   }

   /**
    * Returns the stage X (microns) of the center of the tile at the given
    * logical column and row.
    *
    * @param tmpX logical (snaked) column index, see {@link #snakeColumn}
    * @param y row index
    * @return stage X coordinate in microns
    */
   public double tileCenterX(int tmpX, int y) {
      double offX = tmpX - (nrImagesX - 1) / 2.0;
      double offY = y - (nrImagesY - 1) / 2.0;
      return centerX + offX * stepXdx + offY * stepYdx;
   }

   /**
    * Returns the stage Y (microns) of the center of the tile at the given
    * logical column and row.
    *
    * @param tmpX logical (snaked) column index, see {@link #snakeColumn}
    * @param y row index
    * @return stage Y coordinate in microns
    */
   public double tileCenterY(int tmpX, int y) {
      double offX = tmpX - (nrImagesX - 1) / 2.0;
      double offY = y - (nrImagesY - 1) / 2.0;
      return centerY + offX * stepXdy + offY * stepYdy;
   }
}
