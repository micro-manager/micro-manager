/*
 * Project: ASI Ring TIRF Control
 * License: BSD 3-clause, see LICENSE.md
 * Author: Brandon Simpson (brandon@asiimaging.com)
 * Copyright (c) 2022, Applied Scientific Instrumentation
 */
package com.asiimaging.tirf.model.devices;

import org.micromanager.Studio;

public class PLC extends ASITigerDevice {

    public PLC(final Studio studio) {
        super(studio);
    }

    public void setTriggerSource(final String source) {
        try {
            core.setProperty(deviceName, Keys.TRIGGER_SOURCE, source);
        } catch (Exception e) {
            logs.logError("setTriggerSource failed.");
        }
    }

    public void setPointerPosition(final int position) {
        try {
            core.setProperty(deviceName, Keys.POINTER_POSITION, position);
        } catch (Exception e) {
            logs.logError("setPointerPosition failed.");
        }
    }

    public void setCellType(final String cellType) {
        try {
            core.setProperty(deviceName, Keys.EDIT_CELL_TYPE, cellType);
        } catch (Exception e) {
            logs.logError("editCellType failed.");
        }
    }

    public void setCellConfig(final int value) {
        try {
            core.setProperty(deviceName, Keys.EDIT_CELL_CONFIG, value);
        } catch (Exception e) {
            logs.logError("editCellConfig failed.");
        }
    }

    public void setCellInput1(final int value) {
        try {
            core.setProperty(deviceName, Keys.EDIT_CELL_INPUT1, value);
        } catch (Exception e) {
            logs.logError("editCellInput1 failed.");
        }
    }

    public void setCellInput2(final int value) {
        try {
            core.setProperty(deviceName, Keys.EDIT_CELL_INPUT2, value);
        } catch (Exception e) {
            logs.logError("editCellInput2 failed.");
        }
    }

    public void setCellInput3(final int value) {
        try {
            core.setProperty(deviceName, Keys.EDIT_CELL_INPUT3, value);
        } catch (Exception e) {
            logs.logError("editCellInput3 failed.");
        }
    }

    public void setCellInput4(final int value) {
        try {
            core.setProperty(deviceName, Keys.EDIT_CELL_INPUT4, value);
        } catch (Exception e) {
            logs.logError("editCellInput4 failed.");
        }
    }

    public void setCellInput(final int input, final int value) {
        if (input <= 0 || input >= 5) {
            throw new IllegalArgumentException("Input must be 1-4.");
        }
        try {
            core.setProperty(deviceName, Keys.EDIT_CELL_INPUT + input, value);
        } catch (Exception e) {
            logs.logError("editCellInput failed.");
        }
    }

    // Device Properties
    public static class Keys {

        public static final String CLEAR_ALL_CELL_STATES = "ClearAllCellStates";
        public static final String EDIT_CELL_UPDATE_AUTO = "EditCellUpdateAutomatically";
        public static final String POINTER_POSITION = "PointerPosition";
        public static final String TRIGGER_SOURCE = "TriggerSource";
        public static final String SET_CARD_PRESET = "SetCardPreset";

        public static final String EDIT_CELL_TYPE = "EditCellCellType";
        public static final String EDIT_CELL_CONFIG = "EditCellConfig";
        public static final String EDIT_CELL_INPUT1 = "EditCellInput1";
        public static final String EDIT_CELL_INPUT2 = "EditCellInput2";
        public static final String EDIT_CELL_INPUT3 = "EditCellInput3";
        public static final String EDIT_CELL_INPUT4 = "EditCellInput4";

        public static final String EDIT_CELL_INPUT = "EditCellInput";

        public static class ReadOnly {
            public static final String BACKPLANE_OUTPUT_STATE = "BackplaneOutputState";
            public static final String FRONTPANEL_OUTPUT_STATE = "FrontpanelOutputState";
            public static final String PLOGIC_OUTPUT_STATE = "PLogicOutputState";
            public static final String NUM_LOGIC_CELLS = "NumLogicCells";
            public static final String AXIS_LETTER = "AxisLetter";
        }
    }

    public static class Values {

        public static final String NO = "No";
        public static final String YES = "Yes";

        public static class EditCellType {
            public static final String CONSTANT = "0 - constant";
            public static final String INPUT = "0 - input";
            public static final String D_FLOP = "1 - D flop";
            public static final String OUTPUT_OPEN_DRAIN = "1 - output (open-drain)";
            public static final String LUT2 = "2 - 2-input LUT";
            public static final String OUTPUT_PUSH_PULL = "2 - output (push-pull";
            public static final String LUT3 = "3 - 3-input LUT";
            public static final String LUT4 = "4 - 4-input LUT";
            public static final String AND2 = "5 - 2-input AND";
            public static final String OR2 = "6 - 2-input OR";
            public static final String XOR2 = "7 - 2-input XOR";
            public static final String ONE_SHOT = "8 - one shot";
            public static final String DELAY = "9 - delay";
            public static final String AND4 = "10 - 4-input AND";
            public static final String OR4 = "11 - 4-input OR";
            public static final String D_FLOP_SYNC = "12 - D flop (sync)";
            public static final String JK_FLOP = "13 - JK flop";
            public static final String ONE_SHOT_NRT = "14 - one shot (NRT)";
            public static final String DELAY_NRT = "15 - delay (NRT)";
        }

        public static class TriggerSource {
            public static final String INTERNAL = "0 - internal 4kHz";
            public static final String MICRO_MIRROR_CARD = "1 - Micro-mirror card";
            public static final String BACKPLANE_TTL5 = "2 - backplane TTL5";
            public static final String BACKPLANE_TTL7 = "3 - backplane TTL7";
            public static final String FRONTPANEL_BNC1 = "4 - frontpanel BNC 1";
        }
    }
}
