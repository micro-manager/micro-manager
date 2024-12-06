package org.micromanager.lightsheetcontrol;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author kthorn
 */
class ReferencePoint {
    public double stage1Position;
    public double stage2Position;

    public ReferencePoint (double position1, double position2)  {
        this.stage1Position = position1;
        this.stage2Position = position2;
    }

    public String name() {
        String name = String.valueOf(stage1Position) + " / " + String.valueOf(stage2Position);
        return name;
    }

}
