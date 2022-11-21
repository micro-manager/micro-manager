/*
 * Project: ASI Ring TIRF Control
 * License: BSD 3-clause, see LICENSE.md
 * Author: Brandon Simpson (brandon@asiimaging.com)
 * Copyright (c) 2022, Applied Scientific Instrumentation
 */

package com.asiimaging.tirf.model.devices;

import org.micromanager.Studio;

public class ZStage extends ASITigerDevice {

   public ZStage(final Studio studio) {
      super(studio);
   }

   public void foo() {
      logs.logMessage("asdas");
   }

}
