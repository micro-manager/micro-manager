///////////////////////////////////////////////////////////////////////////////
// FILE:          Host.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     MMCore
//-----------------------------------------------------------------------------
// DESCRIPTION:   Multi-platform implementation of some simple network facilities
//              
// COPYRIGHT:     University of California, San Francisco, 2011,
//
// LICENSE:       This file is distributed under the "Lesser GPL" (LGPL) license.
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
// AUTHOR:        Karl Hoover  karl.hoover@gmail.com 2011


#include "Host.h"

#ifdef _WINDOWS
#include <winsock2.h>
#include "Iphlpapi.h"
#include <stdio.h>
#define snprintf _snprintf 

#endif //_WINDOWS

#ifdef __APPLE__

#include "AppleHost.h"

#endif //__APPLE__

#ifdef linux
#include <stdio.h>
#include <sys/ioctl.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <net/if.h>
#include <string.h>
#include <errno.h>
#endif // linux


Host::Host(void)
{
}

Host::~Host(void)
{
}



// a function to call into the OS stuff

std::vector<MACValue > Host::getMACAddresses(long& status)
{
   // so far no problem...
   status = 0;
   std::vector<MACValue> retval;

#ifdef _WINDOWS

   // Get the buffer length required for IP_ADAPTER_INFO.
   ULONG BufferLength = 0;
   BYTE* pBuffer = 0;
   long osstatus;
   osstatus = GetAdaptersInfo( 0, &BufferLength );
   if( ERROR_BUFFER_OVERFLOW == osstatus)
   {
      // Now the BufferLength contain the required buffer length.
      // Allocate necessary buffer.
      pBuffer = new BYTE[ BufferLength ];
   }
   else
   {
      status = (0==osstatus?-1:osstatus);
   }

   if( 0 == status)
   {
      // Get the Adapter Information.
      PIP_ADAPTER_INFO pAdapterInfo = reinterpret_cast<PIP_ADAPTER_INFO>(pBuffer);
      GetAdaptersInfo( pAdapterInfo, &BufferLength );

      // Iterate the network adapters and print their MAC address.
      while( pAdapterInfo )
      {
         MACValue v; // it's really a long long
         // Assuming pAdapterInfo->AddressLength is 6.
         memcpy(&v, pAdapterInfo->Address, 6);
         retval.push_back(v);

         // Get next adapter info.
         pAdapterInfo = pAdapterInfo->Next;
      }

      // deallocate the buffer.
      delete[] pBuffer;
   }

#endif //_WINDOWS


#ifdef __APPLE__

   io_iterator_t   intfIterator;
   UInt8           MACAddress[kIOEthernetAddressSize];

   int kernResult = FindEthernetInterfaces(&intfIterator);

   if (KERN_SUCCESS != kernResult) {
      status = (long)(0==kernResult?-1:kernResult);
   }
   else {
      kernResult = GetMACAddress(intfIterator, MACAddress, sizeof(MACAddress));

      if (KERN_SUCCESS != kernResult) {
         status = (long)(0==kernResult?-1:kernResult);
      }
      else {
         MACValue v;
         memcpy(&v, MACAddress, sizeof(v));


         retval.push_back(v);
      }
   }

   (void) IOObjectRelease(intfIterator);   // Release the iterator.
   //#endif  //APPLEHOSTIMPL



#endif // __APPLE__

#ifdef linux

   //not tested!
   // assumes the device eth0 is the primary ethernet card
   extern int errno;

   int sock;
   struct ifreq buffer;
   sock = socket(PF_INET, SOCK_DGRAM, 0);
   memset(&buffer, 0, sizeof(buffer));
   strcpy(buffer.ifr_name, "eth0");
   // this could return an error
   int osstatus = ioctl(sock, SIOCGIFHWADDR, &buffer);
   if ( -1 == osstatus)
      status = (long)errno;
   close(sock);
   if( 0 == status)
   {
      MACValue v = 0;
      memcpy(&v, buffer.ifr_hwaddr.sa_data, 6);
      retval.push_back(v);
   }


#endif // linux



   return retval;
}



std::vector<std::string> Host::MACAddresses(long& status)
{

   std::vector<std::string> retval;

   std::vector<MACValue> values = getMACAddresses(status);

   if( 0 == status)
   {

      std::vector<MACValue>::iterator j;
      for( j = values.begin(); j!=values.end(); ++j)
      {
         unsigned char ctmp[6];
         memcpy( ctmp, &(*j), 6);

         char address[19];
         snprintf(address, 19, "%02x-%02x-%02x-%02x-%02x-%02x",
            ctmp[0],
            ctmp[1],
            ctmp[2],
            ctmp[3],
            ctmp[4],
            ctmp[5]);
         retval.push_back(std::string(address));
      }
   }

   return retval;

}
