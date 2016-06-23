#ifndef _PPPARAM_H_
#define _PPPARAM_H_

#include <string>

/**
* Class used by post processing, a list of these elements is built up one for each post processing function
* so the call back function in CPropertyActionEx can get to information about that particular feature in
* the call back function
*/ 
class PpParam 
{
public:
    PpParam(std::string name = "", int ppIndex = -1, int propIndex = -1);

    std::string GetName();
    int         GetppIndex();
    int         GetpropIndex();
    int         GetRange();
    double      GetcurValue();
    void        SetName(std::string name);
    void        SetppIndex(int ppIndex);
    void        SetpropInex(int propIndex);
    void        SetcurValue(double curValue);
    void        SetRange(int range);

    void SetPostProc(PpParam& tmp);

protected:
    std::string mName;
    int         mppIndex;
    int         mpropIndex;
    double      mcurValue;
    int         mRange;

};

#endif