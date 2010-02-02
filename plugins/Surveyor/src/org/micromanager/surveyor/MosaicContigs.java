package org.micromanager.surveyor;


import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;

import org.micromanager.navigation.MultiStagePosition;
import org.micromanager.navigation.PositionList;

public class MosaicContigs extends ArrayList<MosaicContigs.Contig> {

	protected int frameWidth_;
	protected int frameHeight;
	protected double pixelSize;
	Controller controller_;

	public MosaicContigs(int _frameWidth, int _frameHeight, double _pixelSize, Controller _controller) {
		frameWidth_ = _frameWidth;
		frameHeight = _frameHeight;
		pixelSize = _pixelSize;
		controller_ = _controller;
	}
	
	private static final long serialVersionUID = -7554001167637897713L;

	public class Contig extends ArrayList<MultiStagePosition> {
		private static final long serialVersionUID = 1L;
		}
	
	boolean areContiguous(MultiStagePosition pos1, MultiStagePosition pos2) {
		if (! pos1.getDefaultXYStage().contentEquals(pos2.getDefaultXYStage()))
			return false;
		if (Math.abs(pos1.getX()-pos2.getX()) > frameWidth_*pixelSize)
			return false;
		if (Math.abs(pos1.getY()-pos2.getY()) > frameHeight*pixelSize)
			return false;
		if (pos1.getZ() != pos2.getZ())
			return false;
		return true;
	}

	Contig getContig(MultiStagePosition pos) {
		for (Contig contig:this)
			if (contig.contains(pos))
				return contig;
		Contig contig = new Contig();
		contig.add(pos);
		add(contig);
		return contig;
	}

	void mergeContigs(Contig contigA, Contig contigB) {
		if (contigA != contigB) {
			contigA.addAll(contigB);
			remove(contigB);
		}
	}

	void findContigs(PositionList posList) {
		clear();
		int n = posList.getNumberOfPositions();
		for (int i=0;i<n;i++) {
			MultiStagePosition posA = posList.getPosition(i);
			Contig contigA = getContig(posA);
			for (int j=i+1;j<n;j++) {
				MultiStagePosition posB = posList.getPosition(j);
				Contig contigB = getContig(posB);
				//print(contigs);
				if (areContiguous(posA, posB))
					mergeContigs(contigA, contigB);
			}
		}
	}

	/*
	void printContigs() {
		int i=0;
		for (contig:contigs) {
			i++;
			print("\nContig " + i + ":");
			for(pos:this)
				print("    " + pos);
		}
	}
*/
	Rectangle2D.Double getContigBounds(Contig contig) {
		double left, top, right, bottom;

		left = Double.POSITIVE_INFINITY;
		right = Double.NEGATIVE_INFINITY;
		top = Double.POSITIVE_INFINITY;
		bottom = Double.NEGATIVE_INFINITY;
		for(MultiStagePosition pos:contig) {
			if (pos.getX() < left)
				left = pos.getX();
			if (pos.getX() > right)
				right = pos.getX();
			if (pos.getY() < top)
				top = pos.getY();
			if (pos.getY() > bottom)
				bottom = pos.getY();
		}
		right += frameWidth_ * pixelSize;
		bottom += frameHeight * pixelSize;
		return new Rectangle2D.Double(left, top, right-left, bottom-top);
	}

	Rectangle getContigBoundsOnMap(Contig contig) {
		int left, top, right, bottom;
		Point mapPos;
		left = Integer.MAX_VALUE;
		right = Integer.MIN_VALUE;
		top = Integer.MAX_VALUE;
		bottom = Integer.MIN_VALUE;
		for(MultiStagePosition pos:contig) {
			mapPos = controller_.stageToMap(new Point2D.Double(pos.getX(), pos.getY()));
			if (mapPos.x < left)
				left = mapPos.x;
			if (mapPos.x > right)
				right = mapPos.x;
			if (mapPos.y < top)
				top = mapPos.y;
			if (mapPos.y > bottom)
				bottom = mapPos.y;
		}
		right += frameWidth_;
		bottom += frameHeight;
		return new Rectangle(left, top, right-left, bottom-top);
	}


}

