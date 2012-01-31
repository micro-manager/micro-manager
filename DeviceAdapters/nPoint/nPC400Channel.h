//////////////////////////////////////////////////////////////////////////////
// FILE:          nPC400Channel.h 
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   C400 Channel
//
// COPYRIGHT:     NPoint,
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
// AUTHOR:        Lon Chu (lonchu@yahoo.com) created on AUgust 2011
//

#pragma once

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include "nPC400.h"
#include "nPC400Ctrl.h"
	
//
// define channel class that is atached to the C400 controller
//
class nPC400CH : public CStageBase<nPC400CH>
{
public:
    nPC400CH(int nChannel = 0);			// Channel stage constructor
    ~nPC400CH();                        // Channel stage destructor

    // Device API
    // ----------

    // C400 channel initialization & shutdown
    int Initialize();	
    int Shutdown();

    // Get channel device name
    void GetName(char* pszName) const;

    // Busy is not aplicable for MP285
    // It will return false always
    bool Busy() { return false; }

    // Stage API
    // ---------

    // Move channel position in um
    int SetPositionUm(double dPosUm);

    // Get channel position in um
    int GetPositionUm(double& dPosUm);

    // Move channel positiion in uSteps
    int SetPos_(long lPos);
    int GetSetPos_(long& lPos);
    int SetPositionSteps(long lPosSteps);

    // Get channel position in uSteps
    int GetPos_(long& lPos);
    int GetPositionSteps(long& lPosSteps);

    int OnPositionSet(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnPositionGet(MM::PropertyBase* pProp, MM::ActionType eAct);

    // Set origin
    int SetOrigin() { return DEVICE_UNSUPPORTED_COMMAND; }

    // Stop motion
    int Stop() { return DEVICE_UNSUPPORTED_COMMAND; }

    // Get limits of Zstage
    // This function is not applicable for
    // MP285, the function will return DEVICE_OK
    // insttead.
    int GetLimits(double& min, double& max) { min = (double)m_nMinPosSteps; max = (double)m_nMaxPosSteps; return DEVICE_OK; }
    //{ return DEVICE_UNSUPPORTED_COMMAND; }
    //int SetLimits(double min, double max) { return DEVICE_UNSUPPORTED_COMMAND; }
    //{ m_nMinPosSteps = (int)min; m_nMaxPosSteps = (int)max; return DEVICE_OK; }

    // action interface
    // ----------------
    double SetStepSize_(int nRange, int nRangeUnit);
    int GetRangeUnit_(int& sRangeUnit);
    int SetRangeUnit_(int nRangeUnit);
    int OnRadius(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnRangeUnit(MM::PropertyBase* pProp, MM::ActionType eAct);
    int GetRange_(int& nRange);
    int SetRange_(int nRange);
    int OnRange(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnSpeed(MM::PropertyBase* /*pPro*/, MM::ActionType /*eAct*/) { return DEVICE_UNSUPPORTED_COMMAND; }

    // Sequence functions                                                                                                     
    // Sequences can be used for fast acquisitions, sycnchronized by TTLs rather than                                         
    // computer commands.                                                                                                     
    // Sequences of positions can be uploaded to the stage.  The device will cycle through                                    
    // the uploaded list of states (triggered by an external trigger - most often coming                                      
    // from the camera).  If the device is capable (and ready) to do so isSequenceable will                                   
    // be true. If your device can not execute this (true for most stages                                                     
    // simply set isSequenceable to false                                                                                     
    int IsStageSequenceable(bool& isSequenceable) const 
    {
       isSequenceable = false;
       return DEVICE_OK;
    }

    int GetStageSequenceMaxLength(long& /*nrEvents*/) const  {return DEVICE_OK;}
    int StartStageSequence() const {return DEVICE_OK;}
    int StopStageSequence() const {return DEVICE_OK;}
    int LoadStageSequence(std::vector<double> /*positions*/) const {return DEVICE_OK;}
    int ClearStageSequence() {return DEVICE_OK;}
    int AddToStageSequence(double /*position*/) {return DEVICE_OK;}
    int SendStageSequence() const  {return DEVICE_OK;} 
    bool IsContinuousFocusDrive() const {return true;}

private:
    int WriteCommand(const unsigned char* sCommand, int nLength);
    int ReadMessage(unsigned char* sMessage);

    //std::string m_sPort;              // serial port
    bool        m_yInitialized;         // channel initialization flag
    bool        m_yChannelAvailable;    // channel available flag
    int         m_nChannel;             // channel number
    //int       m_nPosition;            // channel position (get)
    double      m_dRadius;              // channel radious
    int         m_nRange;               // channel range
    int         m_nMinPosSteps;         // minimum position steps
    int         m_nMaxPosSteps;         // maximum position steps
    double      m_dAnswerTimeoutMs;     // timeout value of channel waiting for response message
    double      m_dStepSizeUm;          // channel converting unit betweek step and um
    int         m_nRangeUnit;           // channel Range Unit
};

