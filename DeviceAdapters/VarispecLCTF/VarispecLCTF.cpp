///////////////////////////////////////////////////////////////////////////////
// FILE:          VarispecLCTF.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   VarispecLCTF 
//
// AUTHOR:        Nick Anthony, BPL, 2018 based heavily on the the VariLC adapter by Rudolf Oldenbourg.


#include "VarispecLCTF.h"
#include <cstdio>
#include <cctype>
#include <string>
#include <math.h>
#include "../../MMDevice/ModuleInterface.h"
#include <sstream>
#include <algorithm> 


const char* g_ControllerName    = "VarispecLCTF";

const char* g_TxTerm            = "\r"; //unique termination
const char* g_RxTerm            = "\r"; //unique termination

const char* g_BaudRate_key        = "Baud Rate";
const char* g_Baud9600            = "9600";
const char* g_Baud115200          = "115200";



using namespace std;

//Local utility functions.
std::string DoubleToString(double N)
{
   ostringstream ss("");
   ss << N;
   return ss.str();
}

std::vector<double> getNumbersFromMessage(std::string VarispecLCTFmessage) {
   std::istringstream variStream(VarispecLCTFmessage);
   std::string prefix;
   double val;
   std::vector<double> values;

   variStream >> prefix;
   for (;;) 
   {
      variStream >> val;
      if (!variStream.fail()) 
      {
         values.push_back(val);
      }
      else 
      {
         break;
      }
   }

   return values;
}

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
   RegisterDevice(g_ControllerName, MM::GenericDevice, "VarispecLCTF");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0) return 0;
   if (strcmp(deviceName, g_ControllerName) == 0) 
   {
      return new VarispecLCTF();
   }
   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}





///////////////////////////////////////////////////////////////////////////////
// VarispecLCTF Hub
///////////////////////////////////////////////////////////////////////////////

VarispecLCTF::VarispecLCTF() :
   baud_(g_Baud9600),
   initialized_(false),
   initializedDelay_(false),
   answerTimeoutMs_(1000),
  wavelength_(546)
{
   InitializeDefaultErrorMessages();
   // pre-initialization properties
   // Port:
   CPropertyAction* pAct = new CPropertyAction(this, &VarispecLCTF::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);
   SetProperty(MM::g_Keyword_Port, port_.c_str());
   
   pAct = new CPropertyAction(this, &VarispecLCTF::OnBaud);
   CreateProperty(g_BaudRate_key, "Undefined", MM::String, false, pAct, true);

   AddAllowedValue(g_BaudRate_key, g_Baud115200, (long)115200);
   AddAllowedValue(g_BaudRate_key, g_Baud9600, (long)9600);

   EnableDelay();
}


VarispecLCTF::~VarispecLCTF()
{
   Shutdown();
}

void VarispecLCTF::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, g_ControllerName);
}


bool VarispecLCTF::SupportsDeviceDetection(void)
{
   return true;
}

MM::DeviceDetectionStatus VarispecLCTF::DetectDevice(void)
{
   // all conditions must be satisfied...
   MM::DeviceDetectionStatus result = MM::Misconfigured;

   try
   {
      long baud;
      GetProperty(g_BaudRate_key, baud);

      std::string transformed = port_;
      for (std::string::iterator its = transformed.begin(); its != transformed.end(); ++its)
      {
         *its = (char)tolower(*its);
      }

      if (0 < transformed.length() && 0 != transformed.compare("undefined") && 0 != transformed.compare("unknown"))
      {
         int ret = 0;
         MM::Device* pS;


         // the port property seems correct, so give it a try
         result = MM::CanNotCommunicate;
         // device specific default communication parameters
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), MM::g_Keyword_AnswerTimeout, "2000.0");
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), MM::g_Keyword_BaudRate, baud_.c_str());
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), MM::g_Keyword_DelayBetweenCharsMs, "0.0");
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), MM::g_Keyword_Handshaking, "Off");
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), MM::g_Keyword_Parity, "None");
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), MM::g_Keyword_StopBits, "1");
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), "Verbose", "1");
         pS = GetCoreCallback()->GetDevice(this, port_.c_str());
         pS->Initialize();

         PurgeComPort(port_.c_str());
         ret = sendCmd("V?", serialnum_);
         if (ret != DEVICE_OK || serialnum_.length() < 5)
         {
            LogMessageCode(ret, true);
            LogMessage(std::string("VarispecLCTF not found on ") + port_.c_str(), true);
            LogMessage(std::string("VarispecLCTF serial no:") + serialnum_, true);
            ret = 1;
            serialnum_ = "0";
            pS->Shutdown();
         }
         else
         {
            // to succeed must reach here....
            LogMessage(std::string("VarispecLCTF found on ") + port_.c_str(), true);
            LogMessage(std::string("VarispecLCTF serial no:") + serialnum_, true);
            result = MM::CanCommunicate;
            GetCoreCallback()->SetSerialProperties(port_.c_str(),
               "600.0",
               baud_.c_str(),
               "0.0",
               "Off",
               "None",
               "1");
            serialnum_ = "0";
            pS->Initialize();
            ret = sendCmd("R1");
            pS->Shutdown();
         }
      }
   }
   catch (...)
   {
      LogMessage("Exception in DetectDevice!", false);
   }
   return result;
}

