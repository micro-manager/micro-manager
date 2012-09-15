/*
ScopeLED Device Adapters for Micro-Manager. 
Copyright (C) 2011-2012 ScopeLED

This adapter is free software: you can redistribute it and/or modify
it under the terms of the GNU Lesser General Public License as
published by the Free Software Foundation, either version 3 of the
License, or (at your option) any later version.

This software is distributed in the hope that it will be useful, but
WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
Lesser General Public License for more details.

You should have received a copy of the GNU Lesser General Public
License along with this software.  If not, see
<http://www.gnu.org/licenses/>.
    
*/

    
#ifndef ScopeLEDBasicIlluminator_h
#define ScopeLEDBasicIlluminator_h

#include "USBCommAdapter.h"
#include <DeviceBase.h>
#include <time.h>
#include <fstream>

//#define SCOPELED_DEBUGLOG

template <class T> class ScopeLEDBasicIlluminator : public CShutterBase<T>
{
    long m_version;
protected:
    HANDLE m_hDevice;

#ifdef SCOPELED_DEBUGLOG
    std::ofstream m_LogFile;
#endif

    long m_TxnTime;
    long m_lastDeviceResult;

    long m_vid,m_pid;
    HANDLE m_evAbortRx;

    static unsigned long DefaultTimeoutRx()
    {
        return 2000;
    }

    int Transact(const unsigned char* const pCommand, unsigned long cbCommand, unsigned char* pRxBuffer, unsigned long* pcbRxBuffer)
    {
        int ok = DEVICE_OK;

        if (NULL == m_hDevice)
        {
#ifdef SCOPELED_DEBUGLOG
            m_LogFile << "ScopeLEDBasicIlluminator Transact DEVICE_NOT_CONNECTED" << std::endl;
#endif
            return DEVICE_NOT_CONNECTED;
        }
        
#ifdef SCOPELED_DEBUGLOG
        m_LogFile << "<(Tx)";
        for (unsigned long i=0; i < cbCommand; i++)
        {
            m_LogFile << " 0x" << std::hex << (int) pCommand[i];
        }
#endif
        
        m_lastDeviceResult = -1;
        unsigned long length = cbCommand;
        clock_t start_time = clock();
        if (FALSE == g_USBCommAdapter.Write(m_hDevice, pCommand, &length))
        {
            ok = DEVICE_ERR;
#ifdef SCOPELED_DEBUGLOG
            m_LogFile << " {FAILED!}" << std::endl;
#endif
        }
        else
        {
            bool repeat;
#ifdef SCOPELED_DEBUGLOG
            m_LogFile << std::endl;
#endif
            do
            {
                repeat = false;
                memset(pRxBuffer, 0, *pcbRxBuffer);
                int result = g_USBCommAdapter.Read(m_hDevice, pRxBuffer, pcbRxBuffer, DefaultTimeoutRx(), m_evAbortRx);

#ifdef SCOPELED_DEBUGLOG
                m_LogFile << ">(Rx)";
                for (unsigned long i=0; i < *pcbRxBuffer; i++)
                {
                    m_LogFile << " 0x" << std::hex << (int) pRxBuffer[i];
                }
                m_LogFile << std::endl;
#endif
                
                if (FALSE == result)
                {
                    ok = DEVICE_ERR;
                }
                else
                {
                    if (*pcbRxBuffer >= 3)
                    {
                        bool command_match = pCommand[2] == pRxBuffer[2];
                        if (command_match && (*pcbRxBuffer >= 4))
                        {
                            m_lastDeviceResult = pRxBuffer[3];
                        }
                        repeat = !command_match;
                    }
                }
            }
            while (repeat);
        }
        m_TxnTime = clock() - start_time;
        return ok;
    }
    
    int Transact(const unsigned char* const pCommand, unsigned long cbCommand)
    {
        unsigned char RxBuffer[32];
        unsigned long cbRxBuffer = sizeof(RxBuffer);
        return Transact(pCommand, cbCommand, RxBuffer, &cbRxBuffer);
    }

    void QuerySerialNumber()
    {
        char* pBuffer = NULL;
        unsigned long cbBuffer = 0;
        g_USBCommAdapter.GetSerialNumber(m_hDevice, NULL, &cbBuffer);

        if (cbBuffer > 1)
        {
            pBuffer = new char[cbBuffer];
            int result = g_USBCommAdapter.GetSerialNumber(m_hDevice, pBuffer, &cbBuffer);
            if (FALSE != result)
            {
                SetProperty("SerialNumber", pBuffer);
#ifdef SCOPELED_DEBUGLOG
                m_LogFile << "ScopeLEDBasicIlluminator QuerySerialNumber: " << pBuffer << std::endl;
#endif
            }
#ifdef SCOPELED_DEBUGLOG
            else
            {
                m_LogFile << "ScopeLEDBasicIlluminator QuerySerialNumber Failed B, " << result << std::endl;
            }
#endif
            delete [] pBuffer;
        }
#ifdef SCOPELED_DEBUGLOG
        else
        {
            m_LogFile << "ScopeLEDBasicIlluminator QuerySerialNumber Failed A, " << cbBuffer << std::endl;
        }
#endif
    }

    long GetVersion()
    {
        if (0 == m_version)
        {
            unsigned char cmdbuf[5];
            unsigned char reply[32];
            unsigned long cbTx, cbRx;

            cmdbuf[0] = 0xA9;   // Start Byte
            cmdbuf[1] = 2;      // Length
            cmdbuf[2] = 12;     // Command
            cmdbuf[4] = 0x5C;   // Stop Byte

            unsigned char* const pChecksum = &cmdbuf[3];
            unsigned char* const pStart = &cmdbuf[1];    

            *pChecksum = g_USBCommAdapter.CalculateChecksum(pStart, *pStart);

            cbTx = sizeof(cmdbuf);
            cbRx = sizeof(reply);

            int nRet = Transact(cmdbuf, cbTx, reply, &cbRx);
            // reply[3] = Result code
            // reply[4]..reply[7] = Version
            if ((DEVICE_OK == nRet) && (cbRx >= 10) && (0 == reply[3]))
            {
                for (int i=0; i<4; i++)
                {
                    m_version |= (reply[4+i] << ((3-i)*8));
                }
            }
        }
        return m_version;
    }

    int SetShutter(bool open)
    {
        unsigned char cmdbuf[6];
        memset(cmdbuf, 0, sizeof(cmdbuf));
        cmdbuf[0] = 0xA9;  // Start Byte
        cmdbuf[1] = 0x03;  // Length Byte
        cmdbuf[2] =   41;  // Command Byte - MSG_SET_MASTER_ENABLE
        cmdbuf[3] = open;
        cmdbuf[5] = 0x5C;  // End Byte

        unsigned char* const pChecksum = &cmdbuf[4];
        unsigned char* const pStart = &cmdbuf[1];

        *pChecksum = g_USBCommAdapter.CalculateChecksum(pStart, *pStart);
        int result = Transact(cmdbuf, sizeof(cmdbuf));
#ifdef SCOPELED_DEBUGLOG
        m_LogFile << "ScopeLEDBasicIlluminator SetShutter, " << result << std::endl;
#endif
        return result;
    }

    int GetShutter(bool& open)
    {
        unsigned char cmdbuf[5];
        memset(cmdbuf, 0, sizeof(cmdbuf));
        cmdbuf[0] = 0xA9;  // Start Byte
        cmdbuf[1] = 0x02;  // Length Byte
        cmdbuf[2] =   42;  // Command Byte - MSG_GET_MASTER_ENABLE
        cmdbuf[4] = 0x5C;  // End Byte

        unsigned char* const pChecksum = &cmdbuf[3];
        unsigned char* const pStart = &cmdbuf[1];

        *pChecksum = g_USBCommAdapter.CalculateChecksum(pStart, *pStart);

        unsigned char RxBuffer[16];
        unsigned long cbRxBuffer = sizeof(RxBuffer);
        int result = Transact(cmdbuf, sizeof(cmdbuf), RxBuffer, &cbRxBuffer);

        if ((DEVICE_OK == result) && (cbRxBuffer >= 5))
        {
            open = 0 != RxBuffer[4];
        }
        else
        {
            open = false;
        }
#ifdef SCOPELED_DEBUGLOG
        m_LogFile << "ScopeLEDBasicIlluminator GetShutter, " << result << std::endl;
#endif
        return result;
    }

    int SetControlMode(long mode)
    {
        unsigned char cmdbuf[6];
        memset(cmdbuf, 0, sizeof(cmdbuf));
        cmdbuf[0] = 0xA9;  // Start Byte
        cmdbuf[1] = 0x03;  // Length Byte
        cmdbuf[2] =   39;  // Command Byte - MSG_SET_OPERATING_MODE
        cmdbuf[3] = (unsigned char) mode;
        cmdbuf[5] = 0x5C;  // End Byte

        unsigned char* const pChecksum = &cmdbuf[4];
        unsigned char* const pStart = &cmdbuf[1];

        *pChecksum = g_USBCommAdapter.CalculateChecksum(pStart, *pStart);
        return Transact(cmdbuf, sizeof(cmdbuf));
    }

    int GetControlMode(long& mode)
    {
        unsigned char cmdbuf[5];
        memset(cmdbuf, 0, sizeof(cmdbuf));
        cmdbuf[0] = 0xA9;  // Start Byte
        cmdbuf[1] = 0x02;  // Length Byte
        cmdbuf[2] =   38;  // Command Byte - MSG_GET_OPERATING_MODE
        cmdbuf[4] = 0x5C;  // End Byte

        unsigned char* const pChecksum = &cmdbuf[3];
        unsigned char* const pStart = &cmdbuf[1];

        *pChecksum = g_USBCommAdapter.CalculateChecksum(pStart, *pStart);

        unsigned char RxBuffer[16];
        unsigned long cbRxBuffer = sizeof(RxBuffer);
        int result = Transact(cmdbuf, sizeof(cmdbuf), RxBuffer, &cbRxBuffer);

        if ((DEVICE_OK == result) && (cbRxBuffer >= 5))
        {
            mode = RxBuffer[4];
        }
        else
        {
            mode = 0;
        }
        return result;
    }

    virtual void ClearOpticalState()
    {

    }

public:
    ScopeLEDBasicIlluminator() :
        m_hDevice(NULL),
        m_TxnTime(0), m_lastDeviceResult(-1), 
        m_vid(0x24C2), m_pid(0), m_version(0),
        m_evAbortRx(CreateEvent(NULL, TRUE, FALSE, NULL))
#ifdef SCOPELED_DEBUGLOG
        ,m_LogFile("c:\\mmlog.txt")
#endif
    {
#ifdef SCOPELED_DEBUGLOG
        m_LogFile << "ScopeLEDBasicIlluminator ctor" << std::endl;
#endif
        CreateProperty("SerialNumber", "", MM::String, false, NULL, true);
    }
    ~ScopeLEDBasicIlluminator()
    {
        CloseHandle(m_evAbortRx);
#ifdef SCOPELED_DEBUGLOG
        m_LogFile << "ScopeLEDBasicIlluminator dtor" << std::endl;
#endif
    }

    int Shutdown()
    {
        g_USBCommAdapter.Close(m_hDevice);
        m_hDevice = NULL;
        m_version = 0;
        SetProperty("SerialNumber", "");
#ifdef SCOPELED_DEBUGLOG
        m_LogFile << "ScopeLEDBasicIlluminator Shutdown" << std::endl;
#endif
        ClearOpticalState();
        return DEVICE_OK;
    }

    //void GetName (char* pszName) const;
    bool Busy()
    {
        return false;
    }

// Shutter API
    //int SetOpen (bool open = true)
    //int GetOpen(bool& open);
    int Fire(double /*deltaT*/)
    {
        return DEVICE_UNSUPPORTED_COMMAND;
    }

    // action interface
    int OnVendorID(MM::PropertyBase* pProp, MM::ActionType eAct)
    {
        if (eAct == MM::BeforeGet)
        {
            pProp->Set(m_vid);
        }
        else if (eAct == MM::AfterSet)
        {
            pProp->Get(m_vid);
        }
        return DEVICE_OK;
    }
    int OnProductID(MM::PropertyBase* pProp, MM::ActionType eAct)
    {
        if (eAct == MM::BeforeGet)
        {
            pProp->Set(m_pid);
        }
        else if (eAct == MM::AfterSet)
        {
            pProp->Get(m_pid);
        }
        return DEVICE_OK;
    }

    int OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
    {
        int result = DEVICE_OK;
        if (eAct == MM::BeforeGet)
        {
            bool open = false;
            result = GetOpen(open);
            if (open)
                pProp->Set(1L);
            else
                pProp->Set(0L);
        }
        else if (eAct == MM::AfterSet)
        {
            long state;
            pProp->Get(state);
            result = SetOpen(0 != state);
        }
        return result;
    }
    int OnLastError(MM::PropertyBase* pProp, MM::ActionType eAct)
    {
        if (eAct == MM::BeforeGet)
        {
            long error = g_USBCommAdapter.GetLastError(); 
            pProp->Set(error);
            return DEVICE_OK;
        }
        return DEVICE_CAN_NOT_SET_PROPERTY;
    }

    int OnLastDeviceResult(MM::PropertyBase* pProp, MM::ActionType eAct)
    {
        if (eAct == MM::BeforeGet)
        {
            pProp->Set(m_lastDeviceResult);
            return DEVICE_OK;
        }
        return DEVICE_CAN_NOT_SET_PROPERTY;
    }

    int OnTransactionTime(MM::PropertyBase* pProp, MM::ActionType eAct)
    {
        if (eAct == MM::BeforeGet)
        {
            pProp->Set(m_TxnTime);
            return DEVICE_OK;
        }
        return DEVICE_CAN_NOT_SET_PROPERTY;
    }

    int OnVersion(MM::PropertyBase* pProp, MM::ActionType eAct)
    {
        if (eAct == MM::BeforeGet)
        {
            pProp->Set(GetVersion());
            return DEVICE_OK;
        }
        return DEVICE_CAN_NOT_SET_PROPERTY;
    }

    int OnControlMode(MM::PropertyBase* pProp, MM::ActionType eAct)
    {
        int result = DEVICE_OK;
        long mode = 0;
        if (eAct == MM::BeforeGet)
        {
            result = GetControlMode(mode);
            if (DEVICE_OK == result)
            {
                pProp->Set(mode);
            }
        }
        else if (eAct == MM::AfterSet)
        {
            pProp->Get(mode);
            result = SetControlMode(mode);
        }
        return result;
    }

    int OnControlModeString(MM::PropertyBase* pProp, MM::ActionType eAct)
    {
        int result = DEVICE_CAN_NOT_SET_PROPERTY;   
        if (eAct == MM::BeforeGet)
        {
            long mode = 0;
            std::string mode_str;
            result = GetControlMode(mode);
            if (DEVICE_OK == result)
            {
                switch (mode)
                {
                    case 0:
                        mode_str = "";
                        break;
                    case 1:
                        mode_str = "Normal Control Mode";
                        break;
                    case 2:
                        mode_str = "Analog Control Mode";
                        break;
                    case 3:
                        mode_str = "TTL Control Mode";
                        break;
                    default:
                        mode_str = "Unrecognized Control Mode";
                        break;
                }
                pProp->Set(mode_str.c_str());
            }
        }
        return result;
    }
};

#endif

