//////////////////////////////////////////////////////////////////////////////
// FILE:          MP285XYStage.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   XY Stage
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

#ifndef _MP285XYSTAGE_H_
#define _MP285XYSTAGE_H_

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include "MP285.h"
	
//
// define XY stage class that is atached to the MP285 controller
//
class XYStage : public CXYStageBase<XYStage>
{
public:

    XYStage();			// X-Y stage constructor
    ~XYStage();			// X-Y stage destructor

    // Device API
    // ----------
    int Initialize();	// X-Y stage initialization

    // get X-Y stage device name
    void GetName(char* pszName) const;

    // Busy is not applicable for MP285
    // the fuction will return false always
    bool Busy() { return false; }
    int Shutdown();

    // XYStage API
    // -----------

	// setup motion mode (1: relative, 0: relative
	int SetMotionMode(long lMotionMode);

    // Move X-Y stage to position in um
    int SetPositionUm(double dXPosUm, double dYPosUm);
	int SetRelativePositionUm(double dXPosUm, double dYPosUm);

    // Get X-Y stage position in um
    int GetPositionUm(double& dXPosUm, double& dYPosUm);

    // Move X-Y stage to position in uSteps
    int SetPositionSteps(long lXPosSteps, long lYPosSteps);
	int SetRelativePositionSteps(long lXPosSteps, long lYPosSteps);
	int _SetPositionSteps(long lXPosSteps, long lYPosSteps, long lZPosSteps);

    // Get X-Y stage position in uSteps
    int GetPositionSteps(long& lXPosSteps, long& lYPosSteps);

    // Get limits of the X-Y stage, not applicable for MP285 XY stage
    // The function will return DEVICE_OK always
    int GetStepLimits(long& /*xMin*/, long& /*xMax*/, long& /*yMin*/, long& /*yMax*/) { return DEVICE_OK/*DEVICE_UNSUPPORTED_COMMAND*/; }
    int GetLimitsUm(double& /*xMin*/, double& /*xMax*/, double& /*yMin*/, double& /*yMax*/) { return DEVICE_OK/*DEVICE_UNSUPPORTED_COMMAND*/; }

    // get step size in um
    double GetStepSizeXUm() { return m_dStepSizeUm; }

    // get step size in um
    double GetStepSizeYUm() { return m_dStepSizeUm; }

    // Set X-Y stage origin
    int SetOrigin();

    // Calibrate home positionK
    int Home() { return DEVICE_OK/*DEVICE_UNSUPPORTED_COMMAND*/; }

    // Stop X-Y-Z stage motion
    int Stop();

    // action interface
    // ----------------

    //int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);
    //int OnStepSizeUm(MM::PropertyBase* pPro, MM::ActionType eAct);
    //int OnInterface(MM::PropertyBase* pPro, MM::ActionType eAct) ;
    int OnSpeed(MM::PropertyBase* /*pPro*/, MM::ActionType /*eAct*/);
    int OnGetPositionX(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnGetPositionY(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnSetPositionX(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnSetPositionY(MM::PropertyBase* pProp, MM::ActionType eAct);
    //int OnAccel(MM::PropertyBase* pPro, MM::ActionType eAct);

    /*
    * Returns whether a stage can be sequenced (synchronized by TTLs)
    * If returning true, then an XYStage class should also inherit
    * the SequenceableXYStage class and implement its methods.
    */
    int IsXYStageSequenceable(bool& /*isSequenceable*/) const  { return DEVICE_OK/*DEVICE_UNSUPPORTED_COMMAND*/; }     


private:

    int WriteCommand(unsigned char* sCommand, int nLength);
    int ReadMessage(unsigned char* sResponse, int nBytesRead);
    int CheckError(unsigned char bErrorCode);

    //int GetCommand(const std::string& cmd, std::string& response);
    //bool GetValue(std::string& sMessage, double& pos);

    //std::string m_sPort;              // serial port
    bool        m_yInitialized;         // x-y stage initialized flag
    bool        m_yRangeMeasured;       // x-y stage range measured flag
    int         m_nAnswerTimeoutMs;     // time out value of waiting response message
    int         m_nAnswerTimeoutTrys;   // time out trys
    double      m_dStepSizeUm;          // coverting unit between step and um
};

#endif  // _MP285XYSSTAGE_H_