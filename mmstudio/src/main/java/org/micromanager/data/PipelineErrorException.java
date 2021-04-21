///////////////////////////////////////////////////////////////////////////////
// PROJECT:       Micro-Manager
// SUBSYSTEM:     Data API
// -----------------------------------------------------------------------------
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
 * This exception is thrown when Pipeline.insertImage() has been called after an error occurred in
 * one of the Processors in the Pipeline. It indicates that the Pipeline may be in a bad or
 * inconsistent state. You can resume calling Pipeline.insertImage() only after calling
 * Pipeline.clearErrors().
 */
public class PipelineErrorException extends Exception {
  public PipelineErrorException() {
    super();
  }

  public PipelineErrorException(String description) {
    super(description);
  }
}
