/*
 * Project: ASI Ring TIRF Control
 * License: BSD 3-clause, see LICENSE.md
 * Author: Brandon Simpson (brandon@asiimaging.com)
 * Copyright (c) 2022, Applied Scientific Instrumentation
 */
package com.asiimaging.tirf.model.data;

public class Devices {

    public enum Libraries {
        NOT_SUPPORTED("Not Supported"),
        HAMAMATSUHAM("HamamatsuHam");

        private final String text;

        Libraries(final String text) {
            this.text = text;
        }

        @Override
        public String toString() {
            return text;
        }
    }

    public enum Cameras {
        NOT_SUPPORTED("Not Supported"),
        HAMAMATSU_FUSION("C14440-20UP");

        private final String text;

        Cameras(final String text) {
            this.text = text;
        }

        @Override
        public String toString() {
            return text;
        }
    }

    public enum CameraProps {

    }
}
