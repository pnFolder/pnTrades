package ru.privatenull;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import ru.privatenull.command.TradeCommand;
import ru.privatenull.config.GuiConfig;
import ru.privatenull.gui.TradeListener;
import ru.privatenull.lifecycle.PluginBanner;
import ru.privatenull.service.TradeManager;
import org.pnfolder.pnlibrary.bukkit.bootstrap.PnLibraryBootstrap;
import ru.privatenull.bootstrap.PnLibraryLifecycleListener;
import ru.privatenull.integration.PnLibraryRuntime;
import ru.privatenull.log.Log;

import java.util.Objects;

public final class PnTradesPlugin extends JavaPlugin {
    public static final String SUPPORT_DISCORD = "https://discord.gg/rRbzq6cnc6";
    private TradeManager tradeManager;
    private boolean pnLibraryReady;
    private PnLibraryRuntime pnRuntime;

    @Override
    public void onLoad() {
        Log.fallback(getLogger());
        try {
            pnLibraryReady = PnLibraryBootstrap.ensureInstalled(this, PnLibraryRuntime.MINIMUM_VERSION);
        } catch (Throwable failure) {
            Log.error("Не удалось автоматически подготовить pnLibrary. pnTrades не будет запущен.", failure);
        }
    }

    @Override
    public void onEnable() {
        if (!pnLibraryReady) {
            Log.error("pnLibrary не подготовлена: проверьте доступ к GitHub и ошибку загрузки выше.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        try {
            pnRuntime = PnLibraryRuntime.connect(this);
            pnRuntime.register();
        } catch (Throwable failure) {
            Log.error("pnLibrary не удалось включить или подключить к pnTrades.", failure);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getServer().getPluginManager().registerEvents(new PnLibraryLifecycleListener(this), this);
        saveDefaultConfig();
        tradeManager = new TradeManager(this, new GuiConfig(this));
        TradeCommand command = new TradeCommand(tradeManager);
        PluginCommand trade = Objects.requireNonNull(getCommand("trade"), "trade command is missing");
        trade.setExecutor(command);
        trade.setTabCompleter(command);
        getServer().getPluginManager().registerEvents(new TradeListener(tradeManager), this);
        PluginBanner.enabled(this, SUPPORT_DISCORD);
    }

    @Override
    public void onDisable() {
        if (tradeManager != null) {
            tradeManager.closeAll();
        }
        PnLibraryRuntime closing = pnRuntime;
        pnRuntime = null;
        if (closing != null) {
            try {
                closing.close();
            } catch (Exception exception) {
                Log.warning("Не удалось корректно закрыть контекст pnLibrary", exception);
            }
        }
        if (pnLibraryReady) PluginBanner.disabled(this, SUPPORT_DISCORD);
    }

    public String message(String path, String playerName) {
        String value = getConfig().getString("messages." + path, "");
        return value.replace("{player}", playerName).replace('&', '§');
    }
}
