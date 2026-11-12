package dev._2lstudios.chatsentinel.shared.modules;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GeneralModuleCommandTest {
    @Test
    public void isCommand_matchesConfiguredRootAndNamespacedForms() {
        GeneralModule module = new GeneralModule();
        module.loadData(false, false, false, Arrays.asList("/msg", "/reply", "/broadcast"));

        assertTrue(module.isCommand("/msg"));
        assertTrue(module.isCommand("/msg Steve hi"));
        assertTrue(module.isCommand("/MSG Steve hi"));
        assertTrue(module.isCommand("/essentials:msg Steve hi"));
        assertTrue(module.isCommand("/essentials:msg"));
        assertFalse(module.isCommand("/messagex hi"));
        assertFalse(module.isCommand("msg hi"));
        assertFalse(module.isCommand("/gamemode 1"));
    }

    @Test
    public void isCommand_matchesNamespacedConfiguredRoot() {
        GeneralModule module = new GeneralModule();
        module.loadData(false, false, false, Arrays.asList("/essentials:msg"));

        assertTrue(module.isCommand("/msg Steve hi"));
    }
}
