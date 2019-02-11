package perf.qdup.config;

public class HostExpression {

    public static final String EXPRESSION_PATTERN = "= \\s*\\w+(?:[\\-+]\\s+\\w+)*";

    public final String expression;
    public HostExpression(String expression){
        this.expression = expression;
    }
    public String getExpression(){return expression;}
}
