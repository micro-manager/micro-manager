//////////////////////////////////////////////////////////////////////////////
// FILE:          MP285Error.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   MP285s Controller Driver
//
// COPYRIGHT:     Sutter Instrument,
//				  Mission Bay Imaging, San Francisco, 2011
//                All rights reserved
//
// LICENSE:       This library is free software; you can redistribute it and/or
//                modify it under the terms of the GNU Lesser General Public
//                License as published by the Free Software Foundation.
//                
//                You should have received a copy of the GNU Lesser General Public
//                License along with the source distribution; if not, write to
//                the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
//                Boston, MA  02111-1307  USA
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, 
//
// AUTHOR:        Lon Chu (lonchu@yahoo.com), created on June 2011
//

#ifdef WIN32
   #include <windows.h>
   #define snprintf _snprintf 
#endif

#include <stdio.h>
#include <string>
#include <fstream>
#include <iostream>
#include <sstream>
#include <math.h>
#include "../../MMCore/MMCore.h"
#include "../../MMDevice/ModuleInterface.h"
#include "../../MMDevice/DeviceUtils.h"
#include "MP285Error.h"

using namespace std;

bool        MPError::m_yInstanceFlag   = false;
MPError*    MPError::m_pMPError         = NULL;

MPError::MPError()
{
    MPError::m_sErrorText[MPError::MPERR_OK]                    = "No errors.";                                                                // OK
    MPError::m_sErrorText[MPError::MPERR_SerialOverRun]         = "The previous character was not unloaded before the latest was received";    // Serial command buffer over run
    MPError::m_sErrorText[MPError::MPERR_SerialTimeout]         = "The vald stop bits was not received during the appropriate time period";    // Receiving serial command time out
    MPError::m_sErrorText[MPError::MPERR_SerialBufferFull]      = "The input buffer is filled and CR has not been received";                   // Serial command buffer full
    MPError::m_sErrorText[MPError::MPERR_SerialInpInvalid]      = "Input cannot be interpreted -- command byte not valid";                     // Invalid serial command
    MPError::m_sErrorText[MPError::MPERR_SerialIntrupMove]      = "A requested move was interrupted by input of serial port";                  // Serial command interrupt motion
    MPError::m_sErrorText[MPError::MPERR_SerialZeroReturn]      = "No response from MP285 controller";                                         // No Response from serial port
    MPError::m_sErrorText[MPError::MPERR_SerialUnknownError]    = "Unknown error codes";                                                       // Unknown serial command
    MPError::m_sErrorText[MPError::MPERR_GENERIC]               = "MP285 adapter error occured";                                               // Unspecified MP285 adapter errors
    MPError::m_sErrorText[MPError::MPERR_FileOpenFailed]        = "Fail to open file";                                                         // Fail to open file
}

MPError::~MPError()
{

    m_yInstanceFlag = false;
}

MPError* MPError::Instance()
{
    if(!m_yInstanceFlag)
    {
        m_pMPError = new MPError();
        m_yInstanceFlag = true;
    }

    return m_pMPError;
}

std::string MPError::GetErrorText(int nErrorCode) const
{
    string sErrorText;      // MP285 String

    if (m_pMPError != NULL)
    {
        map<int, string>::const_iterator nIterator;
        nIterator = m_sErrorText.find(nErrorCode);   
        if (nIterator != m_sErrorText.end())
        sErrorText = nIterator->second;
    }

    return sErrorText;
}
