///////////////////////////////////////////////////////////////////////////////
// FILE:          ITC18.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   ITC18 from Instrutech
// COPYRIGHT:     University of Massachusetts Medical School, 2009
// LICENSE:       LGPL 
// AUTHOR:        Karl Bellve, Karl.Bellve@umassmed.edu
//

#ifndef _ITC18_H_
#define _ITC18_H_
#endif

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"

#ifdef linux
#include "../../../3rdparty/ITC18-lib-linux/ITC18.h"
#else 
#include "ITC18.h"
#endif

#include <string>
#include <map>
#include <fstream>


#define ERR_USB_ERROR 			101
#define ERR_BOARD_NOT_FOUND 		102
#define ERR_NO_MEMORY 			103
#define ERR_VERSION_MISMATCH 		104
#define ERR_ITC18_LIBRARY		106
#define ERR_ITC18_HUB_NOT_FOUND  	107
#define ERR_FILE_NOT_FOUND 		108
#define ERR_ITC18_NO_CHANNELS		109
#define ERR_PROTOCOL_NOT_LOADED	110

#define MAX_DACHANNELS 4
#define MAX_ADCHANNELS 8
#define myremainder(a,b) ((a) - (int)((a) / (b)) * (b))
//////////////////////////////////////////////////////////////////////////////


class CITC18Hub :  public CGenericBase<CITC18Hub>  
{
public:
    CITC18Hub();
    ~CITC18Hub();

    friend class AcqSequenceThread;

    // MMDevice API
    // ------------
    int Initialize();
    int Shutdown();

    bool Busy();
    void GetName(char* pszName) const;

    // action interface
    // ----------------

    int OnVersion(MM::PropertyBase* pPropt, MM::ActionType eAct);

private:
    long openTimeUs_;
    std::string name_;
    bool busy_;
    bool initialized_;

#ifdef WIN32
    HMODULE hITC18Dll;
#else 
    HDEVMODULE hITC18Dll;
#endif

};

class CITC18Protocol : public CGenericBase<CITC18Protocol>  
{
public:   
    CITC18Protocol();
    ~CITC18Protocol();

    // MMDevice API
    // ------------
    int Initialize();
    int Shutdown();

    bool Busy();
    int LoadProtocolFile(const char *protocol);
    int RunProtocolFile();
    int SetupSequence();
    int FindInSequence(int);

    void GetName(char* pszName) const;

    // action interface
    // ----------------
    int OnProtocolFile(MM::PropertyBase* pPropt, MM::ActionType eAct);
    int OnRunProtocolFile(MM::PropertyBase* pPropt, MM::ActionType eAct);
    int OnImages(MM::PropertyBase* pPropt, MM::ActionType eAct);
    int OnFrames(MM::PropertyBase* pPropt, MM::ActionType eAct);
    int OnSlices(MM::PropertyBase* pPropt, MM::ActionType eAct);    
    int OnChannels(MM::PropertyBase* pPropt, MM::ActionType eAct);
    int OnTime(MM::PropertyBase* pPropt, MM::ActionType eAct);

private:
    int StartDAC_[MAX_DACHANNELS];
    int StartTTL_;
    int StopTTL_;
    int TTLposition_;
    bool busy_;
    int MaxChannels_;
    int MaxDAChannels_;
    int MaxADChannels_;
    int gains_[MAX_ADCHANNELS];
    int DataSize_;
    short *pDataIn_;
    short *pDataOut_;
    long frames_;
    long slices_;
    long channels_;
    long images_;;
    long minTime_;
    long maxTime_;

    std::string name_;
    bool initialized_;

    // ITC18 Channels
    bool b_TTLIN_;
    bool b_TTLOUT_;
    bool b_AD_[MAX_ADCHANNELS];
    bool b_DA_[MAX_DACHANNELS];


    // ITC18 Channels where output data is stored individually
    std::vector<short> v_TTLOUT_;
    std::vector<short> v_DA_[MAX_DACHANNELS];

    // ITC18 Channels where input data is stored individually
    std::vector<short> v_TTLIN_;
    std::vector<short> v_AD_[MAX_ADCHANNELS];

    std::vector<short> *g_v;
    int ParseHeader(char *line);
    int MaxChannels(void);
    int GetDAid(int ID);
    int GetADid(int ID);
    void BuildBuffer(short *pBuffer, int start, int end);  
};


class CITC18Shutter : public CShutterBase<CITC18Shutter>  
{
public:
    CITC18Shutter();
    ~CITC18Shutter();

    // MMDevice API
    // ------------
    int Initialize();
    int Shutdown();

    void GetName(char* pszName) const;
    bool Busy();

    // Shutter API
    int SetOpen(bool open = true);
    int GetOpen(bool& open);
    int Fire(double deltaT);

