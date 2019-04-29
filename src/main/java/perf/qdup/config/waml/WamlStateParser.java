package perf.qdup.config.waml;

import perf.yaup.StringUtil;
import perf.yaup.json.Json;

import java.io.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.function.BiConsumer;

public class WamlStateParser {

   public static String getPrefix(String input){
      int idx =0;
      while(idx < input.length() && (input.charAt(idx)==' ' || input.charAt(idx)=='\t' || input.charAt(idx)=='-')){
         idx++;
      }
      return input.substring(0,idx);
   }

   private abstract class State {
      private String prefix;

      public State(String indent){
         this.prefix = indent;
      }
      abstract String parse(String input, int lineNumber, String originalLine);
      public boolean hasPrefix(){return prefix!=null;}
      public String prefix(){return prefix;}
      public void setPrefix(String prefix){
         if(this.prefix==null){
            this.prefix = prefix;
         }
      }
   }
   //determine if the document is  a map
   private class RootState extends State{
      private Json json;
      public RootState(String prefix, Json json){
         super(prefix);
         this.json = json;
      }
      @Override
      String parse(String input, int lineNumber, String originalLine) {
         String inputPrefix = getPrefix(input);
         if(input.trim().startsWith("-") || input.trim().startsWith("[")){
            ArrayState newState = new ArrayState(inputPrefix,json,false);
            pushState(newState);
            if(input.trim().startsWith("[")){
               newState.setInline(true);
               EntryState entryState = new EntryState("",newState);
               pushState(entryState);
               input = input.substring(input.indexOf("[")+"[".length());
            }
         }else {
            MapState newState = new MapState(inputPrefix.replaceAll("-"," "),json,false);
            pushState(newState);
            if(input.trim().startsWith("{")){
               newState.setInline(true);
               KeyState keyState = new KeyState("",newState);
               pushState(keyState);
               input = input.substring(input.indexOf("{")+"{".length());
            }

         }
         return input;
      }
   }
   private class StartState extends RootState{
      public StartState(String prefix,Json json){
         super(prefix,json);
      }
      @Override
      String parse(String input, int lineNumber, String originalLine){
         popState();
         return super.parse(input,lineNumber,originalLine);
      }
   }
   private class MapState extends State{
      private Json json;
      private boolean inline=false;
      public MapState(String indent,Json json,boolean inline){
         super(indent);
         this.json = json;
         this.inline = inline;
      }
      public Json getJson(){return json;}
      public boolean isInline(){return inline;}
      public void setInline(boolean inline){
         this.inline = inline;
      }
      public String parse(String input, int lineNumber, String originalLine){

         if(isInline()){
            if(input.trim().startsWith("}")){
               popState();
               return input.trim().substring("}".length());
            }else if (input.trim().startsWith(",")){
               State newState = new KeyState("",this);
               pushState(newState);
               return input.trim().substring(",".length()).trim();
            }else{
               pushState(new KeyState("",this));
               //System.exit(-1);
            }
         }else{
            String inputPrefix = getPrefix(input);
            /*if(inputPrefix.contains("-")) {//start of a new array entry
               popState();
               //return peekState().parse(input);
            }else*/ if (!hasPrefix()){//this is the first line, set the prefix
               setPrefix(inputPrefix);
               State newState = new KeyState(inputPrefix,this);
               pushState(newState);
               input = input.substring(inputPrefix.length());
            }else if(inputPrefix.equals(prefix())){//if in the same map
               State newState = new KeyState(inputPrefix,this);
               pushState(newState);
               input = input.substring(inputPrefix.length());
               //return peekState().parse(input.substring(inputPrefix.length()));
            }else if (inputPrefix.length() <= prefix().length()){//close the map, = means same length but has a -
               popState();
               //return peekState().parse(input);
            }else{//more indented, assume waml!
               onWamlLine(originalLine,prefix());
               Json newJson = getJson().has("then") ? getJson().getJson("then") : new Json();
               getJson().set("then",newJson);
               State state = newJson.isEmpty() ?
                  new StartState(inputPrefix,newJson) :
                  newJson.isArray() ?
                     new ArrayState(inputPrefix,newJson,false) :
                     new MapState(inputPrefix,newJson,false);
               pushState(state);
            }
         }
         return input;
      }
   }
   private class KeyState extends State{
      private MapState parent;
      public KeyState(String indent,MapState parent){
         super(indent);
         this.parent = parent;
      }

