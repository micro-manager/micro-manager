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
      MappedFile::Ptr image = MappedFile::New(filename);
      PEFile::Ptr pefile = PEFile::New(image);

      std::cout << "File " << (pefile->IsDLL() ? "is" : "is not") << " a DLL\n";

      std::string machine = "Unknown";
      if (pefile->IsMachine_x86()) {
         machine = "x86";
      }
      else if (pefile->IsMachine_x64()) {
         machine = "x64";
      }
      std::cout << "Machine: " << machine << '\n';

      std::cout << "Dependencies:\n";
      boost::shared_ptr< std::vector<std::string> > imports(pefile->GetImportNames());
      for (std::vector<std::string>::const_iterator it = imports->begin(), end = imports->end(); it != end; ++it) {
         std::cout << *it << '\n';
      }

      std::cout << "Sections:\n";
      boost::shared_ptr< std::vector<std::string> > sections(pefile->GetSectionNames());
      for (std::vector<std::string>::const_iterator it = sections->begin(), end = sections->end(); it != end; ++it) {
         std::cout << *it << ": ";
         std::pair<boost::shared_ptr<void>, size_t> section(pefile->GetSectionByName(*it));
         std::cout << section.second << " bytes\n";
      }
   }
   catch (const std::exception& e) {
      std::cerr << "Exception: " << e.what() << '\n';
      return 1;
   }

   return 0;
}
