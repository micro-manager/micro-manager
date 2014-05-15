// PROJECT:       Micro-Manager
// SUBSYSTEM:     MMCore
//
// DESCRIPTION:   Serial device instance wrapper
//
// COPYRIGHT:     University of California, San Francisco, 2014,
//                All Rights reserved
//
// LICENSE:       This file is distributed under the "Lesser GPL" (LGPL) license.
//                License text is included with the source distribution.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
// AUTHOR:        Mark Tsuchida

#include "SerialInstance.h"


MM::PortType SerialInstance::GetPortType() const { return GetImpl()->GetPortType(); }
int SerialInstance::SetCommand(const char* command, const char* term) { return GetImpl()->SetCommand(command, term); }
int SerialInstance::GetAnswer(char* txt, unsigned maxChars, const char* term) { return GetImpl()->GetAnswer(txt, maxChars, term); }
int SerialInstance::Write(const unsigned char* buf, unsigned long bufLen) { return GetImpl()->Write(buf, bufLen); }
int SerialInstance::Read(unsigned char* buf, unsigned long bufLen, unsigned long& charsRead) { return GetImpl()->Read(buf, bufLen, charsRead); }
int SerialInstance::Purge() { return GetImpl()->Purge(); }
