devenv /REBUILD Release .\MMCorePy_wrap\MMCorePy_wrap.sln
copy .\bin\MMCorePy.py .\Install\micro-manager
copy .\bin\_MMCorePy.pyd .\Install\micro-manager
copy .\MMCorePy_wrap\MMCoreWrapDemo.py .\Install\micro-manager