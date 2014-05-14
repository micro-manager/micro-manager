// AUTHOR:        Mark Tsuchida, May 2014
//
// COPYRIGHT:     University of California, San Francisco, 2014
//
// LICENSE:       This file is distributed under the BSD license.
//                License text is included with the source distribution.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

#include "MMDevice.h"

// Definitions for static const data members.
// The initializer is given in the header, but we still need the definitions
// for these to work in all casees.

namespace MM {

const DeviceType Generic::Type;
const DeviceType Camera::Type;
const DeviceType Shutter::Type;
const DeviceType Stage::Type;
const DeviceType XYStage::Type;
const DeviceType State::Type;
const DeviceType Serial::Type;
const DeviceType AutoFocus::Type;
const DeviceType ImageProcessor::Type;
const DeviceType SignalIO::Type;
const DeviceType Magnifier::Type;
const DeviceType SLM::Type;
const DeviceType Galvo::Type;
const DeviceType Hub::Type;

} // namespace MM