int VarispecLCTF::Initialize()
{
   SetErrorText(97, "The VarispecLCTF reports that it is not exercised.");
   SetErrorText(98, "The VarispecLCTF reports that it is not initialized.");

   //Configure the com port.
   GetCoreCallback()->SetSerialProperties(port_.c_str(),
      "600.0",
      baud_.c_str(),
      "0.0",
      "Off",
      "None",
      "1");

   // empty the Rx serial buffer before sending command
   int ret = PurgeComPort(port_.c_str());
   if (ret != DEVICE_OK) { 
       LogMessage("VarispecLCTF: Failed on Purge");
	   return ret;
   }

   // Name
   ret = CreateProperty(MM::g_Keyword_Name, g_ControllerName, MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Version number
   CPropertyAction* pAct = new CPropertyAction(this, &VarispecLCTF::OnSerialNumber);
   ret = CreateProperty("Version Number", "Version Number Not Found", MM::String, true, pAct);
   if (ret != DEVICE_OK)
      return ret;

   //Set VarispecLCTF to Standard Comms mode
   ret = sendCmd("B0",getFromVarispecLCTF_);
   if (ret != DEVICE_OK) {return ret;}
   ret = sendCmd("G0"); //disable the TTL port
   if (ret != DEVICE_OK) {return ret;}

   while (true){
      ret = getStatus();
      if (ret == 98) { //Needs initialization
         LogMessage("VarispecLCTF: Running initialization");
         sendCmd("I1");
         while (reportsBusy()){
            CDeviceUtils::SleepMs(100);
         }
      }
      else if (ret == 97) { //needs exercising
         LogMessage("VarispecLCTF: Running exercise");
         sendCmd("E1");
         while (reportsBusy()){
            CDeviceUtils::SleepMs(100);
         }
      }
      else if (ret != DEVICE_OK) {
		  LogMessage("VarispecLCTF: Failed on getStatus");
         return ret;
      }
      else { //Device is ok
         break;
      }
   }

   // Wavelength
   std::string ans;
   ret = sendCmd("V?", ans);   //The serial number response also contains the tuning range of the device
   std::vector<double> nums = getNumbersFromMessage(ans);   //This will be in the format (revision level, shortest wavelength, longest wavelength, serial number).
   if (ret != DEVICE_OK){
		LogMessage("VarispecLCTF: Failed on Get Version");
	   return ret;
   }
   pAct = new CPropertyAction(this, &VarispecLCTF::OnWavelength);
   ret = CreateProperty("Wavelength", DoubleToString(wavelength_).c_str(), MM::Float, false, pAct);
   if (ret != DEVICE_OK) {
	   LogMessage("VarispecLCTF: Failed on Wavelength Property");
      return ret;
   }
   SetPropertyLimits("Wavelength", nums.at(1), nums.at(2));

   // Delay
   pAct = new CPropertyAction(this, &VarispecLCTF::OnDelay);
   ret = CreateProperty("Device Delay (ms.)", "200.0", MM::Float, false, pAct);
   if (ret != DEVICE_OK)
      return ret;
   SetPropertyLimits("Device Delay (ms.)", 0.0, 200.0);

   pAct = new CPropertyAction(this, &VarispecLCTF::OnSendToVarispecLCTF);
   ret = CreateProperty("String send to VarispecLCTF", "", MM::String, false, pAct);
   if (ret != DEVICE_OK) {
      return ret;
   }
   pAct = new CPropertyAction(this, &VarispecLCTF::OnGetFromVarispecLCTF);
   ret = CreateProperty("String from VarispecLCTF", "", MM::String, true, pAct);
   if (ret != DEVICE_OK) {
      return ret;
   }
   SetErrorText(99, "Device set busy for ");
   return DEVICE_OK;
}

int VarispecLCTF::Shutdown() {
   initialized_ = false;
   return DEVICE_OK;
}

//////////////// Action Handlers (VarispecLCTF) /////////////////

int VarispecLCTF::OnPort(MM::PropertyBase* pProp, MM::ActionType pAct)
{
   if (pAct == MM::BeforeGet)
   {
      pProp->Set(port_.c_str());
   }
   else if (pAct == MM::AfterSet)
   {
      if (initialized_)
      {
         pProp->Set(port_.c_str());
         return DEVICE_INVALID_INPUT_PARAM;
      }
      pProp->Get(port_);
   }

   return DEVICE_OK;
}

int VarispecLCTF::OnBaud(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(baud_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      if (initialized_)
      {
         pProp->Set(baud_.c_str());
         return DEVICE_INVALID_INPUT_PARAM;
      }
      pProp->Get(baud_);
   }

   return DEVICE_OK;
}


 int VarispecLCTF::OnSerialNumber(MM::PropertyBase* pProp, MM::ActionType eAct)
 {
    if (eAct == MM::BeforeGet)
    {
       int ret = sendCmd("V?", serialnum_);
       if (ret != DEVICE_OK) { return ret; }
       pProp->Set(serialnum_.c_str());
    }
    return DEVICE_OK;
 }

 int VarispecLCTF::OnWavelength(MM::PropertyBase* pProp, MM::ActionType eAct)
 {
    int ret;
    switch (eAct) {

       case (MM::BeforeGet):
       {
          std::string ans;
          ret = sendCmd("W?", ans);
          if (ret != DEVICE_OK) { return ret; }
          vector<double> numbers = getNumbersFromMessage(ans);
          if (numbers.size() == 0) 
          { //The device must have returned "W*" meaning that an invalid wavelength was sent
             SetErrorText(99, "The Varispec device was commanded to tune to an out of range wavelength.");
             return 99;
          }
          pProp->Set(numbers[0]);
          ret = getStatus();
          if (ret != DEVICE_OK) { return ret; }
          break;
       }

       case (MM::AfterSet): 
       {
          double wavelength;
          // read value from property
          pProp->Get(wavelength);
          // write wavelength out to device....
          ostringstream cmd;
          cmd.setf(ios::fixed,ios::floatfield);
          cmd.precision(3);
          cmd << "W " << wavelength;
          ret = sendCmd(cmd.str());
          if (ret != DEVICE_OK)
             return ret;
          changedTime_ = GetCurrentMMTime();
          wavelength_ = wavelength;
          break;
       }

       case (MM::IsSequenceable): 
       {
          pProp->SetSequenceable(128);   //We are using the palette functionality as this is slightly faster than specifying a wavelength jump. the limit of paletted is 128
          break;
       }
       case (MM::StartSequence): 
       {
          ret = sendCmd("M0"); //Ensure we are in sequence mode 0.
          if (ret != DEVICE_OK) {return ret;}
          ret = sendCmd("G1"); //Enable the TTL port. wavelength will change every pulse
          if (ret != DEVICE_OK) { return ret; }
          ret = sendCmd("P0");   //Go to the first pallete element before sequencing begins
          if (ret != DEVICE_OK) { return ret; }
          break;
       }

       case (MM::StopSequence): 
       {
          ret = sendCmd("G0"); //disable the TTL port
          ret |= sendCmd("P0");//Go to the first pallete element after sequencing
          if (ret != DEVICE_OK) { return ret; }
          break;
       }
       case (MM::AfterLoadSequence): 
       {
          ret = sendCmd("C1"); //Clear the devices pallete memory
          if (ret != DEVICE_OK) { return ret; }
          std::vector<std::string> sequence =  pProp->GetSequence();
          for (unsigned int i = 0; i < sequence.size(); i++) 
          {
             ostringstream cmd;
             cmd.setf(ios::fixed,ios::floatfield);
             cmd.precision(3);
             cmd << "D" << sequence.at(i) << "," << i;
             ret = sendCmd(cmd.str());   //Send the sequence over serial.
             if (ret != DEVICE_OK) { return ret; }
          }
          ret = getStatus();
          if (ret != DEVICE_OK) { return ret; }
          break;
       }

       case (MM::NoAction): 
       {
          break;
       }
   }

   return DEVICE_OK;
 }


 int VarispecLCTF::OnSendToVarispecLCTF(MM::PropertyBase* pProp, MM::ActionType eAct)
 {
    if (eAct == MM::AfterSet) {
       // read value from property
       pProp->Get(sendToVarispecLCTF_);
       int ret = sendCmd(sendToVarispecLCTF_, getFromVarispecLCTF_);
       if (ret != DEVICE_OK) { return ret; }
       ret = getStatus();
       if (ret != DEVICE_OK) { return ret; }
    }
    return DEVICE_OK;
 }

int VarispecLCTF::OnGetFromVarispecLCTF(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      //   GetSerialAnswer (port_.c_str(), "\r", getFromVarispecLCTF_);
      pProp->Set(getFromVarispecLCTF_.c_str());
   }
   return DEVICE_OK;
}

