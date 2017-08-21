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

namespace MM {

// Definitions for static const data members.
//
// Note: Do not try to move these initializers to the header. The C++ standard
// allows initializing a static const enum data member inline (inside the class
// definition, where the member is _declared_), but still requires a
// _definition_ (in which case, the definition should not have an initializer).
// However, Microsoft VC++ has a nonstandard extension that allows you to leave
// out the definition altogether, if an initializer is supplied at the
// declaration. Because of that nonstandard behavior, VC++ issues a warning
// (LNK4006) if the initializer is supplied with the declaration _and_ a
// definition is (correctly) provided. So, to compile correctly with a
// standards-conformant compiler _and_ avoid warnings from VC++, we need to
// leave the initializers out of the declarations, and supply them here with
// the definitions. See:
// http://connect.microsoft.com/VisualStudio/feedback/details/802091/lnk4006-reported-for-static-const-members-that-is-initialized-in-the-class-definition

const DeviceType Generic::Type = GenericDevice;
const DeviceType Camera::Type = CameraDevice;
const DeviceType Shutter::Type = ShutterDevice;
const DeviceType Stage::Type = StageDevice;
const DeviceType XYStage::Type = XYStageDevice;
const DeviceType State::Type = StateDevice;
const DeviceType Serial::Type = SerialDevice;
const DeviceType AutoFocus::Type = AutoFocusDevice;
const DeviceType ImageProcessor::Type = ImageProcessorDevice;
const DeviceType SignalIO::Type = SignalIODevice;
const DeviceType Magnifier::Type = MagnifierDevice;
const DeviceType SLM::Type = SLMDevice;
const DeviceType Galvo::Type = GalvoDevice;
const DeviceType Hub::Type = HubDevice;

} // namespace MM
