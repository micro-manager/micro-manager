///////////////////////////////////////////////////////////////////////////////
// FILE:          PI_GCS.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   PI GCS Controller Driver
//
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 08/28/2006
//                Steffen Rau, s.rau@pi.ws, 28/03/2008
// COPYRIGHT:     University of California, San Francisco, 2006
//                Physik Instrumente (PI) GmbH & Co. KG, 2008
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
// CVS:           $Id: PIGCSControllerCom.h,v 1.8, 2014-03-31 12:51:24Z, Steffen Rau$
//

#ifndef _PI_GCS_CONTROLLER_H_
#define _PI_GCS_CONTROLLER_H_

#include "../../MMDevice/DeviceBase.h"
#include "Controller.h"
#include <string>

class PIGCSControllerCom;
class PIGCSControllerComDevice : public CGenericBase<PIGCSControllerComDevice>
{
public:
	PIGCSControllerComDevice();
	~PIGCSControllerComDevice();

   // Device API
   // ----------
   int Initialize();
   int Shutdown();
  
   void SetFactor_UmToDefaultUnit(double dUmToDefaultUnit, bool bHideProperty = true);

   void CreateProperties();

   static const char* DeviceName_;
   static const char* UmToDefaultUnitName_;
   void GetName(char* pszName) const;
   bool Busy();


   int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnUmInDefaultUnit(MM::PropertyBase* pProp, MM::ActionType eAct);


	bool GCSCommandWithAnswer(const std::string command, std::vector<std::string>& answer, int nExpectedLines = -1);
	bool GCSCommandWithAnswer(unsigned char singleByte, std::vector<std::string>& answer, int nExpectedLines = -1);
	bool SendGCSCommand(const std::string command);
	bool SendGCSCommand(unsigned char singlebyte);
	bool ReadGCSAnswer(std::vector<std::string>& answer, int nExpectedLines = -1);
	int GetLastError() const { return lastError_; }

	double umToDefaultUnit_;
private:
	//int OnJoystick(MM::PropertyBase* pProp, MM::ActionType eAct, int joystick);
	int OnJoystick1(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnJoystick2(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnJoystick3(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnJoystick4(MM::PropertyBase* pProp, MM::ActionType eAct);

   std::string port_;
   int lastError_;
   bool initialized_;
   bool bShowProperty_UmToDefaultUnit_;
   PIGCSControllerCom* ctrl_;
};


class PIGCSControllerCom : public PIController
{
public:
	PIGCSControllerCom(const std::string& label, PIGCSControllerComDevice* proxy, MM::Core* logsink);
	~PIGCSControllerCom();

	int Connect();

	virtual bool qIDN(std::string& sIDN);
	virtual bool INI(const std::string& axis);
	virtual bool CST(const std::string& axis, const std::string& stagetype);
	virtual bool SVO(const std::string& axis, BOOL svo);
	virtual int GetError();
	virtual bool IsControllerReady( BOOL* );
	virtual bool IsMoving(const std::string& axes, BOOL* );
	virtual bool MOV(const std::string& axis, const double* target);
	virtual bool MOV(const std::string& axis1, const std::string& axis2, const double* target);
	virtual bool qPOS(const std::string& axis, double* position);
	virtual bool qPOS(const std::string& axis1, const std::string& axis2, double* position);
	virtual bool FRF(const std::string& axes);
	virtual bool REF(const std::string& axes);
	virtual bool MNL(const std::string& axes);
	virtual bool FNL(const std::string& axes);
	virtual bool FPL(const std::string& axes);
	virtual bool MPL(const std::string& axes);
	virtual bool STP();
	virtual bool JON(int joystick, int state);
	virtual bool qJON(int joystick, int& state);
	virtual bool VEL(const std::string& axis, const double* velocity);
	virtual bool qVEL(const std::string& axis, double* velocity);
	virtual bool qTPC(int& nrOutputChannels);
   bool ONL(std::vector<int> outputChannels, std::vector<int> values);

	virtual bool HasINI() {return hasINI_;}
	virtual bool HasSVO() {return hasSVO_;}
	virtual bool HasCST() {return hasCST_;}
	virtual bool HasIsReferencing() {return false;}
	virtual bool HasIsControllerReady() {return true;}
	virtual bool HasIsMoving() {return true;}
	virtual bool HasFRF() {return true;}
	virtual bool HasREF() {return true;}
	virtual bool HasFNL() {return true;}
	virtual bool HasMNL() {return true;}
	virtual bool HasFPL() {return true;}
	virtual bool HasMPL() {return true;}
	virtual bool HasJON() {return hasJON_;}
	virtual bool HasVEL() {return hasVEL_;}
	virtual bool Has_qTPC() {return has_qTPC_;}
   bool HasONL() const {return hasONL_;}

private:
	std::string ConvertToAxesStringWithSpaces(const std::string& axes) const;
   PIGCSControllerComDevice* deviceProxy_;
	bool hasCST_;
	bool hasSVO_;
	bool hasINI_;
	bool hasJON_;
	bool hasVEL_;
   bool has_qTPC_;
   bool hasONL_;
protected:
   //lint -e{1401} // dummy ctor without any initialization
   PIGCSControllerCom () {}
};




#endif //_PI_GCS_CONTROLLER_H_
