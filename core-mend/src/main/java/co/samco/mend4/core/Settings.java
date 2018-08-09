package co.samco.mend4.core;

import co.samco.mend4.core.exception.CorruptSettingsException;

import java.io.IOException;

public interface Settings {
    void setValue(Name name, String value) throws IOException;

    boolean valueSet(Name name) throws IOException;

    String getValue(Name name) throws IOException, CorruptSettingsException;

    enum Name {
        PUBLICKEY("publickey"),
        PRIVATEKEY("privatekey"),
        CURRENTLOG("currentlog"),
        LOGDIR("logdir"),
        ENCDIR("encdir"),
        DECDIR("decdir"),
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
}
