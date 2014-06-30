///////////////////////////////////////////////////////////////////////////////
// FILE:          Okolab.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Okolab generic device adapter
//                
// AUTHOR:        Domenico Mastronardi @ Okolab
//                
// COPYRIGHT:     Okolab s.r.l.
//
// LICENSE:       This file is distributed under the BSD license.
//
// REVISION 2014-04-30:
//					Increased UDP receive timeout to 5 seconds (it was 1 second).
//							
// REVISION 2013-09-23:
//					First release.
//

#include "Okolab.h"
#include "TestControl.h"
#include "OCSControl.h"
#include "H201BLControl.h"
#include "H301BLControl.h"
#include "O2BLControl.h"
#include "CO2BLControl.h"
#include "CO2O2BLControl.h"
#include "CO2O2BL13Control.h"
#include "ActiveHmdControl.h"

#include "../../MMDevice/ModuleInterface.h"
// #include <sstream>

/* Total receive timeout is (RCV_RETRIES * RCV_SLEEP_TIME_MS)
 * To increase the timeout, increase retries number.
 */
#define RCV_RETRIES			100
#define RCV_SLEEP_TIME_MS	50

#ifdef WIN32
 #include <windows.h>
 #include <tchar.h>
 #define snprintf _snprintf 
 #include <winsock.h>
 #pragma comment(lib, "ws2_32.lib") 
#endif

#if defined(WIN32) || defined(WIN64) || defined(X64)
 #include <windows.h>
 #include <process.h>
 #include <Tlhelp32.h>
 #include <winbase.h>
 #include <string.h>
#endif

const char* g_DeviceName = "Okolab";

extern const char* g_TestControl;
extern const char* g_OCSControl;
extern const char* g_H201BLControl;
extern const char* g_H301BLControl;
extern const char* g_CO2BLControl;
extern const char* g_CO2O2BLControl;
extern const char* g_CO2O2BL13Control;
extern const char* g_O2BLControl;
extern const char* g_HmdControl;


#ifdef WIN32
 #define SCK_VERSION1            0x0101
 #define SCK_VERSION2            0x0202
 int PASCAL WSAStartup(WORD,LPWSADATA);
 int PASCAL WSACleanup(void);
 typedef WSADATA *LPWSADATA;
#endif


bool SockSetup()
{
 WSADATA wsadata;
 int error=WSAStartup(0x0202, &wsadata);
 if(error) return false;
 if(wsadata.wVersion!=0x0202)
  {
   WSACleanup(); 
   return false;
  }
 return true;
}



///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////


/**
 *  List all supported hardware devices 
 */
MODULE_API void InitializeModuleData()
{
 #ifdef _DEBUG_
 RegisterDevice(g_TestControl, MM::GenericDevice, "Okolab Test Control");
 #endif
 RegisterDevice(g_OCSControl, MM::GenericDevice, "Okolab OKO Control Server");
 RegisterDevice(g_CO2BLControl, MM::GenericDevice, "Okolab CO2 Unit-BL");
 RegisterDevice(g_O2BLControl, MM::GenericDevice, "Okolab O2 Unit-BL");
 RegisterDevice(g_H201BLControl, MM::GenericDevice, "Okolab H201 T Unit-BL");
 RegisterDevice(g_CO2O2BLControl, MM::GenericDevice, "Okolab CO2-O2 Unit-BL [0-10;1-18]");
 RegisterDevice(g_CO2O2BL13Control, MM::GenericDevice, "Okolab CO2-O2 Unit-BL [0-20;1-95]");
 RegisterDevice(g_H301BLControl, MM::GenericDevice, "Okolab H301 T Unit-BL");
 RegisterDevice(g_HmdControl, MM::GenericDevice, "Okolab H301-HM-ACTIVE");
 SockSetup();
}



MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
 if(deviceName==0) return 0;

 if(strcmp(deviceName, g_OCSControl)==0)
  {
   return new OCSControl();
  }
 if(strcmp(deviceName, g_H201BLControl)==0)
  {
   return new H201BLControl();
  }
 if(strcmp(deviceName, g_CO2BLControl)==0)
  {
   return new CO2BLControl();
  }
 if(strcmp(deviceName, g_O2BLControl)==0)
  {
   return new O2BLControl();
  }
 if(strcmp(deviceName, g_CO2O2BLControl)==0)
  {
   return new CO2O2BLControl();
  }
 if(strcmp(deviceName, g_CO2O2BL13Control)==0)
  {
   return new CO2O2BL13Control();
  }
 if(strcmp(deviceName, g_H301BLControl)==0)
  {
   return new H301BLControl();
  }
  if(strcmp(deviceName, g_HmdControl)==0)
  {
	  return new HmdControl();
  }

 #ifdef _DEBUG_
 if(strcmp(deviceName, g_TestControl)==0)
  {
   return new TestControl();
  }
 #endif

 return 0;
}


