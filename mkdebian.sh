ROOT=../root
rm -Rf $ROOT
mkdir $ROOT

##### Meta information
mkdir $ROOT/DEBIAN
cp doc/copyright.txt $ROOT/DEBIAN/copyright
cp debiancontrol $ROOT/DEBIAN/control
echo "#!/bin/sh" > $ROOT/DEBIAN/postinst
chmod 0555 $ROOT/DEBIAN/postinst

##### Programs
mkdir $ROOT/usr/
mkdir $ROOT/usr/bin/
cp Test_Serial/testserial $ROOT/usr/bin/mm_testserial
cp ModuleTest/moduletest $ROOT/usr/bin/mm_moduletest
cp Test_MMCore/testCore $ROOT/usr/bin/mm_testcore
strip $ROOT/usr/bin/*

##### JAR-files
mkdir $ROOT/usr/share/
mkdir $ROOT/usr/share/java/
mkdir $ROOT/usr/share/java/micro-manager/
cp Bleach/MMBleach_.jar autofocus/MMAutofocus_.jar autofocus/MMAutofocusTB_.jar MMCoreJ_wrap/MMCoreJ.jar Tracking/Tracker_.jar \
mmstudio/MMJ_.jar mmstudio/MMReader_.jar $ROOT/usr/share/java/micro-manager/
#classext/bsh-2.0b4.jar classext/syntax.jar classext/ij.jar 

##### Core
mkdir $ROOT/usr/lib/
mkdir $ROOT/usr/lib/micro-manager/

cp MMCoreJ_wrap/.libs/libMMCoreJ_wrap.so $ROOT/usr/lib/micro-manager/

##### All plugins
for from in `ls DeviceAdapters/*/.libs/*.so`
	do
		to=${from##*/}
		to=$ROOT/usr/lib/micro-manager/libmmgr_dal_`echo $to|sed 's/\..\{2\}$//'`
		to=$to.so
		cp $from $to
	done
strip $ROOT/usr/lib/micro-manager/*

##### Make shared objects visible without hardcoding the path
mkdir $ROOT/etc/
mkdir $ROOT/etc/ld.so.conf.d/
echo "/usr/lib/micro-manager" > $ROOT/etc/ld.so.conf.d/micro-manager.conf
echo "/sbin/ldconfig" >> $ROOT/DEBIAN/postinst

##### Put together
cd ..
dpkg-deb -b root
mv root.deb micro-manager.deb

#use later. buggy now
#jpackage-utils


