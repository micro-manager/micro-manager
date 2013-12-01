package org.micromanager.slideexplorer;

import ij.gui.Roi;
import ij.gui.ShapeRoi;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import org.micromanager.utils.JavaUtils;
import org.micromanager.utils.ReportingUtils;

/*
 * The Coordinates class is a centralized location for most SlideExplorer coordinate
 * mappings.
 * 
 * The "Map" is an integer coordinate system in units of single pixels. The tiled camera images' pixels correspond directly to the Map's coordinates.
 * The Tile coordinate system is in integer pairs.
 * The OffScreen coordinate system is (0,0) in the center of the display.
 * The MultiTile coordinate system is similar to Tiles, but the numbers are lower because the grid is coarser.
 *  
 * Map <--> Tile
 * Map <--> OffScreen
 * Tile <--> MultiTile
 * 
 * See org.micromanager.slideexplorer.Controller for converting between map and stage coordinates.
 */
public class Coordinates {

    // Fixed values:
    protected Dimension tileDimensions_;
    // Changing values:
    protected Point offScreenCornerInMap_;
    protected Dimension viewDimensionsOffScreen_;
    protected Dimension viewDimensionsOnScreen_;
    protected int scaleOffScreenToMap_;
    protected int zoomLevel_;
    protected Dimension roiDimensions_;

    // Setup calls.
    public Coordinates() {
        setZoomLevel(0);
        setViewCenterInMap(new Point(0, 0));
    }

    public Coordinates clone() {
        Coordinates theClone = new Coordinates();
        theClone.setZoomLevel(zoomLevel_);
        theClone.setTileDimensionsOnMap(tileDimensions_);
        theClone.setViewCenterInMap(offScreenCornerInMap_);
        theClone.setViewDimensionsOffScreen(viewDimensionsOffScreen_);
        theClone.setViewDimensionsOnScreen(viewDimensionsOnScreen_);
        return theClone;
    }

    public void setTileDimensionsOnMap(int width, int height) {
        tileDimensions_ = new Dimension(width, height);
    }

    public void setTileDimensionsOnMap(Dimension dim) {
        tileDimensions_ = (Dimension) dim.clone();
    }

    // Coordinate conversions
    public Point multiTileToULTile(Point3D multiTileIndex) {
        return new Point(multiTileIndex.i << -multiTileIndex.k, multiTileIndex.j << -multiTileIndex.k);
    }

    public Point3D tileToMultiTile(Point tileIndex) {
        return new Point3D(tileIndex.x >> -zoomLevel_, tileIndex.y >> -zoomLevel_, zoomLevel_);
    }

    public Point tileToMap(Point tileIndex) {
        return new Point(tileIndex.x * tileDimensions_.width, tileIndex.y * tileDimensions_.height);
    }

    public Point mapToTile(Point pixel) {
        return new Point(
                floorDivision(pixel.x, tileDimensions_.width),
                floorDivision(pixel.y, tileDimensions_.height));
    }

    public Point mapToOffScreen(Point mapPixel) {
        return new Point(
                floorDivision(mapPixel.x - offScreenCornerInMap_.x, scaleOffScreenToMap_),
                floorDivision(mapPixel.y - offScreenCornerInMap_.y, scaleOffScreenToMap_));
    }

    public Point offScreenToMap(Point offScreenPixel) {
        return new Point(
                (offScreenPixel.x * scaleOffScreenToMap_) + offScreenCornerInMap_.x,
                (offScreenPixel.y * scaleOffScreenToMap_) + offScreenCornerInMap_.y);
    }

    // Compound coordinate conversion
    public Point3D offScreenToMultiTile(Point offScreenPosition) {
        return mapToMultiTile(offScreenToMap(offScreenPosition));
    }

    public Point multiTileToOffScreen(Point3D multiTileIndex) {
        return mapToOffScreen(multiTileToMap(multiTileIndex));
    }