MODULE_API void DeleteDevice(MM::Device* pDevice)
{
 delete pDevice;
}


///////////////////////////////////////////////////////////////////////////////
// OkolabGenericDevice implementation
// 

/**
 *  Okolab generic device 
 */
OkolabDevice::OkolabDevice() :
 ipaddress_("127.0.0.1"), ipport_(60000), version_(_VERSION_)
{
 rcv_statuscode=0;
 rcv_answer[0]='\0';
 last_sent_cmd[0]='\0';
 product_id=-1;
 device_id=-1;
}


OkolabDevice::~OkolabDevice()
{
 OCSConnectionClose();
}


int OkolabDevice::GetDeviceId()
{
 return this->device_id;
}


void OkolabDevice::SetDeviceId(int id)
{
 this->device_id=id;
}


bool OkolabDevice::OCSConnectionOpen(unsigned short PortNo, char* IPAddress)
{
 int err;
 SOCKADDR_IN target; 

 target.sin_family = AF_INET; 
 target.sin_port = htons(PortNo); 
 target.sin_addr.s_addr = inet_addr(IPAddress);

 s=socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP); 
 if(s==INVALID_SOCKET) return false;   

 #ifdef WIN32
  u_long iMode=1; // nonblock
  ioctlsocket(s,FIONBIO,&iMode); 
 #endif

 err=connect(s,(SOCKADDR *)&target,sizeof(target));
 if(err==SOCKET_ERROR) return false; 
 return true;
}


void OkolabDevice::OCSConnectionClose()
{
 if(s) closesocket(s);
}


/**
 * Send command to OCS and wait response
 *
 * @param  char *cmd    command string
 * @param  char *par1   first parameter
 * @param  char *par2   second parameter
 * @param  char *par3   third parameter
 *
 * @return true if receives a formally correct answer
 *
 *  Answer values are stored in the member variables:
 *    rcv_statuscode;
 * 	 rcv_answer;
 */
bool OkolabDevice::OCSSendRcvdCommand(char *cmd, char *par1, char *par2, char *par3)
{
 int ires;
 int t=0;
 char sendbuf[51];
 char recvbuf[100];
 char echo[51];
 char answer[51];

 rcv_answer[0]='\0';
 rcv_statuscode=-1;

 // snprintf(sendbuf,50,"%s %s %s %s\r",cmd,par1,par2,par3);
 snprintf(sendbuf,50,"%s",cmd);
 if( (strlen(par1)>0) && (strlen(sendbuf)+strlen(par1)<49) ) { strcat(sendbuf," "); strcat(sendbuf,par1); };
 if( (strlen(par2)>0) && (strlen(sendbuf)+strlen(par2)<49) ) { strcat(sendbuf," "); strcat(sendbuf,par2); };
 if( (strlen(par3)>0) && (strlen(sendbuf)+strlen(par3)<49) ) { strcat(sendbuf," "); strcat(sendbuf,par3); };
 strcat(sendbuf,"\r");

 ires=send(s,sendbuf,(int)strlen(sendbuf),0);
 if(ires==SOCKET_ERROR) return false;
 strcpy(last_sent_cmd,sendbuf); last_sent_cmd[strlen(last_sent_cmd)-1]='\0'; // purge '\r'

 memset(recvbuf,0,99); 
 do
  { 
   ires=recv(s,recvbuf,100,0); 
   if(ires==-1)
    {
     t++;
     Sleep(RCV_SLEEP_TIME_MS);
    }
  }
 while((ires<0)&&(t<RCV_RETRIES));
 if(ires>0) recvbuf[ires]='\0';

 // parse answer
 if(strlen(recvbuf)>0)
  {
   if(sscanf(recvbuf,"%d_%[^_]_%[^\r]\r",&rcv_statuscode,echo,answer)!=3) return false;
   if(strcmp(last_sent_cmd,echo)) return false; // to be sure that received answer concerns last sent command
   strcpy(rcv_answer,answer);
//   if(_LOG_==1) { LogMessage("test"); }

/*
FILE *fp;
fp=fopen("log.txt","a");
if(OCSRunning()) fprintf(fp,"running=1\n");
else fprintf(fp,"running=1\n");
//fprintf(fp,"rcv_answer=%s\n",rcv_answer);
fclose(fp);
*/

   sscanf(answer,"e%d",&last_error_ncode); // set last error number-code
   return true;
  }

 return false;
}


