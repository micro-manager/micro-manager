/*
ScopeLED Device Adapters for Micro-Manager. 
Copyright (C) 2011-2013 ScopeLED

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

#define DICON_USB_VID_NEW 0x24C2
#define DICON_USB_VID_OLD 0xC251
#define QUOTE(str) #str
#define STR(str) QUOTE(str)

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

    HANDLE m_evAbortRx;

	std::string J_version; //20150203 Jason modify long m_version; =>  std::string m_version;
	 std::string m_SerialNumber;

    static const char* const s_ControModeStrings[];
	static const char* const b_ControModeStrings[];
	//static const char* const b_ControModeStrings_Intensity[];
	//static const char* const b_ControModeStrings_Shutter[];
	 
    static const long MAX_CONTROL_MODE;
	static const long MAX_CONTROL_MODE_b;

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

    void InitSerialNumber()
    {
#ifdef SCOPELED_DEBUGLOG
        m_LogFile << "+ScopeLEDBasicIlluminator::InitSerialNumber" << std::endl;
#endif
        char* pBuffer = NULL;
        unsigned long cbBuffer = 0;
        g_USBCommAdapter.GetSerialNumber(m_hDevice, NULL, &cbBuffer);

        if (cbBuffer > 1)
        {
            pBuffer = new char[cbBuffer];
            int result = g_USBCommAdapter.GetSerialNumber(m_hDevice, pBuffer, &cbBuffer);
            if (FALSE != result)
            {
                //SetProperty("SerialNumber", pBuffer);
                m_SerialNumber = pBuffer;
#ifdef SCOPELED_DEBUGLOG
                m_LogFile << "ScopeLEDBasicIlluminator InitSerialNumber: " << m_SerialNumber << std::endl;
#endif
            }
#ifdef SCOPELED_DEBUGLOG
            else
            {
                m_LogFile << "ScopeLEDBasicIlluminator InitSerialNumber Failed B, " << std::dec << result << std::endl;
            }
#endif
            delete [] pBuffer;
        }
#ifdef SCOPELED_DEBUGLOG
        else
        {
            m_LogFile << "ScopeLEDBasicIlluminator InitSerialNumber Failed A, " << cbBuffer << std::endl;
        }
#endif
#ifdef SCOPELED_DEBUGLOG
        m_LogFile << "-ScopeLEDBasicIlluminator::InitSerialNumber" << std::endl;
#endif
    }

    void GetVersion() //20150203 Jason modify long GetVersion()  => char* GetVersion
    {
       /* if (0 == m_version)
        {*/
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
			 if ((DEVICE_OK == nRet) && (cbRx >= 10) && (0== reply[3]))
            {
				char temp[] = {reply[4]+'0' , '.',reply[5]+'0' , '.', reply[6]+'0' , '.', reply[7]+'0' , '\0' };
				J_version = temp;  //20150203 Jason add
				 /*for (int i=0; i<4; i++)
                {
                    m_version |= (reply[4+i] << ((3-i)*8));
                }*/
            }
        //}
#ifdef SCOPELED_DEBUGLOG
        m_LogFile << "ScopeLEDBasicIlluminator GetVersion, version=" << m_version << std::endl;
#endif
       // return m_version; //20150203 Jason modify
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
        m_LogFile << "ScopeLEDBasicIlluminator SetShutter. Result=" << std::dec << result << std::endl;
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

        if ((DEVICE_OK == result) && (cbRxBuffer == 7) && (0==RxBuffer[3]))
        {
            open = 0 != RxBuffer[4];
        }
        else
        {
            open = false;
        }
#ifdef SCOPELED_DEBUGLOG
        m_LogFile << "ScopeLEDBasicIlluminator GetShutter. Open=" << std::dec << open << ". Result=" << std::dec << result << std::endl;
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
        cmdbuf[3] = (unsigned char) mode; // 20140910 Jason modify  cmdbuf[3] =  (unsigned char) (mode-1) => cmdbuf[3] = (unsigned char) mode; // 20140821  Jason add -1 //cmdbuf[3] = (unsigned char) mode; =>  (unsigned char) (mode-1) 
        cmdbuf[5] = 0x5C;  // End Byte

        unsigned char* const pChecksum = &cmdbuf[4];
        unsigned char* const pStart = &cmdbuf[1];

        *pChecksum = g_USBCommAdapter.CalculateChecksum(pStart, *pStart);
        const int result = Transact(cmdbuf, sizeof(cmdbuf));

#ifdef SCOPELED_DEBUGLOG
        m_LogFile << "ScopeLEDBasicIlluminator SetControlMode, mode=" << mode << ". Result=" << std::dec << result << "." << std::endl;
