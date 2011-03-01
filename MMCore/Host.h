#ifndef HOST_H
#define HOST_H
#include <string>
#include <vector>

typedef long long MACValue;


class Host
{
public:
   Host(void);
   ~Host(void);
   std::vector<std::string> MACAddresses(void);
   std::vector<MACValue > getMACAddresses(void);


};

#endif