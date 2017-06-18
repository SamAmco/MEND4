.DEFAULT_GOAL := jar

JAVA_SRC = ./src/co/samco/mend4/desktop/* ./src/co/samco/mend4/commands/* ./src/co/samco/mend4/core/*
JAVA_LIB_ALL = "lib/*"
JAVA_LIB_IO = ./lib/commons-io-2.4.jar
JAVA_LIB_CODEC = ./lib/commons-codec-1.10.jar
BIN_DIR = bin
WIN_DIR = Windows
UNIX_DIR = Unix
JAR_NAME = MEND4.jar

jar: javac
	cd $(BIN_DIR); jar cvfe $(JAR_NAME) co.samco.mend4.desktop.Main ./*
	cp $(BIN_DIR)/$(JAR_NAME) $(WIN_DIR)
	cp $(BIN_DIR)/$(JAR_NAME) $(UNIX_DIR)

javac: 
	mkdir -p $(BIN_DIR)
	javac -d $(BIN_DIR) -cp $(JAVA_LIB_ALL) $(JAVA_SRC)
	cd $(BIN_DIR); jar xf ../$(JAVA_LIB_IO)
	cd $(BIN_DIR); jar xf ../$(JAVA_LIB_CODEC)
	

clean: 
	rm -rf $(BIN_DIR)
	rm -f $(WIN_DIR)/$(JAR_NAME)
	rm -f $(UNIX_DIR)/$(JAR_NAME)