/*
 * Retreive last answer number-code received 
 *
 * @return numeric value of last error (0 if no error)
 */
int OkolabDevice::OCSGetLastAnswerError()
{
 return last_error_ncode;
}



/**
 * Get connection status of a product  
 * 
 * @params int product_id
 * @return int 1=connected 
 */
int OkolabDevice::IsDeviceConnected(int product_id)
{
 int ret=0;
 char param[10];
 int rv[3]; 
 rv[0]=0;rv[1]=0;rv[2]=0;
 sprintf(param,"%d",product_id);
 if(!OCSConnectionOpen(ipport_,ipaddress_)) return -2;
 if(!OCSSendRcvdCommand("getprodinfo",param,"","")) return -3;
 OCSConnectionClose();
 if(rcv_statuscode!=0) return -4;
 sscanf(rcv_answer,"%d %d %d",&rv[0],&rv[1],&rv[2]);
 if(rv[1]==1) ret=1;
 return ret;
}



/**
 * Get working status (connected and working) of a product  
 * 
 * @params int product_id
 * @return int 1=connected 
 */
int OkolabDevice::IsDeviceWorking(int product_id)
{
 int ret=-4;
 char param[10];
 int rv[3]; 
 rv[0]=0;rv[1]=0;rv[2]=0;
 sprintf(param,"%d",product_id);
 if(!OCSConnectionOpen(ipport_,ipaddress_)) return -2;
 if(!OCSSendRcvdCommand("getprodinfo",param,"","")) return -3;
 OCSConnectionClose();
 sscanf(rcv_answer,"%d %d %d",&rv[0],&rv[1],&rv[2]);
 if((rcv_statuscode<2) && (rv[1]==1)) ret=1;
 return ret;
}




/*
 *  Obtains ComPort on witch OCS is connected to this device
 * 
 *  @return 1 if all ok otherwise negative code
 */
int OkolabDevice::GetCommPort(char *strcommport)
{   
 int pn;
 int ret=1;
 char param[2][10];
 sprintf(param[0],"%d",product_id);
 sprintf(param[1],"%d",device_id);
 strcpy(strcommport,"Undefined");
 if(!OCSConnectionOpen(ipport_,ipaddress_)) return -1;
 if(!OCSSendRcvdCommand("comport",param[0],param[1],"")) return -2;
 OCSConnectionClose();
 if(rcv_statuscode!=0) return -3;
 sscanf(rcv_answer,"%d",&pn);
 if((pn>0) && (pn<256)) sprintf(strcommport,"COM %d",pn);
 else { strcpy(strcommport,"-"); ret=0; }
 return ret; 
} 



/*
 *  Send comport value to OCS
 */
int OkolabDevice::SetCommPort(int port)
{
 char strP[8];
 char param[2][10];
 sprintf(param[0],"%d",product_id);
 sprintf(param[1],"%d",device_id);
 snprintf(strP,8,"%d",port);
 if(!OCSConnectionOpen(ipport_,ipaddress_)) return -1;
 if(!OCSSendRcvdCommand("comport",param[0],param[1],strP)) return -2; 
 OCSConnectionClose();
 if(rcv_statuscode!=0) return -3;
 return 1;                                           
}



/*
 *  Obtains main-parameter value from OCS
 */
int OkolabDevice::GetValue(double& val)
{   
 char param[2][10];
 sprintf(param[0],"%d",product_id);
 sprintf(param[1],"%d",device_id);
 if(!OCSConnectionOpen(ipport_,ipaddress_)) return -1;
 if(!OCSSendRcvdCommand("readval",param[0],param[1],"-1")) return -2;
 OCSConnectionClose();
 if(rcv_statuscode!=0) return -3;
 val=atof(rcv_answer);
 return 1;                                                         
} 


/*
 *  Obtains set-point value from OCS
 */
