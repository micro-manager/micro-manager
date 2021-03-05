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
#endif //_WINDOWS

#include "../MMDevice/FixSnprintf.h"

#ifdef __APPLE__

#include "AppleHost.h"

#endif //__APPLE__

#ifdef __linux__
#include <stdio.h>
#include <sys/ioctl.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <net/if.h>
#include <string.h>
#include <errno.h>
#include <unistd.h>
#endif // __linux__


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

#ifdef __linux__

   int sock;
   sock = socket(PF_INET, SOCK_DGRAM, 0);
   if ( -1 == sock ) {
      status = static_cast<long>(errno);
   }
   else {
      char buf[2048];
      ifconf ifc;
      ifc.ifc_buf = buf;
      ifc.ifc_len = sizeof(buf);
      int osstatus = ioctl(sock, SIOCGIFCONF, &ifc);
      if ( -1 == osstatus ) {
         status = static_cast<long>(errno);
      }
      else {
         MACValue macaddr;
         for ( ifreq *ifr(ifc.ifc_req), *ifrEnd(ifc.ifc_req + ifc.ifc_len / sizeof(ifreq));
               ifr != ifrEnd;
               ++ifr )
         {
            osstatus = ioctl(sock, SIOCGIFHWADDR, ifr);
            if ( osstatus == 0 ) {
               macaddr = 0;
               memcpy(&macaddr, ifr->ifr_hwaddr.sa_data, 6);
               if(macaddr != 0) {
                  retval.push_back(macaddr);
               }
            }
         }
         // Raise error flag if no mac address could be found for *any* interface.  The loopback and some tunnel
         // interfaces, for example, may have an IP address but only one peer and therefore no need for a media layer
         // address.  So, some mac addr retrieval failures and null mac addrs are to be expected.
         if ( retval.empty() ) {
            status = static_cast<long>(errno);
         }
      }
      close(sock);
   }


#endif // __linux__



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
