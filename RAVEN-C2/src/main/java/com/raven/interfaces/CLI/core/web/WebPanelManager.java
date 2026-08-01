package com.raven.interfaces.CLI.core.web;

import com.raven.core.output.Logger;
import com.raven.core.server.ListenerMode;
import com.raven.core.server.RavenServer;
import com.raven.interfaces.APP.WebApp;
import com.raven.utils.AnsiColor;
import com.raven.utils.ServerConfig;
import java.time.Instant;

public final class WebPanelManager {

    private final ServerConfig Config;

    private WebApp       WebPanel;
    private ListenerMode ActiveMode;

    public WebPanelManager(ServerConfig Config) {
        this.Config = Config;
    }

    public void SetActiveMode(ListenerMode ActiveMode) {
        this.ActiveMode = ActiveMode;
    }

    public void Start(String Host, int Port, RavenServer Server, Instant ServerStartTime) {
        if (WebPanel != null) {
            Logger.Custom("  %sWeb panel already running%s%n", AnsiColor.Red, AnsiColor.Reset);
            return;
        }
        try {
            WebPanel = new WebApp(Config, ActiveMode);
            WebPanel.Run(Host, Port);
            Logger.Custom("  %sWeb panel started > http://%s:%d/%s%n",
                AnsiColor.Green, Host, Port, AnsiColor.Reset);
        } catch (Exception Exception) {
            Logger.Error("Web panel start failed: " + Exception.getMessage());
            WebPanel = null;
        }
    }

    public void Stop() {
        if (WebPanel == null) {
            Logger.Custom("  %sWeb panel not running%s%n", AnsiColor.Red, AnsiColor.Reset);
            return;
        }
        WebPanel.Stop();
        WebPanel = null;
        Logger.Custom("  %sWeb panel stopped%s%n", AnsiColor.Green, AnsiColor.Reset);
    }

    public void ShowStatus() {
        if (WebPanel == null)
            Logger.Info("Web Panel  : " + AnsiColor.Red + "Offline" + AnsiColor.Reset);
        else
            Logger.Info("Web Panel  : " + AnsiColor.Green + "Online" + AnsiColor.Reset);
    }

    public boolean IsRunning() {
        return WebPanel != null;
    }
}
