///////////////////////////////////////////////////////////////////////////////
// FILE:       CARVII.h
// PROJECT:    Micro-Manager
// SUBSYSTEM:  DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   BD/CrEST CARVII adapter
//                                                                                     
// AUTHOR:        G. Esteban Fernandez, g.esteban.fernandez@gmail.com, 08/19/2011
//                Based on CSU22 and LeicaDMR adapters by Nico Stuurman.
//
// COPYRIGHT:     2011, Children's Hospital Los Angeles
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
//



#ifndef _CARVII_H_
#define _CARVII_H_

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include <string>
#include <map>

//////////////////////////////////////////////////////////////////////////////
// Error codes
//
#define ERR_UNKNOWN_COMMAND          10002
#define ERR_UNKNOWN_POSITION         10003
#define ERR_HALT_COMMAND             10004

class Hub : public CGenericBase<Hub> {
public:
    Hub();
    ~Hub();

    // Device API
    // ----------
    int Initialize();
    int Shutdown();

    void GetName(char* pszName) const;
    bool Busy();

    // action interface
    // ----------------
    int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
    bool initialized_;
    // MMCore name of serial port
    std::string port_;
    // Command exchange with MMCore
    std::string command_;
    // Has a command been sent to which no answer has been received yet?
    bool pendingCommand_;
};

class Shutter : public CShutterBase<Shutter> {
public:
    Shutter();
    ~Shutter();

    int Initialize();
    int Shutdown();

    void GetName(char* pszName) const;
    bool Busy();

    // Shutter API
    int SetOpen(bool open = true);
    int GetOpen(bool& open);
    int Fire(double deltaT);

    // action interface
    int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
    bool initialized_;
    std::string name_;
    int state_;
};

class ExFilter : public CStateDeviceBase<ExFilter> {
public:
    ExFilter();
    ~ExFilter();

    // MMDevice API                                                           
    // ------------                                                           
    int Initialize();
    int Shutdown();

    void GetName(char* pszName) const;
    bool Busy();

    unsigned long GetNumberOfPositions()const {
        return numPos_;
    }

    // action interface                                                       
    // ----------------                                                       
    int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
    bool initialized_;
    unsigned numPos_;
    long pos_;
    std::string name_;
};

class EmFilter : public CStateDeviceBase<EmFilter> {
public:
    EmFilter();
    ~EmFilter();

    // MMDevice API                                                           
    // ------------                                                           
    int Initialize();
    int Shutdown();

    void GetName(char* pszName) const;
    bool Busy();

    unsigned long GetNumberOfPositions()const {
        return numPos_;
    }

    // action interface                                                       
    // ----------------                                                       
    int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
    bool initialized_;
    unsigned numPos_;
    long pos_;
    std::string name_;
};

class Dichroic : public CStateDeviceBase<Dichroic> {
public:
    Dichroic();
    ~Dichroic();

    // MMDevice API                                                           
    // ------------                                                           
    int Initialize();
    int Shutdown();

    void GetName(char* pszName) const;
    bool Busy();

    unsigned long GetNumberOfPositions()const {
        return numPos_;
    }

    // action interface                                                       
    // ----------------                                                       
    int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
    bool initialized_;
    unsigned numPos_;
    long pos_;
    std::string name_;
};

class FRAPIris : public CGenericBase<FRAPIris> {
public:
    FRAPIris();
    ~FRAPIris();

    int Initialize();
    int Shutdown();

    void GetName(char* pszName) const;
    bool Busy();

    // action interface
    int OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
    bool initialized_;
    int pos_;
    std::string name_;
};

class IntensityIris : public CGenericBase<IntensityIris> {
public:
    IntensityIris();
    ~IntensityIris();

    int Initialize();
    int Shutdown();

    void GetName(char* pszName) const;
    bool Busy();

    // action interface
    int OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
    bool initialized_;
    int pos_;
    std::string name_;
};

class SpinMotor : public CStateDeviceBase<SpinMotor> {
public:
    SpinMotor();
    ~SpinMotor();

    // MMDevice API                                                           
    // ------------                                                           
    int Initialize();
    int Shutdown();

    void GetName(char* pszName) const;
    bool Busy();

    unsigned long GetNumberOfPositions()const {
        return numPos_;
    }

    // action interface                                                       
    // ----------------                                                       
    int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
    bool initialized_;
    unsigned numPos_;
    long pos_;
    std::string name_;
};

class DiskSlider : public CStateDeviceBase<DiskSlider> {
public:
    DiskSlider();
    ~DiskSlider();

    // MMDevice API                                                           
    // ------------                                                           
    int Initialize();
    int Shutdown();

    void GetName(char* pszName) const;
    bool Busy();

    unsigned long GetNumberOfPositions()const {
        return numPos_;
    }

    // action interface                                                       
    // ----------------                                                       
    int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
    bool initialized_;
    unsigned numPos_;
    long pos_;
    std::string name_;
};

class PrismSlider : public CStateDeviceBase<PrismSlider> {
public:
    PrismSlider();
    ~PrismSlider();

    // MMDevice API                                                           
    // ------------                                                           
    int Initialize();
    int Shutdown();

    void GetName(char* pszName) const;
    bool Busy();

    unsigned long GetNumberOfPositions()const {
        return numPos_;
    }

    // action interface                                                       
    // ----------------                                                       
    int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
    bool initialized_;
    unsigned numPos_;
    long pos_;
    std::string name_;
};

class TouchScreen : public CStateDeviceBase<TouchScreen> {
public:
    TouchScreen();
    ~TouchScreen();

    // MMDevice API                                                           
    // ------------                                                           
    int Initialize();
    int Shutdown();

    void GetName(char* pszName) const;
    bool Busy();

    unsigned long GetNumberOfPositions()const {
        return numPos_;
    }

    // action interface                                                       
    // ----------------                                                       
    int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
    bool initialized_;
    unsigned numPos_;
    long pos_;
    std::string name_;
};

#endif //_CARVII_H_
