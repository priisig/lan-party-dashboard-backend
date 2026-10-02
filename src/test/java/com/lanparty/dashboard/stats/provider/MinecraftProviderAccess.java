package com.lanparty.dashboard.stats.provider;

/** Test access to package-private parsing helpers. */
public final class MinecraftProviderAccess {

    private MinecraftProviderAccess() {
    }

    public static Double firstNumber(String text) {
        return MinecraftProvider.firstNumber(text);
    }
}
