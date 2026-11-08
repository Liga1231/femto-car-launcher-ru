package io.github.seijikohara.femto.data.navigation;

import com.valhalla.valhalla.ValhallaKotlin;

public class ValhallaBridge {
    private final ValhallaKotlin nativeBridge = new ValhallaKotlin();

    public long createActor(String configPath) {
        return nativeBridge.createActor(configPath);
    }

    public void deleteActor(long handle) {
        nativeBridge.deleteActor(handle);
    }

    public String route(long handle, String request) {
        return nativeBridge.route(handle, request);
    }
}
