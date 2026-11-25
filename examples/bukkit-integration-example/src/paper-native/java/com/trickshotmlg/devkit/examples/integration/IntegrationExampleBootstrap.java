package com.trickshotmlg.devkit.examples.integration;

import com.trickshotmlg.devkit.paper.lifecycle.PaperBootstrapLifecycle;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;

/** Bootstrap-only sample. It registers no runtime services and retains no bootstrap context. */
public final class IntegrationExampleBootstrap implements PluginBootstrap {
    private final PaperBootstrapLifecycle lifecycle = new PaperBootstrapLifecycle();

    @Override
    public void bootstrap(BootstrapContext context) {
        var logger = context.getLogger();
        logger.info("[MDK-PAPER-NATIVE] bootstrap invoked");
        lifecycle.registerDatapackDiscovery(context, registrar -> logger.info(
                "[MDK-PAPER-NATIVE] datapack discovery callback invoked; discovered="
                        + registrar.getDiscoveredPacks().size()));
    }
}
