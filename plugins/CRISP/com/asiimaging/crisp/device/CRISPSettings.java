/**
 * Project: ASI CRISP Control
 * License: BSD 3-clause, see LICENSE.md
 * Author: Brandon Simpson (brandon@asiimaging.com)
 * Copyright (c) 2014-2021, Applied Scientific Instrumentation
 */
package com.asiimaging.crisp.device;

/**
 * A data class to store software settings for CRISP.
 *
 * Also can convert {@code CRISPSettings} to and from JSON.
 * 
 */
public class CRISPSettings {

    private String name;
    private int gain;
    private int ledIntensity;
    private int numAverages;
    private float objectiveNA;
    private float lockRange;

    public static final String NAME_PREFIX = "Profile";
    public static final String SETTINGS_NOT_FOUND = "No Settings";
    public static final String DEFAULT_PROFILE_NAME = "Default";

    public CRISPSettings(final String name) {
        this.name = name;
        this.gain = 1;
        this.ledIntensity = 50;
        this.numAverages = 1;
        this.objectiveNA = 0.65f;
        this.lockRange = 1.0f;
    }
    
    public CRISPSettings(
            final String name, 
            final int gain, 
            final int ledIntensity, 
            final int numAverages, 
            final float objectiveNA, 
            final float lockRange) {
        this.name = name;
        this.gain = gain;
        this.ledIntensity = ledIntensity;
        this.numAverages = numAverages;
        this.objectiveNA = objectiveNA;
        this.lockRange = lockRange;
    }

    @Override
    public String toString() {
        return String.format(
            "%s[name=\"%s\", gain=%s, ledIntensity=%s, numAverages=%s, objectiveNa=%s, lockRange=%s]", 
            getClass().getSimpleName(), name, gain, ledIntensity, numAverages, objectiveNA, lockRange
        );
    }

    public int getGain() {
        return gain;
    }
    
    public int getLEDIntensity() {
        return ledIntensity;
    }
    
    public int getNumAverages() {
        return numAverages;
    }
    
    public float getObjectiveNA() {
        return objectiveNA;
    }
    
    public float getLockRange() {
        return lockRange;
    }
    
    public String getName() {
        return name;
    }
    
    public void setGain(final int n) {
        gain = n;
    }
    
    public void setLEDIntensity(final int n) {
        ledIntensity = n;
    }

    public void setNumAverages(final int n) {
        numAverages = n;
    }
    
    public void setObjectiveNA(final float n) {
        objectiveNA = n;
    }
    
    public void setLockRange(final float n) {
        lockRange = n;
    }

    public void setName(final String newName) {
        name = newName;
    }

}