int VarispecLCTF::OnDelay(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      double delay = GetDelayMs();
      initializedDelay_ = true;
      pProp->Set(delay);
   }
   else if (eAct == MM::AfterSet)
   {
      double delayT;
      pProp->Get(delayT);
      if (initializedDelay_) {
         SetDelayMs(delayT);
      }
      delay_ = delayT * 1000;
   }
   return DEVICE_OK;
}

bool VarispecLCTF::Busy()
{
   if (delay_.getMsec() > 0.0) {
      MM::MMTime interval = GetCurrentMMTime() - changedTime_;
      if (interval.getMsec() < delay_.getMsec()) {
         return true;
      }
   }
   return false;
}



int VarispecLCTF::sendCmd(std::string cmd, std::string& out) {
   int ret = sendCmd(cmd);
   if (ret != DEVICE_OK) {
      return ret;
   }
   GetSerialAnswer(port_.c_str(), "\r", out); //Try returning any extra response from the device.
   return DEVICE_OK;
}

int VarispecLCTF::sendCmd(std::string cmd) {
   int ret = SendSerialCommand(port_.c_str(), cmd.c_str(), "\r");
   if (ret != DEVICE_OK) {
      return DEVICE_SERIAL_COMMAND_FAILED;
   }
   std::string response;
   GetSerialAnswer(port_.c_str(), "\r", response);   //Read back the response and make sure it matches what we sent. If not there is an issue with communication.
   if (response != cmd) {
      SetErrorText(99, "The VarispecLCTF did not respond.");
      return 99;
   }
   return DEVICE_OK;
}