    public Point offScreenToTile(Point offScreenPosition) {
        return mapToTile(offScreenToMap(offScreenPosition));
    }

    public Point tileToOffScreen(Point tileIndex) {
        return mapToOffScreen(tileToMap(tileIndex));
    }

    public Point multiTileToMap(Point3D multiTileIndex) {
        return tileToMap(multiTileToULTile(multiTileIndex));
    }

    public Point3D mapToMultiTile(Point mapPosition) {
        return tileToMultiTile(mapToTile(mapPosition));
    }

    public Point getNearestTileFromMapPosition(Point mapPosition) {
        Point centerPosition = new Point(mapPosition.x + tileDimensions_.width / 2,
                mapPosition.y + tileDimensions_.height / 2);
        return mapToTile(centerPosition);
    }

    // Coordinates for navigation user input and display.

    public void setRoiDimensionsOnMap(Dimension roiDimensions) {
        roiDimensions_ = (Dimension) roiDimensions.clone();
    }

    public Dimension getRoiDimensionsOnMap() {
        return (Dimension) roiDimensions_.clone();
    }

    public Dimension getRoiDimensionsOffScreen() {
        return new Dimension(roiDimensions_.width/scaleOffScreenToMap_, roiDimensions_.height/scaleOffScreenToMap_);
    }

    public Point getOffScreenCenter() {
        return new Point(viewDimensionsOffScreen_.width * 3 / 2, viewDimensionsOffScreen_.height * 3 / 2);
    }

    public Point offScreenClickToMap(Point offScreenPixel) {
        Point mapPos = offScreenToMap(offScreenPixel);
        Point newMapPos = new Point(mapPos.x - tileDimensions_.width / 2, mapPos.y - tileDimensions_.height / 2);
        return newMapPos;
    }

    public Rectangle offScreenToRoiRect(Point offScreen) {
        int w = roiDimensions_.width / scaleOffScreenToMap_;
        int h = roiDimensions_.height / scaleOffScreenToMap_;
        int x = offScreen.x + (tileDimensions_.width - roiDimensions_.width)/scaleOffScreenToMap_/2;
        int y = offScreen.y + (tileDimensions_.height - roiDimensions_.height)/scaleOffScreenToMap_/2;;
        System.out.println(w + "x" + h);
        return new Rectangle(x, y, w, h);
    }

    // The following methods may be repeatedly called as user pans, zooms, and resizes the ImageWindow.
    public void setViewCenterInMap(Point viewCenterInMap) {
        offScreenCornerInMap_ = (Point) viewCenterInMap.clone();
    }

    public void setViewDimensionsOffScreen(Dimension viewDimensionsOffScreen) {
        viewDimensionsOffScreen_ = (Dimension) viewDimensionsOffScreen.clone();
    }

    public void setViewDimensionsOnScreen(Dimension viewDimensionsOnScreen) {
        viewDimensionsOnScreen_ = (Dimension) viewDimensionsOnScreen.clone();
    }

    /*
     * Pans so that target position is translated to the upper left corner.
     */
    public void panTo(Point panTargetOffScreen) {
        offScreenCornerInMap_ = offScreenToMap(panTargetOffScreen);
    }

    public void zoomIn(Point zoomTargetOffScreen) {
        panTo(new Point(viewDimensionsOffScreen_.width/4,
                viewDimensionsOffScreen_.height/4));
        setZoomLevel(zoomLevel_+1);
    }

    public void zoomOut(Point zoomTargetOffScreen) {
        panTo(new Point(-viewDimensionsOffScreen_.width/2,
                -viewDimensionsOffScreen_.height/2));
        setZoomLevel(zoomLevel_-1);
    }

    public void setZoomLevel(int zoomLevel) {
        scaleOffScreenToMap_ = 1 << -zoomLevel;
        zoomLevel_ = zoomLevel;
    }

