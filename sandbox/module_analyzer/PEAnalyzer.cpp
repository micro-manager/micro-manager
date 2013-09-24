//
// PEAnalyzer - read Windows DLLs (and EXEs).
//

// Disable warning C4996
#define _SCL_SECURE_NO_WARNINGS

#include "PEAnalyzer.h"

#include <algorithm>
#include <string>
#include <vector>

#include <boost/lexical_cast.hpp>
#include <boost/shared_ptr.hpp>

// Note: Data structures for PE files are defined in winnt.h

#pragma warning(disable: 4800) // Performance warning, casting to bool


namespace PEAnalyzer {

//
// MappedFile implementation
//

MappedFile::Ptr
MappedFile::New(const std::string filename)
{
   HANDLE hFile = CreateFileA(filename.c_str(), GENERIC_READ, FILE_SHARE_READ, NULL,
         OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, 0);
   if (hFile == INVALID_HANDLE_VALUE) {
      throw ModuleLoadFailedException("Cannot open file: " + filename);
   }

   HANDLE hFileMapping = CreateFileMapping(hFile, NULL, PAGE_READONLY, 0, 0, NULL);
   if (!hFileMapping) {
      CloseHandle(hFile);
      throw ModuleLoadFailedException("Cannot map file: " + filename);
   }

   void* baseAddr = MapViewOfFile(hFileMapping, FILE_MAP_READ, 0, 0, 0);
   if (!baseAddr) {
      CloseHandle(hFileMapping);
      CloseHandle(hFile);
      throw ModuleLoadFailedException("Cannot view file mapping: " + filename);
   }

   return Ptr(new Self(hFile, hFileMapping, baseAddr));
}


MappedFile::MappedFile(HANDLE hFile, HANDLE hFileMapping, void* baseAddress) :
   hFile_(hFile),
   hFileMapping_(hFileMapping),
   baseAddress_(baseAddress)
{
}


MappedFile::~MappedFile()
{
   UnmapViewOfFile(baseAddress_);
   CloseHandle(hFileMapping_);
   CloseHandle(hFile_);
}


void*
MappedFile::GetBaseAddress()
{
   return baseAddress_;
}


//
// PEFile, PEFile32, PEFile64 implementation
//

PEFile::Ptr
PEFile::New(MappedFile::Ptr image)
{
   Self headers(image);

   switch (headers.GetNTOptionalHeaderMagic()) {
      case IMAGE_NT_OPTIONAL_HDR32_MAGIC:
         return PEFile32::New(image);
      case IMAGE_NT_OPTIONAL_HDR64_MAGIC:
         return PEFile64::New(image);
   }
   throw PEFormatException("Unknown NT optional header magic number");
}


PEFile32::Ptr
PEFile32::New(MappedFile::Ptr image)
{
   return Ptr(new PEFile32(image));
}


PEFile64::Ptr
PEFile64::New(MappedFile::Ptr image)
{
   return Ptr(new PEFile64(image));
}


PEFile::PEFile(MappedFile::Ptr image) : image_(image)
{
   CheckMagic();
}


PEFile32::PEFile32(MappedFile::Ptr image) : PEFile(image)
{
   if (GetNTOptionalHeaderMagic() != IMAGE_NT_OPTIONAL_HDR32_MAGIC) {
      throw PEFormatException("Programming error: not a 32-bit PE file");
   }

   WORD machine = GetFileHeader()->Machine;
   if (machine != IMAGE_FILE_MACHINE_I386) {
      throw PEFormatException("PE is 32-bit but machine is not i386 (Machine = " +
            boost::lexical_cast<std::string>(machine) + ")");
   }
}


PEFile64::PEFile64(MappedFile::Ptr image) : PEFile(image)
{
   if (GetNTOptionalHeaderMagic() != IMAGE_NT_OPTIONAL_HDR64_MAGIC) {
      throw PEFormatException("Programming error: not a 64-bit PE file");
   }

   WORD machine = GetFileHeader()->Machine;
   if (machine != IMAGE_FILE_MACHINE_AMD64) {
      throw PEFormatException("PE is 32-bit but machine is not amd64 (Machine = " +
            boost::lexical_cast<std::string>(machine) + ")");
   }
}


IMAGE_SECTION_HEADER*
PEFile::SectionBegin()
{
   return IMAGE_FIRST_SECTION(GetNTHeaders());
}


IMAGE_SECTION_HEADER*
PEFile::SectionEnd()
{
   IMAGE_SECTION_HEADER* begin = SectionBegin();
   return begin + GetFileHeader()->NumberOfSections;
}


IMAGE_SECTION_HEADER*
PEFile::GetSectionContainingRVA(DWORD relativeVirtualAddress)
{
   for (PIMAGE_SECTION_HEADER section = SectionBegin(), end = SectionEnd();
         section != end; ++section) {
      DWORD section_start = section->VirtualAddress;
      DWORD section_stop = section_start + section->Misc.VirtualSize;

      if (relativeVirtualAddress >= section_start &&
            relativeVirtualAddress < section_stop) {
         if (relativeVirtualAddress - section_start > section->SizeOfRawData) {
            // The whole section is not stored in the file, and the requested
            // RVA is in the zero-padded region
            throw DataConsistencyException("Relative virtual address not contained in file");
         }
         return section;
      }
   }

   throw DataConsistencyException("Relative virtual address not contained in any section");
}


void*
PEFile::GetAddressForOffset(DWORD fileOffset)
{
   return reinterpret_cast<char*>(image_->GetBaseAddress()) + fileOffset;
}


void*
PEFile::GetAddressForRVA(DWORD relativeVirtualAddress)
{
   // When the PE is loaded for execution, sections are moved in memory.
   // The RVAs read from the file refer to this in-memory location, not the
   // offset within the file.
   IMAGE_SECTION_HEADER* section = GetSectionContainingRVA(relativeVirtualAddress);
   DWORD addressRelativeToSection = relativeVirtualAddress - section->VirtualAddress;
   DWORD offsetInFile = section->PointerToRawData + addressRelativeToSection;
   return GetAddressForOffset(offsetInFile);
}


IMAGE_DOS_HEADER*
PEFile::GetDOSHeader()
{
   return reinterpret_cast<IMAGE_DOS_HEADER*>(GetAddressForOffset(0));
}


IMAGE_NT_HEADERS*
PEFile::GetNTHeaders()
{
   LONG ntHeadersOffset = GetDOSHeader()->e_lfanew;
   return reinterpret_cast<IMAGE_NT_HEADERS*>(GetAddressForOffset(ntHeadersOffset));
}


IMAGE_FILE_HEADER*
PEFile::GetFileHeader()
{
   return &GetNTHeaders()->FileHeader;
}


WORD
PEFile::GetNTOptionalHeaderMagic()
{
   // The Magic field is at the beginning of the optional header, whether it be
   // IMAGE_OPTIONAL_HEADER32 or IMAGE_OPTIONAL_HEADER_64
   return *reinterpret_cast<WORD*>(&GetNTHeaders()->OptionalHeader);
}


bool
PEFile::IsDLL()
{
   return GetFileHeader()->Characteristics & IMAGE_FILE_DLL;
}


void
PEFile::CheckMagic()
{
   if (GetDOSHeader()->e_magic != IMAGE_DOS_SIGNATURE) {
      throw PEFormatException("Incorrect DOS header signature (found " +
            boost::lexical_cast<std::string>(GetDOSHeader()->e_magic) + ")");
   }

   if (GetNTHeaders()->Signature != IMAGE_NT_SIGNATURE) {
      throw PEFormatException("Not a Windows NT PE file (found signature " +
            boost::lexical_cast<std::string>(GetNTHeaders()->Signature) + ")");
   }
}


IMAGE_OPTIONAL_HEADER32*
PEFile32::GetOptionalHeader()
{
   return &reinterpret_cast<IMAGE_NT_HEADERS32*>(GetNTHeaders())->OptionalHeader;
}


IMAGE_OPTIONAL_HEADER64*
PEFile64::GetOptionalHeader()
{
   return &reinterpret_cast<IMAGE_NT_HEADERS64*>(GetNTHeaders())->OptionalHeader;
}


IMAGE_DATA_DIRECTORY*
PEFile::GetDataDirectory(size_t index)
{
   // We need an implementation to allow instantiating PEFile; it is private,
   // so shouldn't be called.
   throw Exception("Programming error: cannot get data directory from PEFile instance");
}


IMAGE_DATA_DIRECTORY*
PEFile32::GetDataDirectory(size_t index)
{
   return &GetOptionalHeader()->DataDirectory[index];
}


IMAGE_DATA_DIRECTORY*
PEFile64::GetDataDirectory(size_t index)
{
   return &GetOptionalHeader()->DataDirectory[index];
}


IMAGE_IMPORT_DESCRIPTOR*
PEFile::GetImportDescriptors()
{
   IMAGE_DATA_DIRECTORY* dir = GetDataDirectory(IMAGE_DIRECTORY_ENTRY_IMPORT);
   return reinterpret_cast<IMAGE_IMPORT_DESCRIPTOR*>(GetAddressForRVA(dir->VirtualAddress));
}


boost::shared_ptr< std::vector<std::string> >
PEFile::GetImportNames()
{
   boost::shared_ptr< std::vector<std::string> > names(new std::vector<std::string>());

   // The image import descriptor array is null-terminated (Characteristics is
   // 0 in sentinel element)
   for (IMAGE_IMPORT_DESCRIPTOR* desc = GetImportDescriptors(); desc->Characteristics; ++desc) {
      char* name = reinterpret_cast<char*>(GetAddressForRVA(desc->Name));
      names->push_back(name);
   }
   return names;
}


static std::string
SectionName(IMAGE_SECTION_HEADER* section)
{
   char sectionName[9];
   std::copy(&section->Name[0], &section->Name[8], sectionName);
   sectionName[8] = '\0';
   return sectionName;
}


boost::shared_ptr< std::vector<std::string> >
PEFile::GetSectionNames()
{
   boost::shared_ptr< std::vector<std::string> > names(new std::vector<std::string>());
   for (PIMAGE_SECTION_HEADER section = SectionBegin(), end = SectionEnd();
         section != end; ++section) {
      names->push_back(SectionName(section));
   }
   return names;
}


std::pair<boost::shared_ptr<void>, size_t>
PEFile::GetSectionByName(const std::string& name)
{
   for (PIMAGE_SECTION_HEADER section = SectionBegin(), end = SectionEnd();
         section != end; ++section) {
      if (SectionName(section) == name) {
         size_t size = section->Misc.VirtualSize;
         size_t nonzeroSize = section->SizeOfRawData;
         if (nonzeroSize > size) {
            // Truncate any padding in the file
            nonzeroSize = size;
         }

         struct Deleter
         {
            void operator()(void* buffer)
            { delete[] reinterpret_cast<char*>(buffer); }
         } deleter;
         boost::shared_ptr<void> contents(new char[size], deleter);

         // Copy the section, and zero-fill any region beyond what is stored in
         // the file.

         char* srcBegin = reinterpret_cast<char*>(GetAddressForOffset(section->PointerToRawData));
         char* srcNonzeroEnd = srcBegin + nonzeroSize;

         char* dstBegin = reinterpret_cast<char*>(contents.get());
         char* dstZeroStart = std::copy(srcBegin, srcNonzeroEnd, dstBegin);

         char* dstEnd = dstBegin + size;
         std::fill(dstZeroStart, dstEnd, 0);

         return std::make_pair(contents, size);
      }
   }

   throw DataConsistencyException("No section named \"" + name + "\"");
}

} // namespace PEAnalyzer
