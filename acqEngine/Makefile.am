MMAcqEngine.jar:
	test -d classes || mkdir classes
	rm -rf classes/*
	cp -R src/org classes/
	java -cp $(CLASSEXT)/clojure.jar:$(IJJARPATH):../mmstudio/MMJ_.jar:./src -Dclojure.compile.path=classes -server clojure.lang.Compile org.micromanager.acq-engine
	jar cvf MMAcqEngine.jar -C classes/ .    

all: MMAcqEngine.jar

install:
	cp MMAcqEngine.jar $(DESTDIR)$(IJPATH)/plugins/Micro-Manager

clean:
	test -e MMAcqEngine.jar && rm MMAcqEngine.jar || echo "MMAcqEngine.jar not found"

