// Copyright
//           (C) 2020 Regents of the University of California
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

package org.micromanager.display.internal.event;

import java.awt.event.KeyEvent;

/**
 * Used to propagate key-presses through the Guava bus
 * If a consumer wants others to know that the keyEvent led to action,
 * call the "consumed()" function.
 *
 * @author Nico
 */

public class DisplayKeyPressEvent {
    private final KeyEvent keyEvent_;
    private boolean consumed_;

    public DisplayKeyPressEvent(KeyEvent e) {
        keyEvent_ = e;
        consumed_ = false;
    }

    public KeyEvent getKeyEvent() {
        return keyEvent_;
    }

    public void consume() {
        consumed_ = true;
    }

    public boolean wasConsumed() {
        return consumed_;
    }

}
