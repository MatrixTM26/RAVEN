package com.raven.interfaces.APP.shared;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class PathResolver {

    private final Path BaseDir;

    public PathResolver(Class<?> CallerClass) {
        this.BaseDir = Resolve(CallerClass);
    }

    private static Path Resolve(Class<?> CallerClass) {
        Path Cwd = Paths.get("").toAbsolutePath();
        if (Files.exists(Cwd.resolve("config"))) return Cwd;
        try {
            Path Jar = Paths.get(CallerClass.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent();
            for (Path Candidate : new Path[] { Jar, Jar != null ? Jar.getParent() : null }) if (Candidate != null && Files.exists(Candidate.resolve("config"))) return Candidate.toAbsolutePath();
        } catch (Exception Ignored) {}
        return Cwd;
    }

    public Path ResolvePath(String Relative) {
        Path Direct = Paths.get(Relative);
        if (Direct.isAbsolute() && Files.exists(Direct)) return Direct;
        Path FromBase = BaseDir.resolve(Relative);
        if (Files.exists(FromBase)) return FromBase;
        return Paths.get("").toAbsolutePath().resolve(Relative);
    }

    public Path GetBaseDir() {
        return BaseDir;
    }
}