    // action interfacevi 
    // ----------------
    int OnCommand(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnTTLPort(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnOpeningTime(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnClosingTime(MM::PropertyBase* pProp, MM::ActionType eAct);  


private:
    // Time it takes after issuing Close command to close the shutter
    double closingTimeMs_;
    // Time it takes after issuing Open command to open the shutter
    double openingTimeMs_;
    // Time that last command was sent to shutter
    MM::MMTime changedTime_;

    // Last command sent to the controller
    std::string lastCommand_;
    std::string command_;

    long TTLPort_;
    int ITC18RunOnce(short value);
    bool initialized_;
    std::string name_;
    bool busy_;

};

class CITC18DAC : public CSignalIOBase<CITC18DAC>  
{
public:
    CITC18DAC();
    ~CITC18DAC();

    // MMDevice API
    // ------------
    int Initialize();
    int Shutdown();

    void GetName(char* pszName) const;
    bool Busy() {return busy_;}

    // DA API
    int SetGateOpen(bool open);
    int GetGateOpen(bool& open) {open = gateOpen_; return DEVICE_OK;};
    int SetSignal(double volts);
    int GetSignal(double& volts) {volts_ = volts; return DEVICE_UNSUPPORTED_COMMAND;}     
    int GetLimits(double& minVolts, double& maxVolts) {minVolts = minV_; maxVolts = maxV_; return DEVICE_OK;}


    // action interface
    // ----------------
    int OnVolts(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnMaxVolt(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnDACPort(MM::PropertyBase* pProp, MM::ActionType eAct);
    
    // These commands are not supported on this device
    // Sequence functions
    int IsDASequenceable(bool& isSequenceable) const {isSequenceable = false; return DEVICE_OK;}
    int GetDASequenceMaxLength(long& nrEvents) const  {nrEvents = 0; return DEVICE_OK;}
    int StartDASequence() const {return DEVICE_OK;} 
    int StopDASequence() const {return DEVICE_OK;}
    int LoadDASequence(std::vector<double> voltages) const {return DEVICE_OK;} 
    int ClearDASequence() {return DEVICE_OK;}
    int AddToDASequence(double voltage) {return DEVICE_OK;}
    int SendDASequence() const {return DEVICE_OK;}

    
   
    
private:
    int ITC18RunOnce(double value);

    std::string name_;
    bool initialized_;
    double minV_;
    double maxV_;
    double volts_;
    double gatedVolts_;
    int DACPort_;
    int maxChannel_;
    bool busy_;
    bool gateOpen_;
};

class CITC18ADC : public CSignalIOBase<CITC18ADC>  
{
public:
     CITC18ADC();
     ~CITC18ADC();
     
     // MMDevice API
     // ------------
     int Initialize();
     int Shutdown();
     
     void GetName(char* pszName) const;
     bool Busy() {return busy_;}
     double GetRangeFactor();
     
     // DA API
     int SetGateOpen(bool open);
     int GetGateOpen(bool& open) {open = gateOpen_; return DEVICE_OK;};
     int SetSignal(double volts) {return DEVICE_UNSUPPORTED_COMMAND;};
     int GetSignal(double& volts);
     int GetLimits(double& minVolts, double& maxVolts) {minVolts = minV_; maxVolts = maxV_; return DEVICE_OK;};
     
     // action interface
     // ----------------
     int OnVolts(MM::PropertyBase* pProp, MM::ActionType eAct);
     int OnRange(MM::PropertyBase* pProp, MM::ActionType eAct);
     int OnADCPort(MM::PropertyBase* pProp, MM::ActionType eAct);
     
     // These commands are not supported on this device
     // Sequence functions
     int IsDASequenceable(bool& isSequenceable) const {isSequenceable = false; return DEVICE_OK;}
     int GetDASequenceMaxLength(long& nrEvents) const  {nrEvents = 0; return DEVICE_OK;}
     int StartDASequence() const {return DEVICE_OK;} 
     int StopDASequence() const {return DEVICE_OK;}
     int LoadDASequence(std::vector<double> voltages) const {return DEVICE_OK;}
     int ClearDASequence() {return DEVICE_OK;}
     int AddToDASequence(double voltage) {return DEVICE_OK;}
     int SendDASequence() const {return DEVICE_OK;}

     
private:
     int ITC18RunOnce(double &value);		
     std::string name_;
     std::string range_;
     bool initialized_;
     double minV_;
     double maxV_;
     double volts_;
     double gatedVolts_;
     int ADCPort_;
     int maxChannel_;
     bool busy_;
     bool gateOpen_;
};

/**
 * Acquisition thread for controlling the ITC18
 */
class AcqSequenceThread : public MMDeviceThreadBase
{
public:
     AcqSequenceThread(CITC18Hub* pITC18Hub) : 
     busy_(false), 
     stop_(false),
     mode_(1),
     externalTrigger_(0),
     outputEnabled_(1),
     externalClock_(0),
     waitTime_(1000)
     {
          pITC18Hub_ = pITC18Hub;
     };

     ~AcqSequenceThread() {};
     
     int svc(void);

     void SetDataOut(int dataSizeOut, short *dataOut) {dataSizeOut_ = dataSizeOut;dataOut_ = dataOut;}
     void SetDataIn(int dataSizeIn, short *dataIn) {dataSizeIn_ = dataSizeIn;dataIn_ = dataIn;}
     void SetDevice(void *itc) {itc_ = itc;}
     void SetWaitTime (long waitTime) { waitTime_ = waitTime;}
     void SetSequence (int size, int *instructions) { sequenceSize_ = size;sequenceInstructions_ = instructions; }
     int SetInterval (float interval);
     
     void SetExternalTrigger (int trigger) {  externalTrigger_ = trigger; }
     void SetExternalClock (int clock) { externalClock_ = clock; }
     int GetMode() { return mode_;};
     void Stop();
     void Start(int mode);
     bool Busy() {return busy_;}

private:
     CITC18Hub* pITC18Hub_;
     bool busy_;
     bool stop_;
     long mode_;
     long waitTime_;
     short *dataOut_;
     int dataSizeOut_;
     int ticks_;
     short *dataIn_;
     int dataSizeIn_;
     void *itc_;
     int interval_;
     int sequenceSize_;
     int *sequenceInstructions_;
     int externalTrigger_;
     int outputEnabled_;
     int externalClock_;
     int dataWritten_,dataRead_;
     int readPosition_,writePosition_;

     int Poll(void);
     int Write(short* data, int limit, int& written);
     int Read(short* data, int limit, int& read);
};
