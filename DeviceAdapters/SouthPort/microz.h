#ifndef DEMO_Z_H
#define DEMO_Z_H

#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/ImgBuffer.h"
#include "../../MMDevice/DeviceThreads.h"
#include <string>
#include <map>
#include <algorithm>
#include <iostream>
#include <bitset>
#include <stdint.h>

#include <stdlib.h>
#include <stddef.h>
#include <stdio.h>

#define		CRC_POLY_16		    0xA001
#define		CRC_START_MODBUS	0xFFFF

static bool crc_tab16_init = false;
static uint16_t crc_tab16[256];

uint16_t crc16_modbus(const unsigned char* input_str, size_t num_bytes);
void crc16_init(void);

//////////////////////////////////////////////////////////////////////////////
// MicroZStage class
//////////////////////////////////////////////////////////////////////////////
class MicroZStage : public CStageBase<MicroZStage>
{
public:
	MicroZStage();
	~MicroZStage();

	void GetName(char* pszName) const;
	void SetType(bool isJog);

	int Initialize();
	int Shutdown();

	// Stage API
	int SetPositionUm(double pos);
	int GetPositionUm(double& pos);
	double GetStepSize();
	int SetPositionSteps(long steps);
	int GetPositionSteps(long& steps);
	int SetOrigin();
	int GetLimits(double& lower, double& upper);
	int Move(double /*v*/);
	int SetRelativePositionUm(double d);
	int Home();

	bool IsContinuousFocusDrive() const;

	// Sequence functions
	int IsStageSequenceable(bool& isSequenceable) const;
	int GetStageSequenceMaxLength(long& nrEvents) const;
	int StartStageSequence();
	int StopStageSequence();
	int ClearStageSequence();
	int AddToStageSequence(double);
	int SendStageSequence();

private:
	static double stepSize_um_;
	double pos_um_;
	bool busy_;
	bool initialized_;
	std::string port_;
	MM::Core *core_;
	MM::Device *device_;
	bool isReverseDirection_;
	long velocity_;
	bool isJog_;
	bool isJogRunning_;
	bool isSetZero_;
	double goPos_;

	// Actions
	int OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSequence(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSetVelocity(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSetStepSize(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnReverseDirection(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnZHome(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnGoto(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnGotoValue(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSetZero(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnEStop(MM::PropertyBase* pProp, MM::ActionType eAct);

	// Utils
	bool Busy();
	int CheckDeviceStatus();

	int CommandQuery(unsigned char* command, size_t c_len, unsigned char* answer, size_t a_len);
	int SendData(unsigned char *data, unsigned int size);
};

#endif
