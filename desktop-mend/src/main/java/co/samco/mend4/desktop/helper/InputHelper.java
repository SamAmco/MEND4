package co.samco.mend4.desktop.helper;

import co.samco.mend4.desktop.input.InputListener;
import co.samco.mend4.desktop.input.InputProvider;
import co.samco.mend4.desktop.input.impl.MendInputBox;

import javax.inject.Inject;

public class InputHelper {

    @Inject
    public InputHelper() { }

    public InputProvider createInputProviderAndRegisterListener(InputListener inputListener) {
        //TODO we should allow users to use different text editors at some point
        //TODO we should allow users to configure these parameters
        MendInputBox mendInputBox = new MendInputBox(true, false, 800, 250);
        mendInputBox.addListener(inputListener);
        mendInputBox.setVisible(true);
        return mendInputBox;
    }
}
