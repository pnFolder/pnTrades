package ru.privatenull.integration;

import org.bukkit.plugin.java.JavaPlugin;
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticConfiguration;
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticContainer;
import org.pnfolder.pnlibrary.api.logging.PnLogger;
import org.pnfolder.pnlibrary.api.plugin.ModuleContext;
import org.pnfolder.pnlibrary.api.plugin.PluginRegistration;
import org.pnfolder.pnlibrary.api.remote.PolicyErrorAction;
import org.pnfolder.pnlibrary.api.remote.RemotePolicySettings;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity;
import org.pnfolder.pnlibrary.api.runtime.PnLibraryProvider;
import org.pnfolder.pnlibrary.api.updates.UpdateChannel;
import ru.privatenull.log.Log;

import java.util.Map;
import java.util.logging.Level;

/** Managed pnLibrary integration: diagnostics, remote policy and updates. */
public final class PnLibraryRuntime implements AutoCloseable {

    public static final String MINIMUM_VERSION = "0.0.1-dev.6";
    private static final String MODULE_ID = "pntrades";
    private static final int BSTATS_PROJECT_ID = 35050;
    private static final String FASTSTATS_TOKEN = "a41706ca3e9826e2273f5bb903215b89";
    private static final String POLICY_SOURCE =
            "https://raw.githubusercontent.com/pnFolder/pnlibrary-policy/main/.pnlibrary/policies/PnTradesPolicy.java";
    private static final String UPDATE_OWNER = "pnFolder";
    private static final String UPDATE_REPOSITORY = "pnTrades";

    private final JavaPlugin owner;
    private final PnLibrary runtime;
    private PluginRegistration registration;
    private ModuleContext module;

    private PnLibraryRuntime(JavaPlugin owner, PnLibrary runtime) {
        this.owner = owner;
        this.runtime = runtime;
    }

    public static PnLibraryRuntime connect(JavaPlugin owner) {
        PnLibrary runtime = PnLibraryProvider.get();
        if (!runtime.isAtLeastVersion(MINIMUM_VERSION)) {
            throw new IllegalStateException("pnTrades требует pnLibrary " + MINIMUM_VERSION
                    + " или новее, установлена " + runtime.getVersion());
        }
        return new PnLibraryRuntime(owner, runtime);
    }

    public void register() {
        if (registration != null) throw new IllegalStateException("pnTrades уже зарегистрирован в pnLibrary");
        registration = runtime.getPlugins().register(owner);
        try {
            module = registration.registerModule(MODULE_ID, builder -> builder
                    .metrics(metrics -> metrics.bStats(BSTATS_PROJECT_ID).fastStats(FASTSTATS_TOKEN))
                    .diagnostics(owner.getDataFolder().toPath(), DiagnosticContainer.builder("pntrades")
                            .snapshot(() -> Map.of("version", owner.getDescription().getVersion()))
                            .configuration(DiagnosticConfiguration.builder("config.yml")
                                    .secretKeyRegex("(?i).*(password|passwd|secret|token|uri|url|user(name)?)$")
                                    .build())
                            .build())
                    // A policy that cannot be checked (offline server) never stops pnTrades;
                    // only an explicit denial from the policy does.
                    .remotePolicy(RemotePolicySettings.builder()
                            .source(POLICY_SOURCE)
                            .onError(PolicyErrorAction.KEEP_RUNNING)
                            .support(PnLibraryIdentity.SUPPORT_URL)
                            .build())
                    .updates(UPDATE_OWNER, UPDATE_REPOSITORY, updater -> updater
                            .channel(UpdateChannel.STABLE)
                            .automaticDownload(false)
                            .configurationBackup(owner.getDataFolder().toPath(), "config.yml", "gui.yml")
                            .artifact("(?i)^pnTrades-[0-9].*\\.jar$", 17)));
            Log.attach(moduleSink(module.getLogger()));
        } catch (RuntimeException failure) {
            closeRegistration();
            throw failure;
        }
    }

    @Override
    public void close() {
        ModuleContext closing = module;
        module = null;
        Log.attach(null);
        if (closing != null && !closing.isClosed()) {
            try {
                closing.getLifecycle().disabled().show();
            } catch (RuntimeException failure) {
                owner.getLogger().log(Level.FINE, "Не удалось вывести сообщение об остановке pnTrades", failure);
            }
        }
        closeRegistration();
    }

    private static Log.Sink moduleSink(PnLogger logger) {
        return new Log.Sink() {
            @Override
            public void info(String message) {
                logger.info(message);
            }

            @Override
            public void success(String message) {
                logger.success(message);
            }

            @Override
            public void warning(String message) {
                logger.warning(message);
            }

            @Override
            public void error(String message, Throwable error) {
                logger.error(message, error);
            }
        };
    }

    private void closeRegistration() {
        PluginRegistration closing = registration;
        registration = null;
        if (closing != null && !closing.isClosed()) closing.close();
    }
}
