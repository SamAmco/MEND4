package co.samco.mend4.desktop.helper;

import javax.inject.Inject;

public class VersionHelper {

    @Inject
    public VersionHelper() {}

    public String getVersion() {
        //TODO make sure the version number is actually correct
        return getClass().getPackage().getImplementationVersion();
    }
}
