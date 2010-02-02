// Test_Serial.cpp : Defines the entry point for the console application.
//
//

#include "../../MMCore/MMCore.h"
#include "../../MMCore/CoreCallback.h"
#include <string>
#include <iostream>
#include <iomanip>
#include <vector>

using namespace std;
 
// This should work on all Unix platforms
#if !defined(_WIN32)
unsigned long GetClockTicksUs()
{
   struct timeval t;
   gettimeofday(&t,NULL);
   return (long) t.tv_sec * 1000000L + t.tv_usec;
}
#endif



int main(int argc, char* argv[])
{

   if ( argc < 2 )
   {
     std::cerr << "Usage: " << argv[0] << " <port> " << std::endl ;
     return 1 ;
  }
   
   char termSend[2] = {13, 0}; // CR
   char termRcv[2] = {10, 0}; // LF
   CMMCore core;
   char reply;
   unsigned long read=0;
   const char* port= argv[1];
   std::vector<string> command;
   //const char* port="/dev/tty.KeySerial1";

   try
   {
      core.unloadAllDevices();
      
      core.loadDevice("P", "SerialManager", port);
      core.setProperty("P", "BaudRate", "300");
      core.setProperty("P", "StopBits", "1");
      core.loadDevice("Uniblitz", "Vincent", "VMMController");
      core.setProperty("Uniblitz", "Port", "P");
      core.initializeAllDevices();
      //core.setProperty("Uniblitz", "Command", "Open"); 
      core.setProperty("Uniblitz", "Command", "Close"); 
      usleep(50000);
      core.setProperty("Uniblitz", "Command", "Open"); 
      core.waitForDevice("Uniblitz");
      long start = GetClockTicksUs();
      core.setProperty("Uniblitz", "Command", "Close"); 
      core.waitForDevice("Uniblitz");
      long end = GetClockTicksUs();
      long tt = end - start;
      cout << tt << endl;
      usleep(50000);
      core.setProperty("Uniblitz", "Command", "Open"); 
      core.waitForDevice("Uniblitz");
      core.setProperty("Uniblitz", "Command", "Close"); 
      usleep(50000);

      //
      //command.push_back("@");
     // core.writeToSerialPort("P",command);
      // read an array of characters
    
      /*
      std::vector<char> response;
      bool done=false;
      while (!done)
      {
         response = core.readFromSerialPort("P");
         //sleep(1);
         //std::cout << response << std::endl;
         if (response.size() > 0 ) {
            for (int i=0; i< response.size(); i++) {
               std::cout << response[i];
               if (response[i]=='!') {
                  done=true;
               }  
               //if (response[i]=='\r') {
               //   std::cout << std::endl;
               //}  
            } 
            std::cout << std::endl;
         }
      }
      std::string answer;
      do {
         answer = core.getSerialPortAnswer("P","\r");
         std::cout << answer << " Answer1 " << std::endl;
      } while (true);
       while (true);
      // print characters on the screen
      for (unsigned i=0; i<response.size(); i++)
         std::cout << response[i];
     
       std::cout << std::endl;
*/
      /*
      cout << "Hello" << endl; 
      string resp="";
      string resp2="";
      resp = core.getSerialPortAnswer("P", "!");
      printf("%s\n",resp.c_str());
      cout << "Hello2" << endl; 

      sleep(1);
      core.setSerialPortCommand("P", "T", "\r");
      resp = core.getSerialPortAnswer("P", "\r");
      resp2 = core.getSerialPortAnswer("P","\r");
      cout << resp << endl << resp2 << endl;
      sleep(1);
      core.setSerialPortCommand("P", "S", "\r");
      resp = core.getSerialPortAnswer("P","\r");
      resp2 = core.getSerialPortAnswer("P","\r");
      cout << resp << endl << resp2 << endl;
      */

      //std::string resp = core.getProperty("ASI", "Response");
      //core.waitForDevice("ASI");

      //core.unloadAllDevices();
      //core.loadDevice("P", "SerialManager", "COM1");
      //core.loadDevice("ASI", "Ludl", "ASIController");
      //core.setProperty("ASI", "Port", "P");
      //core.initializeAllDevices();

      //core.setProperty("ASI", "Command", "WHERE \r"); 
      //resp = core.getProperty("ASI", "Response");
      //core.waitForDevice("ASI");

      //core.unloadAllDevices();
      //core.loadDevice("P", "SerialManager", "COM1");
      //core.loadDevice("ASI", "Ludl", "ASIController");
      //core.setProperty("ASI", "Port", "P");
      //core.initializeAllDevices();

      //core.setProperty("ASI", "Command", "WHERE \r"); 
      //resp = core.getProperty("ASI", "Response");
      //core.waitForDevice("ASI");


     // // COM port
     // core.loadDevice(port, "SerialManager", "COM4");
     // core.setProperty(port, "StopBits", "1");
     //
     // // Lambda devices
     // /*
     // core.loadDevice("SB", "SutterLambda", "Shutter-B");
     // core.setProperty("SB", "Port", port);
     // core.loadDevice("WA", "SutterLambda", "Wheel-A");
     // core.setProperty("WA", "Port", port);
     // core.loadDevice("WB", "SutterLambda", "Wheel-B");
     // core.setProperty("WB", "Port", port);
     // */

     // // Ludl (ASI) devices
     // core.loadDevice("X", "Ludl", "Stage");
     // core.setProperty("X", "Port", port);
     // core.setProperty("X", "ID", "120");
     // core.setProperty("X", "CommandSet", "1");

     // core.loadDevice("Y", "Ludl", "Stage");
     // core.setProperty("Y", "Port", port);
     // core.setProperty("Y", "ID", "121");
     // core.setProperty("Y", "CommandSet", "1");
     //
     // core.loadDevice("Z", "Ludl", "Stage");
     // core.setProperty("Z", "Port", port);
     // core.setProperty("Z", "ID", "122");
     // core.setProperty("Z", "CommandSet", "1");

     // core.loadDevice("Cam", "DemoCamera", "DCam");

     // core.initializeAllDevices();

     // //core.assignImageSynchro("WA");
     // //core.assignImageSynchro("WB");
     // core.assignImageSynchro("X");
     // core.assignImageSynchro("Y");
     // core.assignImageSynchro("Z");

     // /*
     // // test Lambda
     // core.setState("WA", 2);
     // core.setState("WB", 2);
     // core.snapImage();
     // core.setState("WA", 5);
     // core.setState("WB", 5);
     // */

     // // test ludl-type stage
     // bool busy = core.deviceBusy("X");
     // double x, y, z;
     // x = core.getPosition("X");
     // y = core.getPosition("Y");
     // z = core.getPosition("Z");

     // double newX = x + 3000;
     // double newY = y + 3000;
     // double newZ = z + 100;

     // core.setPosition("X", newX);
     // core.setPosition("Y", newY);
     // core.setPosition("Z", newZ);
     ////core.waitForDevice("X");
     // //core.waitForDevice("Y");
     // core.snapImage();
     // x = core.getPosition("X");
     // y = core.getPosition("Y");
     // z = core.getPosition("Z");

     // std::cout << "Position difference " << newX - x << ", " << newY - y << ", " << newZ - z << std::endl;

      core.unloadAllDevices();
   }
   catch (CMMError& err)
   {
      std::cout << "Exception caught. " << err.getMsg() << std::endl;
      std::cout << "Exiting now." << std::endl;
      return 1;
   }

	return 0;
}