    // Find tiles and MultiTiles in various rectangles.
    public ArrayList<Point3D> getMultiTilesOffScreen() {
        return getMultiTilesInRegion(new Rectangle(0,0,
                viewDimensionsOffScreen_.width, viewDimensionsOffScreen_.height));
    }

    public ArrayList<Point3D> getMultiTilesOnScreen() {
        return getMultiTilesInRegion(new Rectangle( viewDimensionsOnScreen_.width,
                                                    viewDimensionsOnScreen_.height,
                                                    viewDimensionsOnScreen_.width,
                                                    viewDimensionsOnScreen_.height));
    }

    public ArrayList<Point3D> getMultiTilesInRegion(Rectangle region) {
        Point ulOffScreen = new Point(region.x,region.y);
        Point lrOffScreen = new Point(region.x + region.width, region.y + region.height);

        Point3D ulMultiTile = offScreenToMultiTile(ulOffScreen);
        Point3D lrMultiTile = offScreenToMultiTile(lrOffScreen);

        ArrayList<Point3D> multiTiles = new ArrayList<Point3D>();

        int k = ulMultiTile.k;
        for (int i = ulMultiTile.i; i <= lrMultiTile.i; ++i) {
            for (int j = ulMultiTile.j; j <= lrMultiTile.j; ++j) {
                multiTiles.add(new Point3D(i, j, k));
            }
        }

        return multiTiles;
    }

    public ArrayList<Point> getTilesOnScreen() {
        Point ulOffScreen = new Point(viewDimensionsOnScreen_.width, viewDimensionsOnScreen_.height);
        Point lrOffScreen = new Point(2*viewDimensionsOnScreen_.width, 2*viewDimensionsOnScreen_.height);

        Point ulTile = offScreenToTile(ulOffScreen);
        Point lrTile = offScreenToTile(lrOffScreen);

        ArrayList<Point> tiles = new ArrayList<Point>();

        for (int i = ulTile.x; i <= lrTile.x; ++i) {
            for (int j = ulTile.y; j <= lrTile.y; ++j) {
                tiles.add(new Point(i, j));
            }
        }

        return tiles;
    }

    public Roi roiOffScreenToMap(Roi roi) {
        if (roi == null)
            return null;
        
        ReportingUtils.logMessage("prev bounding rect: " + roi.getBounds());
        ShapeRoi shapeRoi = new ShapeRoi(roi);
        Rectangle rect = shapeRoi.getBounds();
        Point ulCorner = offScreenToMap(new Point(rect.x-viewDimensionsOffScreen_.width/2, rect.y-viewDimensionsOffScreen_.height/2));
        rect.x = ulCorner.x;
        rect.y = ulCorner.y;

        
        ReportingUtils.logMessage("newRect: " + rect);
        Shape shape;

        try {
            shape = (Shape) JavaUtils.invokeRestrictedMethod(shapeRoi, shapeRoi.getClass(), "getShape");
        } catch (Exception ex) {
            ReportingUtils.logError(ex, "Can't get the Shape from ShapeRoi");
            return null;
        }

        AffineTransform at = new AffineTransform();
        at.scale(scaleOffScreenToMap_, scaleOffScreenToMap_);
        Shape scaledShape = at.createTransformedShape(shape);
        ShapeRoi scaledShapeRoi = new ShapeRoi(scaledShape);
        scaledShapeRoi.setLocation(rect.x, rect.y);

        return scaledShapeRoi;
    }

    /*
     * This method divides negative integers in a more consistent way,
     * so that -3/2 --> -2, and 3/2 --> 1.
     */
    public int floorDivision(int a, int b) {
        if (a < 0) {
            if (b < 0) {
                return -a / -b;
            } else {
                return -(-a / b) - (-a % b != 0 ? 1 : 0);
            }
        } else if (b < 0) {
            return -(a / -b) - (a % -b != 0 ? 1 : 0);
        } else {
            return a / b;
        }
    }
}
