ZippyFocus.jar: \
	test -d classes || mkdir classes
	rm -rf classes/*
	cp -R src/org classes/
	java -server -cp src:../../mmstudio/MMJ_.jar:../../../3rdpartypublic/classext/clojure.jar:../../acqEngine/MMAcqEngine.jar:../../MMCoreJ_wrap/MMCoreJ.jar:../../../3rdpartypublic/classext/* -Djava.library.path=. -Djava.awt.headless=true -Dclojure.compile.path=classes -server clojure.lang.Compile zippy-focus.core	
	jar cvf ZippyFocus.jar -C classes/ .    

all: ../mmstudio ZippyFocus.jar

install:
	cp ZippyFocus.jar $(DESTDIR)$(IJPATH)/mmautofocus

clean:
	test -e ZippyFocus.jar && rm ZippyFocus.jar || echo "ZippyFocus.jar not found"

