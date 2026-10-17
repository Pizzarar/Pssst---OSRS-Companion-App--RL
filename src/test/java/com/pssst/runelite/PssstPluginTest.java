package com.pssst.runelite;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class PssstPluginTest {
    public static void main(String[] args) throws Exception {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");
        ExternalPluginManager.loadBuiltin(CompanionPlugin.class);
        System.setProperty("runelite.plugin", "com.pssst.runelite.CompanionPlugin");
        RuneLite.main(args);
    }
}
