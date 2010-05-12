package org.micromanager.slideexplorer;

import java.awt.Point;


public class Point3D {
	protected int i,j,k;
	
	public Point3D(int x, int y, int z) {
		i = x;
		j = y;
		k = z;
	}
	
	public Point3D(Point p, int z) {
		i = p.x;
		j = p.y;
		k = z;
	}
	
	public boolean equals(Object other) {
		if (other instanceof Point3D) {
			Point3D otherPt = (Point3D) other;
			return (otherPt.i==i) && (otherPt.j==j) && (otherPt.k==k);
		} else {
			return super.equals(other);
		}
	}
	
	public String toString() {
		return "Point3D<"+i+","+j+","+k+">";
	}
	
	public int hashCode() {
		return 10000*i + 100*j + k;
	}
}