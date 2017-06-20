.DEFAULT_GOAL := jar

JAVA_CORE_SRC = ./src/co/samco/mend4/core/*
JAVA_ALL_SRC = ./src/co/samco/mend4/desktop/* ./src/co/samco/mend4/commands/* $(JAVA_CORE_SRC)
JAVA_LIB_ALL = "lib/*"
JAVA_LIB_IO = ./lib/commons-io-2.4.jar
JAVA_LIB_CODEC = ./lib/commons-codec-1.10.jar
BIN_DIR = bin
WIN_DIR = Windows
UNIX_DIR = Unix
DESKTOP_JAR_NAME = MEND4.jar
CORE_JAR_NAME = MEND4Core.jar
ANDROID_LIBS_DIR = ./AndroidProject/MEND4/app/libs/

jar: javac
	cd $(BIN_DIR); jar cvfe $(DESKTOP_JAR_NAME) co.samco.mend4.desktop.Main ./*
	cp $(BIN_DIR)/$(DESKTOP_JAR_NAME) $(WIN_DIR)
	cp $(BIN_DIR)/$(DESKTOP_JAR_NAME) $(UNIX_DIR)

javac: 
	rm -rf $(BIN_DIR)
	mkdir $(BIN_DIR)
	javac -d $(BIN_DIR) -cp $(JAVA_LIB_ALL) $(JAVA_ALL_SRC)
	cd $(BIN_DIR); jar xf ../$(JAVA_LIB_IO)
	cd $(BIN_DIR); jar xf ../$(JAVA_LIB_CODEC)
	
core: 
	rm -rf $(BIN_DIR)
	mkdir $(BIN_DIR)
	javac -d $(BIN_DIR) -cp $(JAVA_LIB_ALL) $(JAVA_CORE_SRC)
	cd $(BIN_DIR); jar cvf $(CORE_JAR_NAME) ./*

droidlibs: core
	mkdir -p $(ANDROID_LIBS_DIR)
	cp $(BIN_DIR)/$(CORE_JAR_NAME) $(ANDROID_LIBS_DIR)

clean: 
	rm -rf $(BIN_DIR)
	rm -f $(WIN_DIR)/$(DESKTOP_JAR_NAME)
	rm -f $(UNIX_DIR)/$(DESKTOP_JAR_NAME)

