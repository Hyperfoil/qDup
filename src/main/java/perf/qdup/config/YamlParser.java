package perf.qdup.config;

import perf.yaup.HashedLists;
import perf.yaup.Sets;
import perf.yaup.json.Json;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static perf.qdup.cmd.Cmd.STATE_PREFIX;
import static perf.qdup.cmd.Cmd.STATE_SUFFIX;

public class YamlParser {

    public static final String TIMER = "timer";
    public static final String WATCH = "watch";
    public static final String WITH = "with";
    public static final Set<String> RESERVED = Sets.of(WATCH, WITH);


    public static final String CHILD = "child";
    public static final String DASHED = "dashed";
    public static final String KEY = "key";
    public static final String VALUE = "value";
    public static final String COMMENT = "comment";
    public static final String LINE_NUMBER = "lineNumber";

    private static String CHILD_LENGTH = "childLength";
    private static String CHILD_ARRAY = "childArray";

    private static String INLINE_MAP = "inlineMap";
    private static String INLINE_LIST = "inlineList";


    private class Builder {
        private Stack<Json> targets;
        private Stack<Json> contexts;

        public Builder(){
            targets = new Stack<>();
            contexts = new Stack<>();
        }
        public void reset(Json json){
            targets.clear();
            contexts.clear();
            push(json);
        }
        public void push(Json target){
            targets.push(target);
            contexts.push(new Json(false));
        }

        public String debug(){
            StringBuilder sb = new StringBuilder();
            int w=0;
            for(int i=0; i<targets.size(); i++){
                if(contexts.elementAt(contexts.size()-(i+1)).toString().length()>w){
                    w=contexts.elementAt(contexts.size()-(i+1)).toString().length();
                }
            }
            for(int i=0; i<targets.size(); i++){
                sb.append(String.format("[%2d] %"+w+"s : %s%n",
                        i,
                        contexts.elementAt(contexts.size()-(i+1)).toString(),
                        targets.elementAt(targets.size()-(i+1)).toString())
                );
            }
            return sb.toString();
        }

        public void pop(){
            if(targets.size()>1) {
                targets.pop();
                contexts.pop();
            }else{
            }
        }
        public int size(){return targets.size();}
        public Json target(){return targets.peek();}
        public Json peekTarget(int amount){
            if(targets.size()>=amount)
                return targets.elementAt(targets.size()-(amount));
            else{
                return targets.elementAt(0);
            }
        }
        public boolean hasAt(String key,int offset){
            boolean rtrn = false;
            if(contexts.size()>offset+1){
                rtrn = contexts.elementAt(contexts.size()-(offset+1)).has(key);
            }else{

            }
            return rtrn;
        }
        public boolean has(String key,boolean recursive){
            boolean rtrn = false;
            if(!recursive){
                rtrn = contexts.peek().has(key);
            }else{
                int i = contexts.size()-1;
                while(i>=0 && !rtrn){
                    rtrn = contexts.elementAt(i).has(key);
                    i--;
                }
            }
            return rtrn;
        }
        public void set(String key,Object value){
            contexts.peek().set(key,value);
        }
        public Object get(String key,boolean recursive){
            return get(key,recursive,0);
        }
        public Object get(String key,boolean recursive,int skip){
            Object rtrn = null;
            if(!recursive){
                rtrn = contexts.peek().get(key);
            }else{
                int i= contexts.size()-1;
                while(i>=0 && rtrn==null){
                    rtrn = contexts.elementAt(i).get(key);
                    i--;
                    if(rtrn!=null && skip>0){
                        skip--;
                        rtrn=null;
                    }
                }
            }
            return rtrn;
        }
        public int getInt(String key,boolean recursive){
            Object obj = get(key,recursive);
            if(obj != null && obj instanceof Integer){
                return (Integer)obj;
            }else{
                return 0;
            }
        }
        public String getString(String key,boolean recursive){
            Object obj = get(key,recursive);
            if(obj !=null && obj instanceof String){
                return (String)obj;
            }else{
                return "";
            }
        }

    }

    Builder builder;

    Matcher nestMatcher = Pattern.compile("^(?<child>[\\s-]*)").matcher("");
    Matcher inlineValueMatcher = Pattern.compile("^(?<value>\"(?:[^\"]|\\\")+\"|[^{\\[}\\],#]+)").matcher("");
    Matcher valueMatcher = Pattern.compile("^(?<value>\"(?:[^\"]|\\\")+\"|(?:[^{\\[{}\\],#]|}}|\\{\\{)*)").matcher("");
    Matcher inlineKeyMatcher = Pattern.compile("^(?<key>\"(?:[^\"]|\\\")+\"|[^:#,]+)").matcher("");
    Matcher keyMatcher = Pattern.compile("^(?<key>\"(?:[^\"]|\\\")+\"|(?:[^:#,\\s\\[\\]{}]|}}|\\{\\{)+)").matcher("");

