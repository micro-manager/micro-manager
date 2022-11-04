/*
 * Project: ASI Ring TIRF Control
 * License: BSD 3-clause, see LICENSE.md
 * Author: Brandon Simpson (brandon@asiimaging.com)
 * Copyright (c) 2022, Applied Scientific Instrumentation
 */
package com.asiimaging.tirf.ui.panels;

import com.asiimaging.tirf.model.data.Icons;
import com.asiimaging.tirf.ui.TIRFControlFrame;
import com.asiimaging.tirf.model.TIRFControlModel;
import com.asiimaging.tirf.ui.components.Panel;
import com.asiimaging.tirf.ui.components.ToggleButton;

import java.util.Objects;

public class ButtonPanel extends Panel {

    private ToggleButton tglLiveMode;
    private ToggleButton tglStartAcq;

    private final TIRFControlModel model;
    private final TIRFControlFrame frame;

    public ButtonPanel(final TIRFControlModel model, final TIRFControlFrame frame) {
        this.model = Objects.requireNonNull(model);
        this.frame = Objects.requireNonNull(frame);
        setMigLayout(
                "",
                "[]20[]",
                "[]10[]"
        );
        createUserInterface();
    }

    private void createUserInterface() {
        tglStartAcq = new ToggleButton("Start Acquisition", "Stop Acquisition", Icons.CAMERA_GO, Icons.CANCEL);
        tglLiveMode = new ToggleButton("Live", "Stop Live", Icons.CAMERA, Icons.CANCEL);

        createEventHandlers();

        // add ui elements to the panel
        add(tglStartAcq, "");
        add(tglLiveMode, "");
    }

    private void createEventHandlers() {
        tglStartAcq.registerListener(event -> {
            if (!model.isRunning()) {
                //model.startAcquisition();
                model.burstAcq();
            } else {
                model.setRunning(false);
            }
        });

        tglLiveMode.registerListener(event -> {
            frame.toggleLiveMode();
        });
    }

    public ToggleButton getStartButton() {
        return tglStartAcq;
    }

    public ToggleButton getLiveModeButton() {
        return tglLiveMode;
    }

}