int OkolabDevice::GetSetPoint(double& sp)
{   
 char param[2][10];
 sprintf(param[0],"%d",product_id);
 sprintf(param[1],"%d",device_id);
 if(!OCSConnectionOpen(ipport_,ipaddress_)) return -1;
 if(!OCSSendRcvdCommand("readsetpoint",param[0],param[1],"-1")) return -2; 
 OCSConnectionClose();
 if(rcv_statuscode!=0) return -3;
 sp=atof(rcv_answer);
 return 1;                                                         
} 


/*
 *  Send set-point value to OCS
 */
int OkolabDevice::SetSetPoint(double sp)
{
 char strSP[8];
 char param[2][10];
 sprintf(param[0],"%d",product_id);
 sprintf(param[1],"%d",device_id);
 snprintf(strSP,8,"%.01f",sp);
 if(!OCSConnectionOpen(ipport_,ipaddress_)) return -1;
 if(!OCSSendRcvdCommand("writesetpoint",param[0],param[1],strSP)) return -2; 
 OCSConnectionClose();
 if(rcv_statuscode!=0) return -3;
 return 1;                                           
} 



/**
*  Try to connect device 
*  @return int  1 if successful connected
*/
int OkolabDevice::TryConnectDevice(int iport)
{
 char strPort[3];
 char param[2][10];
 sprintf(param[0],"%d",product_id);
 sprintf(param[1],"%d",device_id);

 if(iport<0) return -1;
 // disconnect first 
 if(!OCSConnectionOpen(ipport_,ipaddress_)) return -1;
 if(!OCSSendRcvdCommand("connect",param[0],"0","")) return -1; 
 OCSConnectionClose();

 sprintf(strPort,"%d",iport);
 if(!OCSConnectionOpen(ipport_,ipaddress_)) return -1;
 if(!OCSSendRcvdCommand("comport",param[0],param[1],strPort)) return -1; 
 OCSConnectionClose();
 if(rcv_statuscode!=0) return -1;
 if(strcmp(rcv_answer,"1")!=0) return -1;

 if(!OCSConnectionOpen(ipport_,ipaddress_)) return -1;
 if(!OCSSendRcvdCommand("checkcon",param[0],"","")) return -1; 
 OCSConnectionClose();
 if(rcv_statuscode!=0) return -1;
 if(strcmp(rcv_answer,"1")!=0) return -1;

 return 1;
}



/**
*  Detect device status (connection status)
*  [Try to connect]
*/
MM::DeviceDetectionStatus OkolabDevice::Detect(void)
{
 int iport=-1;
 char param[2][10];
 sprintf(param[0],"%d",product_id);
 sprintf(param[1],"%d",device_id);

 if(initialized_) return MM::CanCommunicate;

 MM::DeviceDetectionStatus result=MM::Misconfigured;
 try
  {
   std::string portLowerCase=port_;
   for(std::string::iterator its=portLowerCase.begin(); its!=portLowerCase.end(); ++its)
    {
     *its=(char)tolower(*its);
    }

   if(0<portLowerCase.length() &&  0!=portLowerCase.compare("undefined")  && 0!=portLowerCase.compare("unknown") )
    {
     result=MM::CanNotCommunicate;
	 if(sscanf(portLowerCase.c_str(),"com%d",&iport)!=1) return MM::Misconfigured;
     if(TryConnectDevice(iport)==1) return MM::CanCommunicate;
    }
   }
  catch(...)
   {
//    LogMessage("DetectDevice Exception!",false);
   }
 return result;
}



/*
 *  Detect if OCS is running
 *  @return boolean 
 */
bool OkolabDevice::OCSRunning()
{
 bool bRunning=false;
 /*
 if(!OCSConnectionOpen(ipport_,ipaddress_)) return false;
 if(!OCSSendRcvdCommand("listprod","","","")) return false;
 OCSConnectionClose();
 if(rcv_statuscode!=0) return false;
 bRunning=true;
 */
 #if defined(WIN32) || defined(WIN64) || defined(X64)
   HANDLE hSnapShot = CreateToolhelp32Snapshot(TH32CS_SNAPALL, NULL);
   PROCESSENTRY32 pEntry;
   pEntry.dwSize = sizeof (pEntry);
   BOOL hRes = Process32First(hSnapShot, &pEntry);
   while(hRes)
    {
     /*
     FILE *fp;
     fp=fopen("processes_log.txt","a");
     char buffer[200];
     wcstombs(buffer, pEntry.szExeFile, sizeof(buffer));
     fprintf(fp,"%s\n",buffer);
     fclose(fp);
     */
     if(lstrcmp(pEntry.szExeFile, _T("OKO-Control Server.exe"))==0)
      {
       bRunning=true;
      }
     hRes=Process32Next(hSnapShot, &pEntry);
	}
 #endif

 return bRunning;
}



