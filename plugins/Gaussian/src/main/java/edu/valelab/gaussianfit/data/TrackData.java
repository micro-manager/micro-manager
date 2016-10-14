/*
 * This class data specific to tracks
 * 
 * Nico Stuurman, nico.stuurman at ucsf.edu
 * 
 * Copyright UCSF, 2016
 * 
 * Licensed under BSD license version 2.0
 * 
 */
package edu.valelab.gaussianfit.data;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author nico
 */
public class TrackData {
    private final List<SpotData> spotList_;
    private int missingAtEnd_;
    
    public TrackData() {
       spotList_ = new ArrayList<SpotData>();
       missingAtEnd_ = 0;
    }
    
    public void addMissing() {
       missingAtEnd_++;
    } 
    
    public void resetMissing() {
       missingAtEnd_ = 0;
    }
    
    public boolean missingMoreThan(int thisMany) {
       return missingAtEnd_ > thisMany;
    }
    
    public int size() {
       return spotList_.size();
    }
    
    public SpotData get(int index) {
       return spotList_.get(index);
    }
    
    public void add(SpotData item) {
       spotList_.add(item);
    }
    
    public List<SpotData> getList() {
       return spotList_;
    }
}