#endif
        return result;
    }

    int GetControlMode(long& mode)
    {
#ifdef SCOPELED_DEBUGLOG
        m_LogFile << "+ScopeLEDBasicIlluminator GetControlMode" << std::endl;
#endif

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

        if ((DEVICE_OK == result) && (cbRxBuffer == 7) && (0==RxBuffer[3]))
        {
           mode =RxBuffer[4]; // 20140910 Jason modify mode =RxBuffer[4]+1 => mode =RxBuffer[4]  // 20140821  Jason add +1 //mode = RxBuffer[4];
        }
        else
        {
            mode = 0;
        }
#ifdef SCOPELED_DEBUGLOG
        m_LogFile << "-ScopeLEDBasicIlluminator GetControlMode, mode=" << std::dec << mode << ". Result=" << std::dec << result << std::endl;
#endif
        return result;
    }

    virtual void ClearOpticalState()
    {

    }

public:
    ScopeLEDBasicIlluminator() :
        m_hDevice(NULL),
        m_TxnTime(0), m_lastDeviceResult(-1), m_version(0),
        m_evAbortRx(CreateEvent(NULL, TRUE, FALSE, NULL))
#ifdef SCOPELED_DEBUGLOG
        ,m_LogFile("c:\\mmlog.txt"/* std::ios_base::out | std::ios_base::app*/)
#endif
    {
#ifdef SCOPELED_DEBUGLOG
        m_LogFile << "ScopeLEDBasicIlluminator ctor" << std::endl;
#endif
        CreateStringProperty("InitSerialNumber", "", false, NULL, true);
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
        J_version.clear(); //20150203 Jason add  
        m_SerialNumber.clear();
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
#ifdef SCOPELED_DEBUGLOG
        m_LogFile << "+ScopeLEDBasicIlluminator::OnControlMode" << std::endl;
#endif
        int result = DEVICE_OK;
        if (eAct == MM::AfterSet)
        {
            std::string strMode;
            if (pProp->Get(strMode))
            {
                long mode;
                /*pProp->GetData(strMode.c_str(), mode))*/
                result = GetPropertyData("ControlMode", strMode.c_str(), mode);
                if (DEVICE_OK == result)
                {
                    result = SetControlMode(mode);
                }
            }
            else result = DEVICE_NO_PROPERTY_DATA;
        }
#ifdef SCOPELED_DEBUGLOG
        m_LogFile << "-ScopeLEDBasicIlluminator::OnControlMode" << std::endl;
#endif
        return result;
    }
//	int OnControlMode_b(MM::PropertyBase* pProp, MM::ActionType eAct)
//    {
//#ifdef SCOPELED_DEBUGLOG
//        m_LogFile << "+ScopeLEDBasicIlluminator::OnControlMode" << std::endl;
//#endif
//        int result = DEVICE_OK;
//        if (eAct == MM::AfterSet)
//        {
//            std::string strMode;
//            if (pProp->Get(strMode))
//            {
//                long mode;
//                /*pProp->GetData(strMode.c_str(), mode))*/
//                result = GetPropertyData("ControlMode", strMode.c_str(), mode);
//                if (DEVICE_OK == result)
//                {
//                    result = SetControlMode(mode);
//                }
//            }
//            else result = DEVICE_NO_PROPERTY_DATA;
//        }
//#ifdef SCOPELED_DEBUGLOG
//        m_LogFile << "-ScopeLEDBasicIlluminator::
//			" << std::endl;
//#endif
//        return result;
//    }
};
template <class T> const char* const ScopeLEDBasicIlluminator<T>::s_ControModeStrings[] = { "", "Normal", "Analog", "TTL" };  //20140910 jason add "", "Analog", "Normal", "TTL" => "", "Normal", "Analog", "TTL"
template <class T> const char* const ScopeLEDBasicIlluminator<T>::b_ControModeStrings[] = {"Analog+TTL", "Analog+USB", "USB+TTL", "USB+USB" }; 
//template <class T> const char* const ScopeLEDBasicIlluminator<T>::b_ControModeStrings_Intensity[] = {"Analog", "USB" };   //20150203 jason add ""
//template <class T> const char* const ScopeLEDBasicIlluminator<T>::b_ControModeStrings_Shutter[] = {"TTL", "USB" };   //20150203 jason add ""
template <class T> const long ScopeLEDBasicIlluminator<T>::MAX_CONTROL_MODE = 4L;
//template <class T> const long ScopeLEDBasicIlluminator<T>::MAX_CONTROL_MODE_b = 2; //20150203 jason add ""
#endif

