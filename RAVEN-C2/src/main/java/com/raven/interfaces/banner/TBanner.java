package com.raven.interfaces.banner;

import com.raven.core.output.Logger;
import com.raven.utils.AnsiColor;

public final class TBanner {

    private TBanner() {}

    public static void Logo() {
        String Banner =
            "\n" +
            AnsiColor.Bold +
            AnsiColor.Red +
            "\n" +
            "   11111111111         11111   111         111   11111111111    1111      111 \n" +
            "   111      111       111 111   111       111    111            111111    111 \n" +
            "   111      111      111   111   111     111     111111111      111 111   111 \n" +
            "   11111111111      111     111   111   111      111            111   111 111 \n" +
            "   111      111    111       111   111 111       111            111     11111 \n" +
            "   111      111   111         111   11111        11111111111    111      1111 \n" +
            AnsiColor.White +
            "          * when lifes gives you lemons, just lemonade then weaponized *      \n" +
            AnsiColor.Reset;
        Logger.Custom(Banner, 1);
    }
}
