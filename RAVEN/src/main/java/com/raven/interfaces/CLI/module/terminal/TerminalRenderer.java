package com.raven.interfaces.CLI.module.terminal;

import com.raven.utils.TerminalHelper;

public final class TerminalRenderer {

    public TerminalRenderer() {}

    public int ContentWidth() {
        return TerminalHelper.ContentWidth();
    }

    public String Indent(String Text) {
        return "  " + Text;
    }

    public String Divider() {
        return TerminalHelper.Divider();
    }

    public String Box(String Title) {
        return TerminalHelper.Box(Title);
    }

    public String OutputBox(String Output) {
        return TerminalHelper.OutputBox(Output);
    }
}
