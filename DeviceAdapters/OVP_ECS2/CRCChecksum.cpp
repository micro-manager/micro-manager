///////////////////////////////////////////////////////////////////////////////
// FILE:          OVP_ECS2.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Optic Valley Photonics Environmental Conditioning System Gen2
//
// COPYRIGHT:     Applied Scientific Instrumentation, Eugene OR
//
// LICENSE:       This file is distributed under the BSD license.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
// AUTHOR:        Jon Daniels (jon@asiimaging.com) 06/2014
//

#include "OVP_ECS2.h"

// The GetCRCChecksum function is placed in a separate translation unit,
// because the Boost CRC templates generate VC++ warnings that cannot be
// suppressed with a local pragma warning push/pop.
#ifdef _MSC_VER
#pragma warning (disable: 4244)
#pragma warning (disable: 4245)
#endif

#include <boost/crc.hpp>
#include <boost/integer.hpp>

Message ECS::GetCRCChecksum(Message msg)
// returns a 2-element vector (16 bits)
// relies on the header-only Boost CRC library (boost/crc.hpp)
// when I compile I see 3 warnings related to type conversions
{
   Message crc_vector;
   boost::uint16_t crc_uint;
   boost::crc_optimal<16, 0x8005, 0xFFFF, 0, true, true> crc_modbus;
   crc_modbus.process_bytes(&msg[0], msg.size());
   crc_uint = crc_modbus.checksum();
   crc_vector.push_back(crc_uint & 0xFF);
   crc_vector.push_back((crc_uint & 0xFF00) >> 8);
   return crc_vector;
}