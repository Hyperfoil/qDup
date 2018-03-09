package perf.qdup.cmd.impl;

import perf.qdup.cmd.Cmd;
import perf.qdup.cmd.Context;
import perf.qdup.cmd.CommandResult;

import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Sleep extends Cmd {

    public static final String MINUTES = "m";
    public static final String SECONDS = "s";
    public static final String MILLISECONDS = "ms";
    public static final String HOURS = "h";

    private static final Pattern timeUnitPattern = Pattern.compile("(?<amount>\\d+)(?<unit>"+MILLISECONDS+"|"+MINUTES+"|"+SECONDS+"|"+HOURS+")?");

    String amount;
    public Sleep(String amount){
        super(true);
        this.amount = amount;
    }

    public String getAmount(){return amount;}

    public static long parseToMs(String amount){
        amount = amount.replaceAll("_","");
        long rtrn = 0;
        Matcher m = timeUnitPattern.matcher(amount);
        while(m.find()){
            long toAdd = Long.parseLong(m.group("amount"));
            String unit = m.group("unit") == null ? "" : m.group("unit"); //in case there isn't a unit
            TimeUnit timeUnit;
            switch (unit){
                case HOURS:
                    timeUnit = TimeUnit.HOURS;
                    break;
                case MINUTES:
                    timeUnit = TimeUnit.MINUTES;
                    break;
                case SECONDS:
                    timeUnit = TimeUnit.SECONDS;
                    break;
                case MILLISECONDS:
                default:
                    timeUnit = TimeUnit.MILLISECONDS;
            }
            long increment = timeUnit.toMillis(toAdd);
            rtrn += increment;
        }
        return rtrn;
    }

    @Override
    protected void run(String input, Context context, CommandResult result) {

        long sleepMs = parseToMs(amount);

        context.schedule(this,() -> result.next(this,input),sleepMs);
    }

    @Override
    protected Cmd clone() {
        return new Sleep(this.amount).with(this.with);
    }

    @Override public String toString(){return "sleep: "+amount;}


}
