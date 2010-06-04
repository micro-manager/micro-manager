\bin\pskill javaw.exe
CALL buildscripts\updateallfromsvn.bat
CALL buildscripts\MMBUILD.BAT withpython
pscp -i c:\projects\MM.ppk -batch /projects/micromanager/Install_Win32/Output/MMSetupx86_%mmversion%_%YYYYMMDD%.exe MM@valelab.ucsf.edu:./public_html/nightlyBuilds/1.4/Windows/MMSetupx86_%mmversion%_%YYYYMMDD%.exe
