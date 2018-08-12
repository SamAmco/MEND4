package co.samco.mend4.desktop.helper;

import javax.inject.Inject;

public class VersionHelper {

    @Inject
    public VersionHelper() {}

    public String getVersion() {
        return getClass().getPackage().getImplementationVersion();
    }
}