/*
 *  Try to start OCS
 */
void OkolabDevice::OCSStart()
{
 #if defined(WIN32) || defined(WIN64)

 char cmdline[500]; 

 DWORD size=1023;
 DWORD type=REG_SZ;
 TCHAR wvalue[255];
 HKEY hKey;

/*
 #if defined(WIN32)
 if(RegOpenKeyEx(HKEY_LOCAL_MACHINE, L"SOFTWARE\\Microsoft\\Windows\\CurrentVersion", 0, KEY_READ | KEY_WOW64_64KEY, &hKey)==ERROR_SUCCESS)
  {
   DWORD dwret;
   dwret=RegQueryValueEx(hKey, L"ProgramFilesDir", NULL, &type, (LPBYTE)wvalue, &size);
   RegCloseKey(hKey);
  }
 #endif

 #if defined(WIN64) || defined(X64)
 if(RegOpenKeyEx(HKEY_LOCAL_MACHINE, L"SOFTWARE\\Microsoft\\Windows\\CurrentVersion", 0, KEY_READ | KEY_WOW64_64KEY, &hKey)==ERROR_SUCCESS)
  {
   DWORD dwret;
   dwret=RegQueryValueEx(hKey, L"ProgramFilesDir (x86)", NULL, &type, (LPBYTE)wvalue, &size);
   RegCloseKey(hKey);
  }
 #endif
*/

 if(RegOpenKeyEx(HKEY_LOCAL_MACHINE, L"SOFTWARE\\Wow6432Node\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\{8AEF906D-2228-4298-B37E-OKOCTRLSERV2}_is1", 0, KEY_READ | KEY_WOW64_64KEY, &hKey)==ERROR_SUCCESS)
  {
   DWORD dwret;
   dwret=RegQueryValueEx(hKey, L"Inno Setup: App Path", NULL, &type, (LPBYTE)wvalue, &size);
   RegCloseKey(hKey);
  }
 else
 if(RegOpenKeyEx(HKEY_LOCAL_MACHINE, L"SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\{8AEF906D-2228-4298-B37E-OKOCTRLSERV2}_is1", 0, KEY_READ | KEY_WOW64_64KEY, &hKey)==ERROR_SUCCESS)
  {
   DWORD dwret;
   dwret=RegQueryValueEx(hKey, L"Inno Setup: App Path", NULL, &type, (LPBYTE)wvalue, &size);
   RegCloseKey(hKey);
  }

 char value[400]; 
 WideCharToMultiByte(CP_ACP,0,wvalue,255,value,400,NULL,NULL);
// snprintf(cmdline,500,"\"%s\\OKOlab\\OKO-Control Server\\OKO-Control Server.exe\"",value);
 snprintf(cmdline,500,"\"%s\\OKO-Control Server.exe\"",value);

/*
FILE *fp;
fp=fopen("registry_log.txt","a");
fprintf(fp,"cmdline=%s\n",cmdline);
fclose(fp);
*/

 if(OCSRunning()) return;
 TCHAR path[500];
 MultiByteToWideChar(CP_ACP, MB_PRECOMPOSED, cmdline, -1, path, 500);

 SHELLEXECUTEINFO ShExecInfo;
 ShExecInfo.cbSize = sizeof(SHELLEXECUTEINFO);
 ShExecInfo.fMask = NULL;
 ShExecInfo.hwnd = NULL;
 ShExecInfo.lpVerb = NULL;
 ShExecInfo.lpFile = path;
 ShExecInfo.lpParameters = NULL;
 ShExecInfo.lpDirectory = NULL;
 ShExecInfo.nShow = SW_MAXIMIZE;
 ShExecInfo.hInstApp = NULL;
 ShellExecuteEx(&ShExecInfo);

 #endif
}


/*
 *  Try to stop OCS
 *  @return 1 if all ok otherwise negative code
*/
int OkolabDevice::OCSStop()
{
 if(!OCSRunning()) return -1;
 if(!OCSConnectionOpen(ipport_,ipaddress_)) return -2;
 if(!OCSSendRcvdCommand("close", "", "", "")) return -3;
 OCSConnectionClose();
 if(rcv_statuscode!=0) return -4;
 return 1;
}
