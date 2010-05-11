\bin\pskill javaw.exe
CALL buildscripts\updatemmfromsvn.bat
CALL buildscripts\MMINCREMENTALBUILD.BAT withpython
pscp -i c:\projects\MM.ppk -batch -r /projects/micromanager/Install32/Output/MMSetupx86_%mmversion%_%YYYYMMDD%.exe MM@valelab.ucsf.edu:./public_html/nightlyBuilds/1.4/Windows/MMSetupx86_%mmversion%_%YYYYMMDD%.exe

