///////////////////////////////////////////////////////////////////////////////
// FILE:       XLIGHT.h
// PROJECT:    Micro-Manager
// SUBSYSTEM:  DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   CrEST XLight adapter
//                                                                                     
// AUTHOR:        E. Chiarappa echiarappa@libero.it, 01/20/2014
//                Based on CARVII adapter by  G. Esteban Fernandez.
//
// COPYRIGHT:     2014, Crestoptics s.r.l.
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



#ifndef _XLUPD_H_
#define _XLUPD_H_

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

 
class Emission : public CStateDeviceBase<Emission> {
public:
    Emission();
    ~Emission();

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

class Excitation : public CStateDeviceBase<Excitation> {
public:
    Excitation();
    ~Excitation();

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
	static const char cmd_letter = 'E'; // use 'B' for testing, revert to 'E' before shipping
    bool initialized_;
	bool use_new_command_;
    unsigned numPos_;
    long pos_;
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

#endif //_XLIGHT_H_
