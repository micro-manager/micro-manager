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
// CVS:           $Id: Controller.h,v 1.7, 2010-12-09 12:04:30Z, Rachel Bach$
//

#ifndef _PI_CONTROLLER_H_
#define _PI_CONTROLLER_H_

#define COM_ERROR -1L
#define PI_CNTR_NO_ERROR  0L
#define PI_CNTR_UNKNOWN_COMMAND 2L
#define PI_CNTR_MOVE_WITHOUT_REF_OR_NO_SERVO 5L
#define PI_CNTR_POS_OUT_OF_LIMITS  7L
#define PI_CNTR_AXIS_UNDER_JOYSTICK_CONTROL  51L

#define ERR_GCS_PI_CNTR_POS_OUT_OF_LIMITS 102
#define ERR_GCS_PI_CNTR_MOVE_WITHOUT_REF_OR_NO_SERVO 103
#define ERR_GCS_PI_CNTR_AXIS_UNDER_JOYSTICK_CONTROL 104

extern const char* g_msg_CNTR_POS_OUT_OF_LIMITS;
extern const char* g_msg_CNTR_MOVE_WITHOUT_REF_OR_NO_SERVO;
extern const char* g_msg_CNTR_AXIS_UNDER_JOYSTICK_CONTROL;

#include "../../MMDevice/DeviceBase.h"
#include <string>

//////////////////////////////////////////////////////////////////////////////
// Error codes
//
#define ERR_PORT_CHANGE_FORBIDDEN    10004
#define ERR_UNRECOGNIZED_ANSWER      10009
#define ERR_OFFSET 10100

#ifndef WIN32
#define WINAPI
#define BOOL int
#define TRUE 1
#define FALSE 0
#endif

size_t ci_find(const std::string& str1, const std::string& str2);

class PIController
{
public:
	PIController(const std::string& label);
	virtual ~PIController();

	static PIController* GetByLabel(const std::string& label);
	static void DeleteByLabel(const std::string& label);

	int InitStage(const std::string& axisName, const std::string& stageType);

	bool IsBusy();
	int Home(const std::string& axesNames, const std::string& homingMode);
	double umToDefaultUnit_;

	std::string MakeAxesString(const std::string& axis1Name, const std::string& axis2Name);
	static std::vector<std::string> tokenize(const std::string& lines);

	int TranslateError( int err = PI_CNTR_NO_ERROR );

	virtual bool qIDN(std::string& sIDN) { return false;}
	virtual bool INI(const std::string& axis) {return false;}
	virtual bool CST(const std::string& axis, const std::string& stagetype){return false;}
	virtual bool SVO(const std::string& axis, BOOL svo) {return false;}
	virtual bool FRF(const std::string& axes) {return false;}
	virtual bool REF(const std::string& axes) {return false;}
	virtual bool MNL(const std::string& axes) {return false;}
	virtual bool FNL(const std::string& axes) {return false;}
	virtual bool FPL(const std::string& axes) {return false;}
	virtual bool MPL(const std::string& axes) {return false;}
	virtual int GetError() {return PI_CNTR_NO_ERROR;}
	//virtual bool IsReferenceOK(const std::string& axes, BOOL* ) {return false;}
	virtual bool IsReferencing(const std::string& axes, BOOL* ) {return false;}
	virtual bool IsControllerReady( BOOL* ) {return false;}
	virtual bool IsMoving(const std::string& axes, BOOL* ) {return false;}
	virtual bool MOV(const std::string& axis, const double* target) {return false;}
	virtual bool MOV(const std::string& axis1, const std::string& axis2, const double* target) {return false;}
	virtual bool qPOS(const std::string& axis, double* position) {return false;}
	virtual bool qPOS(const std::string& axis1, const std::string& axis2, double* position) {return false;}
	virtual bool STP() {return false;}
	virtual bool JON(int joystick, int state) {return false;}
	virtual bool qJON(int joystick, int& state) {return false;}
	virtual bool VEL(const std::string& axis, const double* velocity)  {return false;}
	virtual bool qVEL(const std::string& axis, double* velocity) {return false;}
	virtual bool qTPC(int& nrOutputChannels) {return false;}

	virtual bool HasINI() {return false;}
	virtual bool HasSVO() {return false;}
	virtual bool HasCST() {return false;}
	//virtual bool HasIsReferenceOK() {return false;}
	virtual bool HasIsReferencing() {return false;}
	virtual bool HasIsControllerReady() {return false;}
	virtual bool HasIsMoving() {return false;}
	virtual bool HasFRF() {return false;}
	virtual bool HasREF() {return false;}
	virtual bool HasFNL() {return false;}
	virtual bool HasMNL() {return false;}
	virtual bool HasFPL() {return false;}
	virtual bool HasMPL() {return false;}
	virtual bool HasJON() {return false;}
	virtual bool HasVEL() {return false;}
	virtual bool Has_qTPC() {return false;}

	int FindNrJoysticks();
   int OnJoystick(MM::PropertyBase* pProp, MM::ActionType eAct, int joystick);
   int GetNrOutputChannels();

protected:

	bool gcs2_;
	std::string label_;
   bool onlyIDSTAGEvalid_;
	static std::map<std::string, PIController*> allControllersByLabel_;
};



#endif //_PI_CONTROLLER_H_
