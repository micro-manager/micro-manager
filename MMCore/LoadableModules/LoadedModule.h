#pragma once

#include <boost/shared_ptr.hpp>
#include <boost/utility.hpp>


class LoadedModuleImpl;

class LoadedModule /* final */ : boost::noncopyable
{
public:
   explicit LoadedModule(const std::string& filename);
   ~LoadedModule();

   void Unload(); // For developer use only
   void* GetFunction(const char* funcName);

private:
   LoadedModuleImpl* pImpl_;
   const std::string filename_;
};
