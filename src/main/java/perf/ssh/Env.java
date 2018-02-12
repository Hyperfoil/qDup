package perf.ssh;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class Env {

    private Map<String,String> data;

    public Env(Map<String,String> data){
        this.data = data;
    }
    public Env(String input){
        this(parse(input));
    }

    public Set<String> keys(){return data.keySet();}
    public String get(String key){return data.get(key);}
    public boolean isEmpty(){return data.isEmpty();}
    public int size(){return data.size();}


    public static Map<String,String> parse(String input){
        Map<String,String> rtrn = new LinkedHashMap<>();
        //input = input.trim();
        if(input!=null && !input.isEmpty()){
            int i=0;
            int valueStart = 0;
            String key="";

            boolean noSpacesFromLineStart=true;
            boolean haveKey=false;
            int prevNewLine=0;
            while(i<input.length()){

                switch (input.charAt(i)){
                    case '=':
                        if(noSpacesFromLineStart){
                            if(haveKey && prevNewLine > valueStart){
                                String value = input.substring(valueStart,prevNewLine).trim();
                                rtrn.put(key,value);
                                haveKey=false;
                            }
                            key = input.substring(prevNewLine+1,i).trim();
                            valueStart=i+1;
                            haveKey=true;
                        }
                        break;
                    case ' ':
                    case '\t':
                        noSpacesFromLineStart=false;
                        break;
                    case '\n':
                        prevNewLine=i;
                        noSpacesFromLineStart=true;
                }
                i++;
            }
            if(valueStart < i ){
                rtrn.put(key,input.substring(valueStart).trim());
            }
        }

        return rtrn;
    }
}