      @Override
      public String parse(String input, int lineNumber, String originalLine) {
         if(parent.isInline()) {
            input = input.trim();//trim off prefix if new line inside inline map
         }
         if(input.trim().startsWith("-")){
            throw new WamlException("map key should not have yaml prefix with a -");
         }

         String key = StringUtil.findNotQuoted(input,":");
         input = input.substring(key.length()).trim();
         if(input.startsWith(":")){
            input = input.substring(":".length()).trim();
         }else{
            throw new WamlException("map key needs to end with :");
         }
         if(input.isEmpty() || input.trim().startsWith("#")){//value must be an object
            Json newJson = new Json();
            parent.getJson().set(key,newJson);
            StartState nextState = new StartState(prefix(),newJson);
            popState();
            pushState(nextState);
         }else {
            State nextState = new ValueState(prefix(), parent, StringUtil.removeQuotes(key));
            popState();//remove self
            pushState(nextState);
         }

         return input;
      }
   }
   private class ValueState extends State{
      private MapState parent;
      private String key;

      private boolean isScalar=false;
      private boolean isFolded=false;
      private int scalarLength=-1;

      public ValueState(String indent,MapState parent,String key){
         super(indent);
         this.parent = parent;
         this.key = key;
      }

      @Override
      public String parse(String input, int lineNumber, String originalLine) {
         if(input.startsWith(":")){
           throw new WamlException("map value should not start with :");
         }

         if(isFolded || isScalar){
            if(scalarLength < 0){
               String inputPrefix = getPrefix(input);
               scalarLength = inputPrefix.length();
            }
            String inputPrefix = getPrefix(input);
            if(inputPrefix.length() >= scalarLength && !inputPrefix.contains("-")){
               String separator="";
               if(parent.getJson().has(key)){
                  if(isFolded){
                     separator=" ";
                  }else{
                     separator=System.lineSeparator();
                  }
               }
               //TODO scalar cut off leading # of spaces or just trim input?
               parent.getJson().set(key,parent.getJson().getString(key,"")+separator+input.substring(scalarLength));
               input = "";
            }else{//no longer setting value
               popState();
               //input = peekState().parse(input);
            }
         }else{
            if(input.trim().startsWith("{")){//we know it's an inline map
               Json newJson = new Json();
               parent.getJson().set(key,newJson);
               MapState newState = new MapState(prefix(),newJson,true);
               newState.setInline(true);
               input = input.trim().substring("{".length()).trim();
               popState();
               pushState(newState);
               pushState(new KeyState("",newState));
               return input;
            } else if (input.trim().startsWith("[")){
               Json newJson = new Json();
               parent.getJson().set(key,newJson);
               ArrayState newState = new ArrayState(prefix(),newJson,true);
               newState.setInline(true);
               input = input.trim().substring("[".length()).trim();
               popState();
               pushState(newState);
               pushState(new EntryState("",newState));
            }else if(input.trim().isEmpty() || input.trim().startsWith("#")){//no value, could be a map or array (next line tells us)
               Json newJson = new Json();
               parent.getJson().set(key,newJson);
               State newState = new StartState(prefix(),newJson);
               popState();
               pushState(newState);
               return input;
            }else{
               if(!parent.getJson().has(key)) {//if this is the first line
                  if (input.trim().startsWith("|")) {
                     isScalar = true;
                     input = input.substring(input.indexOf("|")+"|".length());
                  } else if (input.trim().startsWith(">")) {
                     isFolded = true;
                     input = input.substring(input.indexOf(">")+">".length());
                  }else{
                     if(parent.isInline()){
                        String value = StringUtil.findNotQuoted(input,",}] ");
                        parent.getJson().set(key,StringUtil.removeQuotes(value));
                        popState();
                        input = input.substring(value.length());
                     }else{
                        String value = StringUtil.findNotQuoted(input,"#");
                        parent.getJson().set(key,StringUtil.removeQuotes(value));
                        popState();
                        input="";
                     }
                  }

               }else{//added to support multiple keys in yaml
//                  if(parent.isInline()){
//                     String value = StringUtil.findNotQuoted(input,",}] ");
//                     parent.getJson().add(key,StringUtil.removeQuotes(value));
//                     popState();
//                     input = input.substring(value.length());
//                  }else{
//                     String value = StringUtil.findNotQuoted(input,"#");
//                     parent.getJson().add(key,StringUtil.removeQuotes(value));
//                     popState();
//                     input="";
//                  }
               }

            }

         }
         return input;
      }
   }
   private class ArrayState extends State{
      private Json json;
      private boolean inline=false;
      public ArrayState(String indent,Json json,boolean inline) {
         super(indent);
         this.json = json;
         this.inline = inline;
      }
      public Json getJson(){return json;}
      public boolean isInline(){return inline;}
      public void setInline(boolean inline){
         this.inline = inline;
      }
      @Override
      String parse(String input, int lineNumber, String originalLine) {
         if(isInline()){
            if(input.trim().startsWith("]")){
               popState();
               return input.trim().substring("]".length());
            }else if (input.trim().startsWith(",")){
               State newState = new EntryState("",this);
               pushState(newState);
               return input.trim().substring(",".length()).trim();
            }else{
               System.err.println("MapState inlined saw input="+input.trim());
               System.err.println(getJson().toString(2));
               System.exit(-1);
            }
         }else{
            String inputPrefix = getPrefix(input);
            if(inputPrefix.contains("-")){//start of a new array entry
               if(inputPrefix.equals(prefix())) {
                  State newState = new EntryState(inputPrefix, this);
                  pushState(newState);
                  input = input.substring(inputPrefix.length());
               }else if (inputPrefix.startsWith(prefix())){//child entry
                  State newState = new EntryState(inputPrefix,this);
                  pushState(newState);
                  input = input.substring(inputPrefix.length());
               }else {//does not belong to this
                  popState();
                  //input = peekState().parse(input);
               }
            }else {//not likely from this object
               popState();
            }
         }
         return input;
      }
   }
   private class EntryState extends State{
      private final ArrayState parent;
      //matches ValueState, create superclass?
      private boolean isFolded=false;
      private boolean isScalar=false;
      private int scalarLength=-1;
      private final Object key;
      public EntryState(String indent,ArrayState parent){
         super(indent);
         this.parent = parent;
         this.key = parent.getJson().size();
      }

