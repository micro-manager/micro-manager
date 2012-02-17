//---------------------------------------------------------------------------

#ifndef lineparserH
#define lineparserH

#include "andorvartypes.h"

class TLineParser
{
public:
  TLineParser(andoru32 _u32_stride, andoru32 _u32_lineCount);
  ~TLineParser();

  unsigned char* GetFirstLine(unsigned char* _p_basePointer, andoru32 _u32_imageSizeBytes);
  unsigned char* GetNextLine();

private:
  unsigned char * mp_linePointer;
  andoru32 mu32_stride;
  andoru32 mu32_lineCount;
  andoru32 mu32_currentLine;

  void reset(unsigned char* _p_basePointer);
  bool noMoreLines();
  bool validImageSizeBytes(andoru32 _u32_imageSizeBytes);
};
//---------------------------------------------------------------------------
#endif