    private HashedLists<String,String> fileErrors;
    private HashMap<String,Json> loaded;

    public YamlParser(){
        builder = new Builder();
        fileErrors = new HashedLists<>();
        loaded = new LinkedHashMap<>();
    }

    public Set<String> fileNames(){return loaded.keySet();}
    public boolean hasErrors(){return !fileErrors.isEmpty();}
    public List<String> getErrors(){return Collections.unmodifiableList(fileErrors.values().stream().flatMap(v->v.stream()).collect(Collectors.toList()));}
    private void addError(String key,String error){
        fileErrors.put(key,error);
    }

    public void load(String yamlPath){
        try(InputStream stream = new FileInputStream(yamlPath)){
            load(yamlPath,new FileInputStream(yamlPath));
        } catch (FileNotFoundException e) {
            addError(yamlPath,"could not find "+yamlPath);
        } catch (IOException e) {
            addError(yamlPath,"failed to read "+yamlPath);
        }
    }
    private int charactersUntil(String line,char target){
        return charactersUntil(line,target,0);
    }
    private int charactersUntil(String line,char target,int start){
        boolean stop = false;
        int i=start;
        while(i<line.length() && !stop){
            if(target == line.charAt(i)){
                if(i > 0 && '\\'==line.charAt(i-1)){
                }else{
                    stop=true;
                }
            }
            i++;
        }
        return i;
    }

    private enum State {ExpectKey,ExpectValue,InKey,InValue};

