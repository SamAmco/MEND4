package co.samco.mend4.core;

import javax.xml.transform.TransformerException;

public interface Settings {
    String getPlatformDependentHeader();

    void setValue(Name name, String value) throws TransformerException,
            CorruptSettingsException, InvalidSettingNameException;

    String getValue(Name name) throws CorruptSettingsException, InvalidSettingNameException;

    enum Name {
        PUBLICKEY("publickey"),
        PRIVATEKEY("privatekey"),
        CURRENTLOG("currentlog"),
        LOGDIR("logdir"),
        ENCDIR("encdir"),
        DECDIR("decdir"),
        PASSCHECK("passcheck"),
        RSAKEYSIZE("rsakeysize"),
        AESKEYSIZE("aeskeysize"),
        PREFERREDRSA("preferredrsa"),
        PREFERREDAES("preferredaes"),
        SHREDCOMMAND("shredcommand");

        private final String name;

        Name(String name) {this.name = name;}

        @Override
        public String toString() {
            return name;
        }
    }

    class UnInitializedSettingsException extends Exception {
        private static final long serialVersionUID = -1209609585057442380L;

        public UnInitializedSettingsException(String message) {
            super(message);
        }
    }

    class CorruptSettingsException extends Exception {
        private static final long serialVersionUID = -7872915002684524393L;

        public CorruptSettingsException(String message) {
            super(message);
        }
    }

    class InvalidSettingNameException extends Exception {
        private static final long serialVersionUID = -396660409805269958L;

        public InvalidSettingNameException(String message) {
            super(message);
        }
    }

}
