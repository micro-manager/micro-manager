\bin\pskill javaw.exe
CALL buildscriptsx64\updateallfromsvn.bat
CALL buildscriptsx64\MMBUILD.BAT withpython
pscp -i c:\projects\MM.ppk -batch /projects/micromanager/Install64/Output/MMSetupx64_%mmversion%_%YYYYMMDD%.exe MM@valelab.ucsf.edu:./public_html/nightlyBuilds/1.4/Windows/MMSetupx64_%mmversion%_%YYYYMMDD%.exe
