#include "Host.h"
typedef long long MACValue;

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
#endif // linux


Host::Host(void)
{
}

Host::~Host(void)
{
}




// a free function to call into the OS stuff

std::vector<MACValue > getMACAddresses(void)
{


    std::vector<MACValue> retval;

#ifdef _WINDOWS

// Get the buffer length required for IP_ADAPTER_INFO.
ULONG BufferLength = 0;
BYTE* pBuffer = 0;
if( ERROR_BUFFER_OVERFLOW == GetAdaptersInfo( 0, &BufferLength ))
{
    // Now the BufferLength contain the required buffer length.
    // Allocate necessary buffer.
    pBuffer = new BYTE[ BufferLength ];
}
else
{
    // Error occurred. handle it accordingly.
}

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

#endif //_WINDOWS



#ifdef __APPLE__


#ifdef APPLEHOSTIMPL  // no way all this works just yet.

    io_iterator_t   intfIterator;
    UInt8           MACAddress[kIOEthernetAddressSize];
 
    kernResult = FindEthernetInterfaces(&intfIterator);
    
    if (KERN_SUCCESS != kernResult) {
        printf("FindEthernetInterfaces returned 0x%08x\n", kernResult);
    }
    else {
        kernResult = GetMACAddress(intfIterator, MACAddress, sizeof(MACAddress));
        
        if (KERN_SUCCESS != kernResult) {
            printf("GetMACAddress returned 0x%08x\n", kernResult);
        }
        else {
           MACValue v;
           memcpy(v, MACAddress, sizeof(v));


            //printf("This system's built-in MAC address is %02x:%02x:%02x:%02x:%02x:%02x.\n",
            //        MACAddress[0], MACAddress[1], MACAddress[2], MACAddress[3], MACAddress[4], MACAddress[5]);
        retval.push_back(v);
        }
    }
    
    (void) IOObjectRelease(intfIterator);   // Release the iterator.
#else  // APPLEHOSTIMPL
retval.push_back(0);
#endif  //APPLEHOSTIMPL



#endif // __APPLE__

#ifdef linux

//not tested!
// assumes the device eth0 is the primary ethernet card


	int sock;
	struct ifreq buffer;
	sock = socket(PF_INET, SOCK_DGRAM, 0);
	memset(&buffer, 0, sizeof(buffer));
	strcpy(buffer.ifr_name, "eth0");
	ioctl(sock, SIOCGIFHWADDR, &buffer);
	close(sock);
   MACValue v = 0;
   memcpy(&v, buffer.ifr_hwaddr.sa_data, 6);
	retval.push_back(v);


#endif // linux



return retval;
}



std::vector<std::string> Host::MACAddresses()
{

   std::vector<std::string> retval;

   std::vector<MACValue> values = getMACAddresses();

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


return retval;

}