      @Override
      String parse(String input, int lineNumber, String originalLine) {
         if(isFolded || isScalar) {
            if (scalarLength < 0) {
               String inputPrefix = getPrefix(input);
               scalarLength = inputPrefix.length();
            }
            String inputPrefix = getPrefix(input);
            if (inputPrefix.length() >= scalarLength) {
               String separator = "";
               if (parent.getJson().has(key)) {
                  if (isFolded) {
                     separator = " ";
                  } else {
                     separator = System.lineSeparator();

                  }
               }
               //TODO scalar cut off leading # of spaces or just trim input?
               parent.getJson().set(key, parent.getJson().getString(key, "") + separator + input.substring(scalarLength));
               input = "";
            } else {//no longer setting value
               popState();
               //input = peekState().parse(input);
            }
         }else if(parent.isInline()) {
            if(input.trim().startsWith("]")){
               popState();
               //input = peekState().parse(input);
            } else if(input.trim().startsWith("{")){
               Json newJson = new Json();
               parent.getJson().add(newJson);
               MapState newState = new MapState("",newJson,true);
               popState();
               pushState(newState);
               input = input.substring("{".length());
               pushState(new KeyState("",newState));
               //input = peekState().parse(input.substring("{".length()));
            }else if (input.trim().startsWith("[")){
               Json newJson = new Json();
               parent.getJson().add(newJson);
               ArrayState newState = new ArrayState("",newJson,true);
               popState();
               pushState(newState);
               input = input.substring("[".length());
               pushState(new EntryState("",newState));
               //input = peekState().parse(input.substring("[".length()));
            }else{
               String entry = StringUtil.findNotQuoted(input,",]");
               parent.getJson().add(StringUtil.removeQuotes(entry.trim()));
               popState();
               input = input.substring(entry.length()).trim();
            }
         }else{
            if(input.trim().startsWith("[")){
               Json newJson = new Json();
               parent.getJson().add(newJson);
               ArrayState newState = new ArrayState("",newJson,true );
               newState.setInline(true);
               popState();
               pushState(newState);
               input = input.substring("[".length());
            }else if (input.trim().startsWith("{")) {
               Json newJson = new Json();
               parent.getJson().add(newJson);
               MapState newState = new MapState("", newJson, true);
               newState.setInline(true);
               popState();
               pushState(newState);
               input = input.substring("{".length());
            } else if (input.trim().startsWith("|")) {
               isScalar = true;
               input = input.substring(input.indexOf("|")+"|".length());
            } else if (input.trim().startsWith(">")) {
               isFolded = true;
               input = input.substring(input.indexOf(">")+">".length());
            }else{
               String entry = StringUtil.findNotQuoted(input,":#");
               if(entry.equals(input) || input.charAt(entry.length())=='#'){//entire line is an entry
                  parent.getJson().add(entry);
                  popState();
                  input="";
               }else{//start of a map
                  Json newJson = new Json();
                  parent.getJson().add(newJson);
                  MapState newState = new MapState(prefix().replaceAll("-"," "),newJson,false);
                  popState();
                  pushState(newState);
                  input = input.substring(entry.length());
                  if(input.startsWith(":")){
                     input = input.substring(":".length()).trim();
                  }
                  if(input.trim().isEmpty() || input.trim().startsWith("#")){
                     Json startStateJson = new Json();
                     newState.getJson().set(StringUtil.removeQuotes(entry),startStateJson);
                     pushState(new StartState(newState.prefix(),startStateJson));
                  }else {
                     pushState(new ValueState(newState.prefix(), newState, StringUtil.removeQuotes(entry)));
                  }
               }
            }


         }
         return input;
      }
   }
   private void onWamlLine(String line, String prefix){
      if(wamlConsumer !=null){
         wamlConsumer.accept(line,prefix);
      }
   }
   //private State peekState(){return stateStack.peek();}
   private void pushState(State state){
      changed = true;
      stateStack.push(state);
   }
   private void popState(){
      changed = true;
      State popped = stateStack.pop();
   }
   public boolean changed(){return changed;}
   public void clearChanged(){changed = false;}
   private boolean changed = false;
   private Json rootJson = new Json();
   private RootState rootState = null;
   private Stack<State> stateStack = new Stack<>();
   private Map<String,Json> loadedFiles = new LinkedHashMap<>();
   private BiConsumer<String,String> wamlConsumer = null;
   public WamlStateParser(){
      //init();
   }

