/*
DiCon Illuminator Device Adapter for Micro-Manager. 
Copyright (C) 2011 DiCon Lighting, Inc

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

    
#ifndef DiConBasicIlluminator_h
#define DiConBasicIlluminator_h

#include "USBCommAdapter.h"
#include <DeviceBase.h>
#include <time.h>

template <class T> class DiConBasicIlluminator : public CShutterBase<T>
{
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
public:
    DiConBasicIlluminator() :
        m_state(false), m_hDevice(NULL),
        m_TxnTime(0), m_lastDeviceResult(-1), 
        m_vid(0x24C2), m_pid(0),
        m_evAbortRx(CreateEvent(NULL, TRUE, FALSE, NULL))
    {

    }
    ~DiConBasicIlluminator()
    {
        CloseHandle(m_evAbortRx);
    }

    int Shutdown()
    {
        g_USBCommAdapter.Close(m_hDevice);
        m_hDevice = NULL;
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
};

#endif