int VarispecLCTF::getStatus() {
   std::string statuscmd = "@";
   int ret = WriteToComPort(port_.c_str(), (const unsigned char*) statuscmd.c_str(), 1);
   unsigned char ans[2];
   unsigned char tempAns[1];
   unsigned long numRead = 0;
   unsigned long numReadThisTime;
   while (numRead < 2) {
      ret = ReadFromComPort(port_.c_str(), tempAns, 1, numReadThisTime); //This function returns even if nothing is available. Causes problems.
      if (ret != DEVICE_OK) { return ret; }
      if (numReadThisTime > 0) {
         ans[numRead] = tempAns[0];
         numRead += numReadThisTime;
      }
   }
   if (ans[0] != '@') {
      SetErrorText(99, "Varispec LCTF: Did not receive '@' in response to a request for status");
      return 99;
   }
   if (ans[1] & 0x20) { //An error has occurred.
      std::string answer;
      ret = sendCmd("R?", answer);
      if (ret != DEVICE_OK) { return ret; }
      ret = sendCmd("R1"); //clear the error
      if (ret != DEVICE_OK) { return ret; }
      std::string err =  "The VarispecLCTF reports error number: " + answer;
      SetErrorText(99, err.c_str());
      return 99;
   }
   if (!(ans[1] & 0x02)) {
      return 97;
   }
   if (!(ans[1] & 0x01)) {
      return 98;
   }
   return DEVICE_OK;
}

bool VarispecLCTF::reportsBusy() {
   std::string statuscmd = "!";
   int ret = WriteToComPort(port_.c_str(), (const unsigned char*) statuscmd.c_str(), 1);
   unsigned char ans[2];
   unsigned long numRead = 0;
   unsigned char tempAns[1];
   unsigned long numReadThisTime;
   while (numRead < 2) {
      ret = ReadFromComPort(port_.c_str(), tempAns, 1, numReadThisTime); //This function returns even if nothing is available. Causes problems.
      if (ret != DEVICE_OK) { return ret; }
      if (numReadThisTime > 0) {
         ans[numRead] = tempAns[0];
         numRead += numReadThisTime;
      }
   }
   ret = ReadFromComPort(port_.c_str(), ans, 2, numRead);
   if (ans[1] == '>') {
      return false;
   }
   else if (ans[1] == '<') {
      return true;
   }
   else {
      LogMessage("Error: VarispecLCTF received invalid character in response to busy request");
      return false;
   }
}
