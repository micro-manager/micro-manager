devenv /BUILD Release .\MMCorePy_wrap\MMCorePy_wrap.sln
copy .\bin32\MMCorePy.py .\Install32\micro-manager
copy .\bin32\_MMCorePy.pyd .\Install32\micro-manager
copy .\MMCorePy_wrap\MMCoreWrapDemo.py .\Install32\micro-manager