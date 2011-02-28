#ifndef HOST_H
#define HOST_H
#include <string>
#include <vector>
class Host
{
public:
   Host(void);
   ~Host(void);
   static std::vector<std::string> MACAddresses();
};

#endif