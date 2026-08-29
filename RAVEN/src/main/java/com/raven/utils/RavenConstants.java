package com.raven.utils;

import java.time.format.DateTimeFormatter;

public final class RavenConstants {

    private RavenConstants() {}

    public static final DateTimeFormatter TimestampFmt  = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    public static final DateTimeFormatter FilenameFmt   = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    public static final DateTimeFormatter ChatTimeFmt     = DateTimeFormatter.ofPattern("HH:mm:ss");

    public static final String ServerConfigPath = "config/server/raven.properties";
    public static final String OperatorConfigPath = "config/operator/operator.properties";
    public static final String ProfilesDir = "config/profiles";
    public static final String ProfileExt = ".profile";
    public static final String DefaultProfile = "default";
    public static final String CmdDispatchPath = "config/commands/command-dispatcher.properties";
}
