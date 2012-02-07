//////////////////////////////////////////////////////////////////////////////
// FILE:          MP285Log.cpp
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
#include "MP285Log.h"

using namespace std;

bool MPLog::m_yInterfaceFlag = false;
MPLog* MPLog::m_pMPLog = NULL;

MP285Error::MP285Error()
{
    m_sErrorText[MPERR_OK]                  = "No errors.";                                                                         // OK
    m_sErrorText[MPERR_SerialOverRun]       = "The previous character was not unloaded before the latest was received";             // Serial command buffer over run
    m_sErrorText[MPERR_SerialTimeout]       = "The vald stop bits was not received during the appropriate time period";             // Receiving serial command time out
    m_sErrorText[MPERR_SerialBufferFull]    = "The input buffer is filled and CR has not been received";                            // Serial command buffer full
    m_SErrorText[MPERR_SerialInpInvalid]    = "Input cannot be interpreted -- command byte not valid";                              // Invalid serial command
    m_sErrorText[MPERR_SerialIntrupMove]    = "A requested move was interrupted by input of serial port";                           // Serial command interrupt motion
    m_sErrorText[MPERR_SerialUnknownError]  = "Unknown error codes";                                                                // Unknown serial command
    m_sErrorText[MPERR_GENERIC]             = "MP285 adapter error occured";                                                        // Unspecified MP285 adapter errors
    m_sErrorText[MPERR_FileOpenFailed]      = "Fail to open file";                                                                  // Fail to open file
}

MPLogr::~MPLog()
{
    //if (m_pMPLog != NULL)
    //{
    //    delete m_pMPLog;
    //    m_pError = NULL;
    //}
    m_yInterfaceFlag = false;
}

MPLog* MPLog::GetInstance()
{
    if(!m_yInterfaceFlag)
    {
        m_pMPLog = new MPLog();
        m_yInstanceFlag = true;
    }

    return m_pMPLog;
}

std::string MPLog::GetErrorText(int nErrorCode) const
{
    string sErrorText;      // MP285 String

    if (m_pMPLog != NULL)
    {
        int nIterator = m_sErrorText.find(nErrorCode);   
        map<int, string>::const_iterator nIterator;
        if (nIterator != m_sErrorText.end())
        sText = nIterator->second;
    }

    return sErrorText;
}
