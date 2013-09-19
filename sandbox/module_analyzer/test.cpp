#include "PEAnalyzer.h"

#include <tchar.h>
#include <iostream>


int _tmain(int argc, _TCHAR* argv[])
{
   try {
      using namespace PEAnalyzer;

#ifdef _UNICODE
#error We're assuming ascii charset for this test.
#endif
      const char* filename = "mmgr_dal_DemoCamera.dll";
      if (argc >= 2) {
         filename = argv[1];
      }
      MappedImage::Ptr image = MappedImage::New(argv[1]);
      PEFile::Ptr pefile = PEFile::New(image);

      std::cout << "File " << (pefile->IsDLL() ? "is" : "is not") << " a DLL\n";

      boost::shared_ptr < std::vector<std::string> > imports(pefile->GetImportNames());
      for (std::vector<std::string>::const_iterator it = imports->begin(), end = imports->end(); it != end; ++it) {
         std::cout << *it << '\n';
      }
   }
   catch (const std::exception& e) {
      std::cerr << "Exception: " << e.what() << '\n';
      return 1;
   }

   return 0;
}