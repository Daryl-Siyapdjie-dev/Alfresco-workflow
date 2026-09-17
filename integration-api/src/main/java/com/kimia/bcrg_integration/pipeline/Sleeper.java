package com.kimia.bcrg_integration.pipeline;

/**
 * Petite abstraction de mise en pause, injectée dans le rejeu pour le rendre testable : en test on
 * fournit un {@code Sleeper} sans attente réelle. En production, {@code Thread::sleep}.
 */
@FunctionalInterface
public interface Sleeper {

    void sleep(long millis) throws InterruptedException;
}
