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

template <class T> class ScopeLEDBasicIlluminator : public CShutterBase<T>
{
    long m_version;
protected:
    bool m_state;
    HANDLE m_hDevice;

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

        /*
        s_LogFile << "<(Tx)";
        for (unsigned long i=0; i < cbCommand; i++)
        {
            s_LogFile << " 0x" << std::hex << (int) pCommand[i];
        }
        */
        
        m_lastDeviceResult = -1;
        unsigned long length = cbCommand;
        clock_t start_time = clock();
        if (FALSE == g_USBCommAdapter.Write(m_hDevice, pCommand, &length))
        {
            ok = DEVICE_ERR;
            //s_LogFile << " {FAILED!}" << std::endl;
        }
        else
        {
            bool repeat;
            //s_LogFile << std::endl;
            do
            {
                repeat = false;
                memset(pRxBuffer, 0, *pcbRxBuffer);
                int result = g_USBCommAdapter.Read(m_hDevice, pRxBuffer, pcbRxBuffer, DefaultTimeoutRx(), m_evAbortRx);

                /*
                s_LogFile << ">(Rx)";
                for (unsigned long i=0; i < *pcbRxBuffer; i++)
                {
                    s_LogFile << " 0x" << std::hex << (int) pRxBuffer[i];
                }
                s_LogFile << std::endl;
                */
                
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
            }
            delete [] pBuffer;
        }
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
                memcpy(&m_version, &reply[4], min(sizeof(m_version), 4));
            }
            else
            {
                memset(&m_version, 0, sizeof(m_version));
            }
        }
        return m_version;
    }
public:
    ScopeLEDBasicIlluminator() :
        m_state(false), m_hDevice(NULL),
        m_TxnTime(0), m_lastDeviceResult(-1), 
        m_vid(0x24C2), m_pid(0), m_version(0),
        m_evAbortRx(CreateEvent(NULL, TRUE, FALSE, NULL))
    {
        CreateProperty("SerialNumber", "", MM::String, false);
    }
    ~ScopeLEDBasicIlluminator()
    {
        CloseHandle(m_evAbortRx);
    }

    int Shutdown()
    {
        g_USBCommAdapter.Close(m_hDevice);
        m_hDevice = NULL;
        SetProperty("SerialNumber", "");
        return DEVICE_OK;
    }

    //void GetName (char* pszName) const;
    bool Busy()
    {
        return false;
    }

// Shutter API
    //int SetOpen (bool open = true)
    int GetOpen(bool& open)
    {
        open = m_state;
        return DEVICE_OK;
    }
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
        if (eAct == MM::BeforeGet)
        {
            if (m_state)
                pProp->Set(1L);
            else
                pProp->Set(0L);
        }
        else if (eAct == MM::AfterSet)
        {
            long state;
            pProp->Get(state);

            // apply the value
            SetOpen(0 != state);
        }
        return DEVICE_OK;
    }
    int OnLastError(MM::PropertyBase* pProp, MM::ActionType eAct)
    {
        if (eAct == MM::BeforeGet)
        {
            long error = g_USBCommAdapter.GetLastError(); 
            pProp->Set(error);
        }
        else if (eAct == MM::AfterSet)
        {
            // never do anything!!
        }
        return DEVICE_OK;
    }

    int OnLastDeviceResult(MM::PropertyBase* pProp, MM::ActionType eAct)
    {
        if (eAct == MM::BeforeGet)
        {
            pProp->Set(m_lastDeviceResult);
        }
        else if (eAct == MM::AfterSet)
        {
            // never do anything!!
        }
        return DEVICE_OK;
    }

    int OnTransactionTime(MM::PropertyBase* pProp, MM::ActionType eAct)
    {
        if (eAct == MM::BeforeGet)
        {
            pProp->Set(m_TxnTime);
        }
        else if (eAct == MM::AfterSet)
        {
            // never do anything!!
        }
        return DEVICE_OK;
    }

    int OnVersion(MM::PropertyBase* pProp, MM::ActionType eAct)
    {
        if (eAct == MM::BeforeGet)
        {
            pProp->Set(GetVersion());
        }
        else if (eAct == MM::AfterSet)
        {
            // never do anything!!
        }
        return DEVICE_OK;
    }
};

#endif

