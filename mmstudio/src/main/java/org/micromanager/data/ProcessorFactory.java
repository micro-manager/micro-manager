///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Data API
//-----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger, 2015
//
// COPYRIGHT:    University of California, San Francisco, 2015
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

package org.micromanager.data;

/**
 * A ProcessorFactory is an object that generates new DataProcessors. Each
 * ProcessorFactory is pre-configured to generate a DataProcessor with specific
 * settings; that is, the Factory and the Processors it generates are both
 * "locked in" to certain settings and cannot be reconfigured once created.
 * The ProcessorFactory should not make reference to any outside material when
 * creating new DataProcessors.
 */
public interface ProcessorFactory {
   /**
    * Generate a new DataProcessor based on the configuration of the Factory.
    * @return new DataProcessor based on the configuration of the Factory
    */
   Processor createProcessor();
}
