///////////////////////////////////////////////////////////////////////////////
// FILE:          Host.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     MMCore
//-----------------------------------------------------------------------------
// DESCRIPTION:   Multi-platform declaration of some simple network facilities
//              
// COPYRIGHT:     University of California, San Francisco, 2011,
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
// AUTHOR:        Karl Hoover  karl.hoover@gmail.com 2011
#ifndef HOST_H
#define HOST_H
#include <string>
#include <vector>

typedef long long MACValue;


class Host
{
public:
   Host(void);
   ~Host(void);
   std::vector<std::string> MACAddresses(long& status);
   std::vector<MACValue > getMACAddresses(long& status);


};

#endif