   private void setWalmConsumer(BiConsumer<String,String> consumer){
      this.wamlConsumer = consumer;
   }

   private boolean startFile(String name){
      if(loadedFiles.containsKey(name)){
         //error
         return false;
      }
      rootJson = new Json();
      loadedFiles.put(name,rootJson);
      init();
      return true;
   }
   private void init(){
      //only init if starting (null state) or previous document is not empty
      if(rootState == null || !rootState.json.isEmpty()){
         Json json = new Json();
         rootJson.add(json);
         stateStack.clear();
         rootState = new RootState("",json);
         stateStack.push(rootState);
      }

   }

   public Json getLoaded(){
      return rootJson;
   }
   public Set<String> getFileNames(){return loadedFiles.keySet();}
   public Json getLoaded(String name){return loadedFiles.get(name);}
   public String toYaml(String fileName, InputStream stream){
      StringBuilder builder = new StringBuilder();



      return builder.toString();
   }
   public String load(String fileName, String content, boolean buildYaml){
      return load(fileName,new ByteArrayInputStream(content.getBytes()),buildYaml);
   }
   public String load(String fileName, InputStream stream,boolean buildYaml){
      boolean isNew = startFile(fileName);
      StringBuilder builder = buildYaml ? new StringBuilder() : null;
      if(buildYaml){
         setWalmConsumer((line,prefix)->{
            String linePrefix = getPrefix(line);
            builder.append(prefix);
            builder.append("then:");
            builder.append(System.lineSeparator());
         });
      }else{
         setWalmConsumer(null);
      }
      if(!isNew){
         //TODO throw error that file was already loaded?
         return null;
      }
      try(BufferedReader reader = new BufferedReader(new InputStreamReader(stream))){
         int lineNumber = 0;
         String originalLine;
         while((originalLine=reader.readLine())!= null && ++lineNumber > 0){
            if(builder!=null && lineNumber > 1){
               builder.append(System.lineSeparator());
            }
            String line = originalLine;
            String prevLine = "";
            while( line!=null && ( changed() || (!line.equals(prevLine) && !line.isEmpty()) ) ){
               clearChanged();
               prevLine = line;
               if(line.isEmpty() || line.trim().startsWith("#")){
                  line="";
               } else if (line.trim().startsWith("---")) {
                  init();
                  line="";//mark the line handled
               }else{
                  try {
                     line = stateStack.peek().parse(line,lineNumber,originalLine);
                  }catch (WamlException e){
                     if(!rootJson.has("error")){
                        rootJson.set("error",new Json());
                     }
                     rootJson.getJson("error").add(
                        Json.fromJs("{line:\""+originalLine+"\",message:\""+e.getMessage()+"\"}")
                     );
                  }
               }
            }
            if(builder!=null){
               builder.append(originalLine);
            }
         }
      }catch (IOException e){
         e.printStackTrace();
      }
      return builder!=null ? builder.toString() : null;
   }

}
