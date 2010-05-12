package org.micromanager.slideexplorer;

import ij.process.ImageProcessor;

import java.awt.Dimension;
import java.awt.Point;
import java.io.File;
import java.util.Hashtable;

import org.micromanager.utils.ImageUtils;

public class MultiTileCache {
	
	protected int minZoomLevel_;
	protected int width_ = 0;
	protected int height_ = 0;
	private int type_ = -1;
	
	protected Hashtable<Point3D,MultiTile> allTiles_ = new Hashtable<Point3D,MultiTile>();
	protected Hashtable<Integer,Point3D> currentTiles_ = new Hashtable<Integer, Point3D>();
	
	public MultiTileCache(int zoomLevels, Dimension tileDimensions) {
		minZoomLevel_ = -zoomLevels+1;
		width_ = tileDimensions.width;
		height_ = tileDimensions.height;

        File tmpDir = new File("/tmp");
        if(!tmpDir.exists())
                tmpDir.mkdir();
	}
	
	public void addImage(Point idx, ImageProcessor proc) {
		if (type_ == -1) {
			type_ = ImageUtils.getImageProcessorType(proc);
		}
		
		MultiTile tile = getTile(idx);
		tile.setImage(proc);
		propagateTile(idx, tile);
	}
	
	public ImageProcessor getImage(Point3D multiTileIndex) {
		return getMultiTile(multiTileIndex).getImage();
	}
	
	public boolean hasImage(Point p) {
		return allTiles_.containsKey(new Point3D(p,0));
	}
	
	public boolean hasImage(Point3D p) {
		return allTiles_.containsKey(p);
	}
		
	protected void propagateTile(Point idx, MultiTile multiTile) {
		propagateTile(new Point3D(idx, 0), multiTile);
	}
	
	protected void propagateTile(Point3D idx, MultiTile multiTile) {
		if (idx.k>minZoomLevel_) {
			Point3D pidx = getParentIndex(idx);
			Point pquad = getParentQuadrant(idx);
			MultiTile parentTile = getMultiTile(pidx);
			
			ImageProcessor proc = multiTile.getImage();
			parentTile.insertQuadrantImage(pquad, proc);
			propagateTile(pidx, parentTile);
		}
	}
		
	protected MultiTile getMultiTile(int i, int j, int z) {
		return getMultiTile(new Point3D(i,j,z));
	}
	
	protected MultiTile getTile(Point idx) {
		return getMultiTile(new Point3D(idx.x, idx.y, 0));
	}
	
	protected synchronized MultiTile getMultiTile(Point3D idx) {

		if (currentTiles_.containsKey(idx.k)) {
			Point3D currentTile = currentTiles_.get(idx.k);
			if (!currentTile.equals(idx)) {
				allTiles_.get(currentTile).dropFromMemory();
				currentTiles_.remove(currentTile.k);
			}
		}

		currentTiles_.put(idx.k, idx);
		if (! allTiles_.containsKey(idx)) {
			MultiTile tile = new MultiTile(type_, width_, height_);
			allTiles_.put(idx, tile);
			return tile;
		} else {
			return allTiles_.get(idx);
		}
	}
		
	protected Point3D getParentIndex(Point3D idx) {
		return new Point3D(idx.i>>1, idx.j>>1, idx.k-1);
	}
	
	protected Point getParentQuadrant(Point3D idx) {
		return new Point(idx.i & 1, idx.j & 1);
	}
	
	protected Point3D newPoint3D(int i, int j, int k) {
		return new Point3D(i,j,k);
	}

	public void clear() {
		for (MultiTile multiTile:allTiles_.values()) {
			multiTile.wipeFromDisk();
		}
        currentTiles_.clear();
        allTiles_.clear();
	}
	
	

}