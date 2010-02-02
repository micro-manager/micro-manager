#pragma once
// format system time like: yyyyMMddhhmmss  (LDAP, Semi, etc.)

#include <windows.h>
#include <iostream> //Stream I/O. cout and cin, istream and ostream, and endl, fixed, and showpoint manipulators. 
#include <iomanip> //More I/O manipulaters: eg, setw(w) and setprecision(p). 
#include <fstream> //File I/O. ifstream and ofstream. 
#include <sstream> //I/O to and from strings. istringstream and ostringstream. 

class yyyyMMddhhmmss
{
public:
	std::string ToString()
	{
		using namespace std;
	   ostringstream sout; 
		sout.fill('0'); 
		sout.width(4);
		sout<<st_.wYear;
		sout << setw(2) << st_.wMonth << setw(2) << st_.wDay << setw(2) << st_.wHour << setw(2) << st_.wMinute << setw(2) << st_.wSecond << setw(1) << ".";
		sout.width(3);
		sout << st_.wMilliseconds;
		return sout.str();
	};
	yyyyMMddhhmmss(void){GetSystemTime(&st_);};
	~yyyyMMddhhmmss(void){};
private:
	SYSTEMTIME st_;

};
