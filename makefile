.DEFAULT_GOAL := jar

JAVA_SRC = ./src/co/samco/mend/* ./src/co/samco/commands/*
JAVA_LIB = "lib/*"
BIN_DIR = bin
WIN_DIR = Windows
UNIX_DIR = Unix
JAR_NAME = MEND4.jar

jar: javac
	cd $(BIN_DIR); jar cvfe $(JAR_NAME) co.samco.mend.Main ./*
	cp $(BIN_DIR)/$(JAR_NAME) $(WIN_DIR)
	cp $(BIN_DIR)/$(JAR_NAME) $(UNIX_DIR)

javac: 
	mkdir -p $(BIN_DIR)
	javac -d $(BIN_DIR) -cp $(JAVA_LIB) $(JAVA_SRC)

clean: 
	rm -rf $(BIN_DIR)
	rm -f $(WIN_DIR)/$(JAR_NAME)
	rm -f $(UNIX_DIR)/$(JAR_NAME)

