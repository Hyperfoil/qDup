package io.hyperfoil.tools.qdup.config.converter;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FileSizeConverter {

    static final Pattern pattern = Pattern.compile("(?<sizeValue>[\\d.]+)(?<sizeUnit>[GMK]?B)*", Pattern.CASE_INSENSITIVE);
    static final Map<String, Integer> powerMap;

    static {
        powerMap = new HashMap<>();
        powerMap.put("B", 0);
        powerMap.put("KB", 1);
        powerMap.put("MB", 2);
        powerMap.put("GB", 3);
        powerMap.put("TB", 4);
        powerMap.put("PB", 5);
        powerMap.put("EB", 6);
    }
    public static long toBytes(String fileSize){
        long returnValue = -1;
        if( fileSize != null) {
            Matcher matcher = pattern.matcher(fileSize);
            if (matcher.find()) {
                String value = matcher.group("sizeValue");
                String unit = matcher.group("sizeUnit");
                if(unit == null){
                    unit = "B";
                }
                int pow = powerMap.get(unit.toUpperCase());
                BigDecimal bytes = new BigDecimal(value);
                bytes = bytes.multiply(BigDecimal.valueOf(1024).pow(pow));
                returnValue = bytes.longValue();
            }
        }
        return returnValue;

    }
}
