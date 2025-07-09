package io.hyperfoil.tools.qdup.cli;

import picocli.CommandLine;

public class PathConverter implements CommandLine.ITypeConverter<String> {
    @Override
    public String convert(String value) throws Exception {
        return value.replaceFirst("^~",System.getProperty("user.home"));
    }
}
