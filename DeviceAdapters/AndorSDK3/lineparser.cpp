#include "lineparser.h"
#include <cstdlib>

TLineParser::TLineParser(andoru32 _u32_stride, andoru32 _u32_lineCount)
:mu32_stride(_u32_stride), mu32_lineCount(_u32_lineCount)
{
  reset(NULL);
}

TLineParser::~TLineParser()
{

}

unsigned char* TLineParser::GetFirstLine(unsigned char* _p_basePointer, andoru32 _u32_imageSizeBytes)
{
  if (validImageSizeBytes(_u32_imageSizeBytes)) {
    reset(_p_basePointer);
  }
  else {
    reset(NULL);
  }

  return mp_linePointer;
}

unsigned char* TLineParser::GetNextLine()
{
  mu32_currentLine++;

  if (noMoreLines()) {
    reset(NULL);
  }
  else {
    mp_linePointer += mu32_stride;
  }

  return mp_linePointer;
}

void TLineParser::reset(unsigned char* _p_basePointer)
{
  mu32_currentLine = 0;
  mp_linePointer = _p_basePointer;
}

bool TLineParser::noMoreLines()
{
  return (mu32_currentLine >= mu32_lineCount) || (mp_linePointer==NULL);
}

bool TLineParser::validImageSizeBytes(andoru32 _u32_imageSizeBytes)
{
  return (_u32_imageSizeBytes >= mu32_stride * mu32_lineCount);
}
