package modules.client;

import modules.ModulesGlobal;

public final class ClientGlobal {
    private ClientGlobal() {}

    public static int sumTwice(int n) {
        return ModulesGlobal.sum(n) * 2;
    }
}
