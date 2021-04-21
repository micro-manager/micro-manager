package org.micromanager.hcs;

import org.micromanager.PositionList;

public class WellPositionList {
  private String label_;
  private PositionList sites_;
  private int row_ = 0;
  private int col_ = 0;

  public WellPositionList() {
    sites_ = new PositionList();
  }

  String getLabel() {
    return label_;
  }

  public void setLabel(String lab) {
    label_ = lab;
  }

  public PositionList getSitePositions() {
    return sites_;
  }

  public void setSitePositions(PositionList pl) {
    sites_ = pl;
  }

  public int getRow() {
    return row_;
  }

  public int getColumn() {
    return col_;
  }

  public void setGridCoordinates(int r, int c) {
    row_ = r;
    col_ = c;
  }
}
