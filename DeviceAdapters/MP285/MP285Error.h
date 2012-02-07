///////////////////////////////////////////////////////////////////////////////
// FILE:          MP285Error.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   MP285 Device Adapter Error Codes & Messages
//
// COPYRIGHT:     Sutter Instrument,
//                Mission Bay Imaging, San Francisco, 2011
//
// LICENSE:       This file is distributed under the BSD license.
//                License text is included with the source distribution.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER(S) OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
// AUTHOR:        Lon Chu (lonchu@yahoo.com) created on June 2011
//

#ifndef _MP285ERROR_H_
#define _MP285ERROR_H_

#include <string>
#include <map>

class MPError
{
public:
    ~MPError();

    typedef int MPErr;
    enum _MPErr
    {
        MPERR_OK                        = 0,        // OK
        MPERR_SerialOverRun             = 1,        // Serial command buffer over run
        MPERR_SerialTimeout             = 2,        // Receiving serial command time out
        MPERR_SerialBufferFull          = 3,        // Serial command buffer full
        MPERR_SerialInpInvalid          = 4,        // Invalid serial command
        MPERR_SerialIntrupMove          = 5,        // Serial command interrupt motion
        MPERR_SerialZeroReturn          = 6,        // No response from serial port
        MPERR_SerialUnknownError        = 7,        // Unknown serial command
        MPERR_GENERIC                   = 8,        // Unspecified MP285 adapter errors
        MPERR_FileOpenFailed            = 9         // Fail to open file
    };

    static MPError* Instance();
    std::string GetErrorText(int nErrorCode) const;

private:
    MPError();
    static bool m_yInstanceFlag;
    static MPError* m_pMPError;
    std::map<int, std::string> m_sErrorText;        // error messages
};

#endif  // _MP285ERROR_H_