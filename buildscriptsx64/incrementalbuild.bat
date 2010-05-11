\bin\pskill javaw.exe
CALL buildscripts\updatemmfromsvn.bat
CALL buildscriptsx64\MMINCREMENTALBUILD.BAT withpython
pscp -i c:\projects\MM.ppk -batch -r /projects/micromanager/Install/Output/MMSetup_%mmversion%_%YYYYMMDD%.exe MM@valelab.ucsf.edu:./public_html/nightlyBuilds/1.4/Windows/MMSetup_%mmversion%_%YYYYMMDD%.exe