    public void load(String fileName,InputStream stream){
        State state = State.ExpectKey;
        Json json = new Json(true);
        builder.reset(json);
        try(BufferedReader reader = new BufferedReader(new InputStreamReader(stream))){
            String originalLine = null;
            int lineNumber = 0;
            boolean nestedDash = false;
            boolean emptyDash = false;
            boolean scalar = false;
            boolean foldedScalar = false;
            boolean quoted=false;
            String quotedKey="";
            char quoteClose='"';
            Stack<String> inlineStack = new Stack<>();

            while((originalLine=reader.readLine())!=null && ++lineNumber > 0) {
                String line = originalLine;
                //start of a new line, if we aren't quoted we expect to find a key
                if(!quoted && !scalar){
                    state = State.ExpectKey;
                }
                while(!line.isEmpty() && !hasErrors()) {
                    if (quoted) {
                        boolean stop = false;
                        int i = charactersUntil(line, quoteClose);
                        if (!line.isEmpty() && (i < line.length() || (quoteClose == line.charAt(i - 1) && '/' != line.charAt(i - 2)))) {
                            quoted = false;
                            if (KEY.equals(quotedKey)) {
                                state = State.ExpectValue;//finished the key
                                //finishedMultiLineKey=true;
                            } else {
                                state = State.InValue;
                                //finishedMultiLineValue=true;
                            }
                        }
                        String additionalValue = line.substring(0, i);
                        if (!quoted) {
                            if (KEY.equals(quotedKey) && additionalValue.endsWith(quoteClose + "")) {
                                additionalValue = additionalValue.substring(0, additionalValue.length() - 1);
                            } else if (VALUE.endsWith(quotedKey)) {
                                //TODO finish the value parsing for the rest of this line
                                ////VALUE_PARSING
                            }
                        }

                        line = line.substring(i);
                        if(KEY.endsWith(quotedKey)){
                            line = line.replaceAll("^\\s*","");
                        }

                        String existingValue = builder.target().getString(quotedKey, "");
                        String newValue = (
                                builder.target().get(quotedKey) +
                                        (existingValue.length() > 1 ? System.lineSeparator() : "") + // check > 1 because of staritng quoteChar
                                        additionalValue
                        ).replaceAll("\\s+$", "");
                        builder.target().set(quotedKey, newValue);
                    }
                    if(!line.isEmpty() && !hasErrors()) {
                        switch (state) {
                            case InValue:  //just found a ': [^\\{\\[]' or scalar or finished a quote and have more line
                                if (scalar) {
                                    int keyIndent = builder.getInt(CHILD_LENGTH, true);
                                    int lineIndent = 0;
                                    while (line.startsWith(" ", lineIndent)) {
                                        lineIndent++;
                                    }
                                    if (lineIndent > keyIndent) {
                                        //TODO deal with newlines
                                        String prefix = "";
                                        if (!foldedScalar) {
                                            if (builder.target().getString(VALUE, "").length() > 0) {
                                                prefix = System.lineSeparator();
                                            }
                                        } else {
                                            //TODO need to be able to know if it is more indented
                                            if (builder.target().getString(VALUE, "").length() > 0) {
                                                prefix = " ";
                                            }
                                        }

                                        builder.target().set(VALUE, builder.target().get(VALUE) + prefix + line.trim());

                                        line = "";
                                    } else if (line.isEmpty()) {
                                        String prefix = System.lineSeparator();

                                        builder.target().set(VALUE, builder.target().get(VALUE) + prefix);

                                    } else {
                                        scalar = false;
                                        foldedScalar = false;
                                        //just closed a value so expect a key
                                        state = State.ExpectKey;
                                    }
                                } else if (!quoted) {//capture case where just closed a quote or not in one to begin with
                                    int i = 0;
                                    boolean stop = false;
                                    boolean inVariable = false;
                                    while (i < line.length() && !stop) {
                                        switch (line.charAt(i)) {
                                            case ',': //, separating key:value , key:value inline map
                                                if (!quoted && !inVariable && !inlineStack.isEmpty()) {
                                                    stop = true;
                                                    i--;
                                                    state = State.ExpectKey;
                                                }
                                                break;
                                            case '$':
                                                if (!quoted && line.startsWith(STATE_PREFIX, i)) {
                                                    inVariable = true;
                                                }
                                                break;
                                            case '#':
                                                if(!quoted){
                                                    stop=true;
                                                    i--;
                                                    state = State.ExpectKey;//expect key captures the comment
                                                }
                                                break;
                                            case '"':
                                            case '\'':
                                                if (!quoted) {
                                                    quoted = true;
                                                    quoteClose = line.charAt(i);
                                                    quotedKey = VALUE;
                                                } else {
                                                    if ('\\' == line.charAt(i - 1) || quoteClose != line.charAt(i)) {
                                                        //not a close quote
                                                    } else {
                                                        quoted = false;
                                                    }
                                                }
                                                break;
                                            case '}':
                                                if (!quoted) {
                                                    if (inVariable && line.startsWith(STATE_SUFFIX, i)) {
                                                        i++;//skip the next }
                                                        inVariable = false;
                                                    } else if (!inlineStack.isEmpty() && INLINE_MAP.equals(inlineStack.peek())) {
                                                        stop = true;//end of nested map
                                                        i--;
                                                        state = State.ExpectKey;
                                                    }
                                                }
                                                break;
                                            case ']':
                                                if (!quoted) {
                                                    if (!inlineStack.isEmpty() && INLINE_LIST.equals(inlineStack.peek())) {
                                                        stop = true;//end of nested list
                                                        i--;
                                                        state = State.ExpectKey;
                                                    }
                                                }
                                                break;
                                        }
                                        i++;
                                    }
                                    if (i >= 1) {
                                        String lineValue = line.substring(0, i);
                                        if ("|".equals(lineValue.trim()) || ">".equals(lineValue.trim())) {
                                            scalar = true;
                                            if (lineValue.trim().endsWith(">")) {
                                                foldedScalar = true;
                                            }
                                            lineValue = "";
                                        }

                                        builder.target().set(VALUE, builder.target().getString(VALUE,"")+lineValue.replaceAll("\\s+$", ""));

                                        line = line.substring(i).replaceAll("\\s+$", "");
                                    }
                                }
                                break;

                            case InKey:
                                //not sure how this differs from quoted which is handled before switch
                                break;

                            case ExpectKey:
                                String lineContent = line.replaceAll("^\\s*","");
                                if(lineContent.startsWith("---")){
                                    line="";
                                }else if (lineContent.startsWith("---")){
                                    line="";
                                }else if(lineContent.startsWith("#")){//lineContent avoids the nest changes
                                    if(builder.target().isEmpty() || builder.target().isArray()){
                                        Json commentJson = new Json();
                                        commentJson.add(COMMENT,lineContent.substring(1));
                                        builder.target().add(commentJson);
                                    }else{

                                        if(!builder.target().has(COMMENT)){
                                            builder.target().set(COMMENT,lineContent.substring(1));
                                        }else{
                                            //don't see how we would have two comments on one line
                                            //don't add error because it's a parse error not content error
                                        }
                                    }
                                    line="";

                                    //now we are getting to the parts where the line will already be trimmed
                                }else if (line.startsWith(",")){
                                    if(!inlineStack.isEmpty()){
                                        while(!builder.hasAt(INLINE_LIST,1) && !builder.hasAt(INLINE_MAP,1) ){
                                            builder.pop();//close the previous entry
                                        }
                                        line = line.substring(1).trim();
                                    }
                                }else if (line.startsWith("[")){
                                    inlineStack.push(INLINE_LIST);

                                    Json childArray = new Json();
                                    Json firstEntry = new Json();
                                    childArray.add(firstEntry);
                                    if(builder.target().isArray()){
                                        Json newEntry = new Json();
                                        newEntry.add(CHILD,childArray);
                                        builder.target().add(newEntry);

                                    }else{
                                        builder.target().add(CHILD, childArray);
                                    }
                                    builder.push(childArray);
                                    builder.set(INLINE_LIST,true);
                                    builder.push(firstEntry);

                                    line = line.substring(1).trim();
                                }else if (line.startsWith("{")) {//new inline map
                                    inlineStack.push(INLINE_MAP);

                                    Json childArray = new Json();
                                    Json firstEntry = new Json();

                                    childArray.add(firstEntry);

                                    if (builder.target().isArray()) {
                                        Json newEntry = new Json();
                                        newEntry.add(CHILD, childArray);
                                        builder.target().add(newEntry);
                                    } else {
                                        builder.target().add(CHILD, childArray);
                                    }
                                    builder.push(childArray);
                                    builder.set(INLINE_MAP, true);
                                    builder.push(firstEntry);


                                    line = line.substring(1).trim();
                                }else if (line.startsWith("]")){//end inline list
                                    line = line.substring(1).trim();
                                    if(!INLINE_LIST.equals(inlineStack.peek())) {
                                        addError(fileName, String.format(
                                                "Encountered ] but expected } %n%s[%d]: %s%n",
                                                fileName,
                                                lineNumber,
                                                originalLine
                                        ));
                                    }
                                    inlineStack.pop();

                                    while(!builder.has(INLINE_LIST,false) && builder.size()>1){
                                        builder.pop();
                                    }
                                    builder.pop();//pop the inline list, we just closed it :)
                                }else if (line.startsWith("}")) {//end inline map
                                    line = line.substring(1).trim();
                                    if(!INLINE_MAP.equals(inlineStack.peek())){
                                        addError(fileName, String.format(
                                                "Encounterd } but expected ] %n%s[%d]: %s%n",
                                                fileName,
                                                lineNumber,
                                                originalLine
                                        ));

                                    }
                                    inlineStack.pop();

                                    while(!builder.has(INLINE_MAP,false) && builder.size()>1){
                                        builder.pop();
                                    }
                                    builder.pop();//pop the inline map, we just closed it :)
                                } else if (line.startsWith(":")){ // expect value
                                    state = State.ExpectValue;

                                    //now we actually expect a key character :)
                                }else if (nestMatcher.reset(line).find()){
                                    //but first we have to indent
                                    String childValue = nestMatcher.group(CHILD);
                                    int childLength = childValue.length();
                                    int contextLength = builder.getInt(CHILD_LENGTH,true);

                                    if(inlineStack.isEmpty() && !emptyDash) {

                                        if (childLength > contextLength) { // CHILD
                                            Json childAry = new Json();
                                            Json newChild = new Json(false);

                                            if (builder.target().has(CHILD)) {
                                                childAry = builder.target().getJson(CHILD);
                                            } else {
                                                builder.target().add(CHILD, childAry);
                                            }

                                            childAry.add(newChild);

                                            builder.push(childAry);
                                            builder.set(CHILD_ARRAY, true);
                                            builder.push(newChild);
                                            builder.set(CHILD_LENGTH, childLength);
                                        } else if (childLength < contextLength) {//elder

                                            while (childLength < builder.getInt(CHILD_LENGTH, true) || builder.has(CHILD_ARRAY, false)) {
                                                builder.pop();
                                            }

                                            if(childValue.contains("-")){
                                                while (!builder.has(CHILD_ARRAY,false)){
                                                    builder.pop();
                                                }
                                                Json newEntry = new Json(false);
                                                builder.target().add(newEntry);
                                                builder.push(newEntry);
                                                builder.set(CHILD_LENGTH,childLength);
                                            }else{
                                                //space indent, don't change target
                                            }
                                        } else { //sibling
                                            if (childValue.contains("-")) {
                                                while (!builder.has(CHILD_ARRAY, false)) {
                                                    builder.pop();
                                                }
                                                Json newJson = new Json(false);
                                                builder.target().add(newJson);
                                                builder.push(newJson);
                                            } else {
                                                while (!builder.target().isArray()) {
                                                    builder.pop();
                                                }
                                            }
                                            builder.set(CHILD_LENGTH, childLength);

                                        }
                                        if(childValue.contains("-")){
                                            nestedDash = true;
                                        }
                                    }

                                    if(!inlineStack.isEmpty() && childValue.contains("-")){
                                        addError(fileName,
                                                String.format("Encountered - inside an inline list or map%n%s[%d]: %s%n",
                                                        fileName,
                                                        lineNumber,
                                                        originalLine));
                                    }
                                    line = line.substring(nestMatcher.end()).trim();

                                    if(line.trim().isEmpty() && nestedDash){
                                       emptyDash = true;
                                    }else{
                                        emptyDash = false;
                                    }

                                    //nesting all done, now look at content of line
                                    line = line.trim();
                                    String keyValue = null;
                                    int end = 0;
                                    if(line.startsWith("\"") || line.startsWith("'")){
                                        quoteClose = line.charAt(0);
                                        quoted=true;
                                        quotedKey=KEY;
                                        line = line.substring(1);
                                        int i = charactersUntil(line,quoteClose);

                                        if(!line.isEmpty() && (i<line.length() || ( (i>1 && quoteClose==line.charAt(i-1)) && ( i>2 && '/'!=line.charAt(i-2)) )) ){
                                            quoted=false;
                                        }
                                        end=i;
                                        keyValue = line.substring(0,end);
                                        if(!quoted && keyValue.endsWith(""+quoteClose)){
                                            keyValue = keyValue.substring(0,keyValue.length()-1);
                                        }
                                    }else if (keyMatcher.reset(line).find()){
                                        keyValue = keyMatcher.group(KEY);
                                        end = keyMatcher.end();
                                    }
                                    if(keyValue!=null){
                                        if( (keyValue.startsWith("\"") && keyValue.endsWith("\"")) ||
                                            (keyValue.startsWith("'") && keyValue.endsWith("'")) ){
                                            if(keyValue.length()<=2){
                                                keyValue="";
                                                line="";
                                            }else {
                                                keyValue = keyValue.substring(1, keyValue.length() - 1);
                                            }
                                        }
                                        //start the new entry
                                        if (builder.target().isEmpty()){
                                            Json newEntry = new Json(false);
                                            builder.target().add(newEntry);
                                            builder.push(newEntry);

                                        }else if (!builder.target().isArray()){
                                            while(!builder.target().isArray()){
                                                builder.pop();
                                            }
                                            Json newEntry = new Json(false);
                                            builder.target().add(newEntry);
                                            builder.push(newEntry);
                                        } else {
                                            Json newEntry = new Json(false);
                                            builder.target().add(newEntry);
                                            builder.push(newEntry);
                                        }

                                        if(builder.target().has(KEY)){
                                            addError(fileName,
                                                    String.format("Key already exists %n%s[%d]: %s%n",
                                                            fileName,
                                                            lineNumber,
                                                            originalLine));
                                        }else{
                                            builder.target().set(KEY,keyValue);
                                            builder.target().set(LINE_NUMBER,lineNumber);
                                        }
                                        if(nestedDash){
                                            builder.target().set(DASHED, true);
                                            nestedDash = false;
                                        }
                                        if(!line.isEmpty()) {
                                            line = line.substring(end).trim();
                                        }
                                    }else{
                                        //umm... what?
                                    }

                                }
                                break;

                            case ExpectValue:
                                if(",[]{}".contains(""+line.charAt(0))){//characters that indicate there will be a key
                                    state = State.ExpectKey;
                                }
                                if(line.startsWith(":")){
                                    line = line.substring(1).replaceAll("^\\s*","");
                                    //if we find an inline map or list instead of a value
                                    if(line.isEmpty() || "[]{}".contains(""+line.charAt(0))){
                                        state = State.ExpectKey;
                                    }else{
                                        state = State.InValue;
                                    }
                                }else{
                                    //umm.... what?
                                    state = State.ExpectKey;
                                }
                                break;

                        }
                    }
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        loaded.put(fileName,json);
    }

    public void load2(String fileName,InputStream stream){


        State state = State.ExpectKey;
        Json json = new Json(true);
        builder.reset(json);
        try(BufferedReader reader = new BufferedReader(new InputStreamReader(stream))){
            String originalLine = null;
            int lineNumber = 0;
            boolean nestedDash = false;
            boolean emptyDash = false;
            boolean scalar = false;
            boolean foldedScalar = false;
            boolean quoted=false;
            boolean finishedMultiLineKey = false;
            boolean finishedMultiLineValue = false;
            String quotedKey="";
            char quoteClose='"';
            Stack<String> inlineStack = new Stack<>();
            while((originalLine=reader.readLine())!=null && ++lineNumber > 0){
                String line = originalLine;
                if(scalar){
                    int keyIndent = builder.getInt(CHILD_LENGTH, true);
                    int lineIndent = 0;
                    while(line.startsWith(" ",lineIndent)){
                        lineIndent++;
                    }
                    if(lineIndent>keyIndent){
                        //TODO deal with newlines
                        String prefix = "";
                        if(!foldedScalar) {
                            if(builder.target().getString(VALUE,"").length()>0) {
                                prefix = System.lineSeparator();
                            }
                        }else {
                            //TODO need to be able to know if it is more indented
                            if(builder.target().getString(VALUE,"").length()>0) {
                                prefix = " ";
                            }
                        }

                        builder.target().set(VALUE,builder.target().get(VALUE)+prefix+line.trim());
                        line = "";
                    }else if (line.isEmpty()) {
                        String prefix = System.lineSeparator();
                        builder.target().set(VALUE,builder.target().get(VALUE)+prefix);
                    }else{
                        scalar = false;
                        foldedScalar = false;
                    }
                }else if (quoted){
                    boolean stop = false;
                    int i= charactersUntil(line,quoteClose);
                    if(!line.isEmpty() && (i < line.length() || (quoteClose==line.charAt(i-1) && '/'!=line.charAt(i-2))) ){
                        quoted=false;
                        if(KEY.equals(quotedKey)){
                            finishedMultiLineKey=true;
                        }else{
                            finishedMultiLineValue=true;
                        }
                    }
                    String additionalValue = line.substring(0,i);

                    if(!quoted){
                        if(KEY.equals(quotedKey) && additionalValue.endsWith(quoteClose+"")){
                            additionalValue = additionalValue.substring(0,additionalValue.length()-1);
                        }else if (VALUE.endsWith(quotedKey)){
                            //TODO finish the value parsing for the rest of this line
                            ////VALUE_PARSING
                        }
                    }

                    line = line.substring(i);
                    String existingValue = builder.target().getString(quotedKey,"");
                    String newValue = (
                            builder.target().get(quotedKey)+
                            (existingValue.length()>1?System.lineSeparator():"")+ // check > 1 because of staritng quoteChar
                            additionalValue
                        ).replaceAll("\\s+$", "");
                    builder.target().set(quotedKey,newValue);
                }

                if(line.isEmpty()){

                }else if (line.trim().startsWith("---")) {

                }else if (line.trim().startsWith("...")){

                }else if (line.trim().isEmpty()){

                }else if ( nestMatcher.reset(line).find()){
                    if(!finishedMultiLineKey) {
                        String childValue = nestMatcher.group(CHILD);
                        int childLength = childValue.length();
                        int contextLength = builder.getInt(CHILD_LENGTH, true);

                        if (inlineStack.isEmpty() && !emptyDash) {


                            if (childLength > contextLength) { // CHILD
                                Json childAry = new Json();
                                Json newChild = new Json(false);

                                if (builder.target().has(CHILD)) {
                                    childAry = builder.target().getJson(CHILD);
                                } else {
                                    builder.target().add(CHILD, childAry);
                                }

                                childAry.add(newChild);

                                builder.push(childAry);
                                builder.set(CHILD_ARRAY, true);
                                builder.push(newChild);
                                builder.set(CHILD_LENGTH, childLength);

                            } else if (childLength < contextLength) { // elder

                                while (childLength < builder.getInt(CHILD_LENGTH, true) || builder.has(CHILD_ARRAY, false)) {
                                    builder.pop();
                                }

                                if (childValue.contains("-")) {
                                    while (!builder.has(CHILD_ARRAY, false)) {
                                        builder.pop();
                                    }
                                    Json newEntry = new Json(false);
                                    builder.target().add(newEntry);
                                    builder.push(newEntry);
                                    builder.set(CHILD_LENGTH, childLength);
                                } else {
                                    //space indent, don't change target
                                }

                            } else {//sibling
                                if (childValue.contains("-")) {
                                    while (!builder.has(CHILD_ARRAY, false)) {
                                        builder.pop();
                                    }

                                    Json newJson = new Json(false);
                                    builder.target().add(newJson);
                                    builder.push(newJson);


                                } else {
                                    while (!builder.target().isArray()) {
                                        builder.pop();
                                    }
                                }
                                builder.set(CHILD_LENGTH, childLength);


                            }

                            if (childValue.contains("-")) {
                                nestedDash = true;
                            }


                            //nesting all done, now look at content of line
                        }
                        if (!inlineStack.isEmpty() && childValue.contains("-")) {
                            //we are in an inline structure but there is a -
                            addError(fileName, String.format("Encountered - inside an inline list | map%n%s[%d]: %s%n", fileName, lineNumber, originalLine));
                        }
                        line = line.substring(nestMatcher.end());

                        if (line.trim().isEmpty() && nestedDash) {
                            emptyDash = true;
                        } else {
                            emptyDash = false;
                        }
                    }else{
                        line = line.trim();
                        finishedMultiLineKey = false;
                    }

                    while(!line.isEmpty() && !hasErrors()){

                        if(line.startsWith("#")){
                            if(builder.target().isEmpty() || builder.target().isArray()){
                                Json commentJson = new Json();
                                commentJson.add(COMMENT,line.substring(1));
                                builder.target().add(commentJson);
                            }else{
                                if(!builder.target().has(COMMENT)){
                                    builder.target().set(COMMENT,line.substring(1));
                                    builder.target().set(LINE_NUMBER,lineNumber);
                                }else{
                                    //don't see how we would have two comments on one line
                                }
                            }
                            line = "";
                        }else if (line.startsWith(",")) {//end of the inlineList|inlineMap entry
                            if(!inlineStack.isEmpty()){

                                while(!builder.hasAt(INLINE_LIST,1) && !builder.hasAt(INLINE_MAP,1) ){
                                    builder.pop();//close the previous entry
                                }

                                line = line.substring(1).trim();


                            }else{
                                //WTF, error
                                addError(fileName,String.format("encountered , @ {}but not in an inline map nor list",lineNumber));
                            }
                        }else if (line.startsWith("[")){//new inline list

                            inlineStack.push(INLINE_LIST);



                            Json childArray = new Json();
                            Json firstEntry = new Json();
                            childArray.add(firstEntry);
                            if(builder.target().isArray()){
                                Json newEntry = new Json();
                                newEntry.add(CHILD,childArray);
                                builder.target().add(newEntry);
                            }else {
                                builder.target().add(CHILD, childArray);
                            }
                            builder.push(childArray);
                            builder.set(INLINE_LIST,true);
                            builder.push(firstEntry);

                            line = line.substring(1).trim();
                        }else if (line.startsWith("{")){//new inline map

                            inlineStack.push(INLINE_MAP);

                            line = line.substring(1).trim();

                            Json childArray = new Json();
                            Json firstEntry = new Json();

                            childArray.add(firstEntry);

                            if(builder.target().isArray()){
                                Json newEntry = new Json();
                                newEntry.add(CHILD,childArray);
                                builder.target().add(newEntry);

                            }else {
                                builder.target().add(CHILD, childArray);
                            }

                            builder.push(childArray);
                            builder.set(INLINE_MAP,true);
                            builder.push(firstEntry);



                        }else if (line.startsWith("]")){//end inline list
                            line = line.substring(1).trim();

                            if(!INLINE_LIST.equals(inlineStack.peek())){
                                addError(fileName,String.format("Encountered ] but expected } %n%s[%d]: %s%n",fileName,lineNumber,originalLine));
                            }
                            inlineStack.pop();



                            while(!builder.has(INLINE_LIST,false) && builder.size()>1){
                                builder.pop();
                            }
                            builder.pop();//pop the inline list, we just closed it :)




                        }else if (line.startsWith("}")){//end inline map

                            line = line.substring(1).trim();

                            if(!INLINE_MAP.equals(inlineStack.peek())){
                                addError(fileName,String.format("Encountered } but expected ] %n%s[%d]: %s%n",fileName,lineNumber,originalLine));
                            }
                            inlineStack.pop();

                            while(!builder.has(INLINE_MAP,false) && builder.size()>1){
                                builder.pop();
                            }
                            builder.pop();//pop the inline list, we just closed it :)


                        }else{
                            String keyValue = null;
                            int end=0;
                            if(line.startsWith("\"") || line.startsWith("'")) {
                                quoteClose = line.charAt(0);
                                quoted=true;
                                quotedKey=KEY;
                                line = line.substring(1);
                                int i = charactersUntil(line,quoteClose);

                                if(!line.isEmpty() && (i < line.length() || (quoteClose==line.charAt(i-1) && '/'!=line.charAt(i-2))) ){
                                    quoted=false;
                                }
                                end = i;
                                keyValue = line.substring(0,i);
                                if(!quoted && keyValue.endsWith(quoteClose+"")){
                                    keyValue = keyValue.substring(0,keyValue.length()-1);
                                }

                            }else if(keyMatcher.reset(line).find()) {

                                keyValue = keyMatcher.group(KEY);
                                end = keyMatcher.end();

                            }
                            if(keyValue!=null) {
                                if ((keyValue.startsWith("\"") && keyValue.endsWith("\"")) || (keyValue.startsWith("'") && keyValue.endsWith("'"))) {
                                    keyValue = keyValue.substring(1, keyValue.length() - 1);
                                }
                                //start the new entry
                                if (builder.target().isEmpty()) {
                                    Json newEntry = new Json(false);
                                    builder.target().add(newEntry);
                                    builder.push(newEntry);

                                } else if (!builder.target().isArray()) {
                                    while (!builder.target().isArray()) {
                                        builder.pop();
                                    }
                                    Json newEntry = new Json(false);
                                    builder.target().add(newEntry);
                                    builder.push(newEntry);


                                } else {
                                    Json newEntry = new Json(false);
                                    builder.target().add(newEntry);
                                    builder.push(newEntry);
                                }

                                if (builder.target().has(KEY)) {
                                    addError(fileName, String.format("Key already exists %n%s[%d]: %s%n", fileName, lineNumber, originalLine));
                                } else {
                                    builder.target().set(KEY, keyValue);
                                    builder.target().set(LINE_NUMBER, lineNumber);
                                }


                                if (nestedDash) {
                                    builder.target().set(DASHED, true);
                                    nestedDash = false;
                                }
                                line = line.substring(end).trim();
                            }else{
                                //addError(fileName,String.format("Expecting KEY : VALUE %n%s[%d]: %s%n",fileName,lineNumber,originalLine));
                            }
                            if(line.startsWith(":")) {
                                line = line.substring(1).trim();

                                if (line.startsWith("#")) {

                                } else if (line.startsWith("[")) {

                                } else if (line.startsWith("{")){

                                }else{
                                    //TODO this is the value parsing that needs to resume after ending quote
                                    //VALUE_PARSING
                                    int i=0;
                                    boolean stop=false;

                                    boolean inVariable=false;
                                    while(i<line.length() && !stop){
                                        switch (line.charAt(i)){
                                            case ',':
                                                if(!quoted && !inVariable && !inlineStack.isEmpty()){
                                                    stop=true;
                                                    i--;
                                                }
                                                break;
                                            case '$':
                                                if(line.substring(i).startsWith("${{")){
                                                    inVariable=true;
                                                }
                                                break;
                                            case '#':
                                                if(!quoted){
                                                    stop=true;
                                                    i--;
                                                }
                                                break;
                                            case '"':
                                            case '\'':
                                                if(!quoted){
                                                    quoted=true;
                                                    quoteClose=line.charAt(i);
                                                    quotedKey=VALUE;
                                                }else{
                                                    if('\\'==line.charAt(i-1) || quoteClose!=line.charAt(i)){

                                                    }else{
                                                        quoted=false;
                                                        //stop=true; //don't stop, could just be a quite in the line
                                                    }
                                                }
                                                break;
                                            case '}':
                                                if(!quoted ){
                                                    if(inVariable && line.substring(i).startsWith("}}")){
                                                        i++;//skip the next }
                                                    }else if(!inlineStack.isEmpty() && inlineStack.peek().equals(INLINE_MAP)){
                                                        stop=true;
                                                        i--;
                                                    }
                                                }
                                                break;
                                            case ']':
                                                if(!quoted ){
                                                    if(!inlineStack.isEmpty() && inlineStack.peek().equals(INLINE_LIST)){
                                                        stop=true;
                                                        i--;
                                                    }
                                                }
                                                break;

                                        }
                                        i++;
                                    }
                                    if(i>=1){
                                        String lineValue = line.substring(0,i);
                                        if ("|".equals(lineValue.trim()) || ">".equals(lineValue.trim()) && line.endsWith(lineValue)) {
                                            scalar=true;
                                            if(">".endsWith(lineValue.trim())){
                                                foldedScalar=true;
                                            }
                                            lineValue="";
                                        }

                                        builder.target().set(VALUE, builder.target().getString(VALUE,"")+lineValue.replaceAll("\\s+$", ""));
                                        line = line.substring(i).trim();

                                    }
                                }

                            }



                        }

                    }

                    if(!inlineStack.isEmpty()){
                        //addError(fileName,String.format("inline map | list is malformed: %s %n%s[%d]: %s%n",inlineStack.toString(),fileName,lineNumber,originalLine));
                    }
                }//nest match

            }

            if(!inlineStack.isEmpty()){
                addError(fileName,"Unclosed inline structures: "+inlineStack.toString()+"\n"+json.getString(2));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        loaded.put(fileName,json);

    }

    public Json getJson(){
        Json rtrn = new Json();
        loaded.values().forEach(j->rtrn.add(j));
        return rtrn;
    }
    public Json getJson(String key){
        return loaded.containsKey(key) ? loaded.get(key) : new Json();
    }
}
