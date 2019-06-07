package io.hyperfoil.tools.qdup.config.waml;

public class WamlException extends RuntimeException {


   public WamlException(String message){
      super(message);
   }
   public WamlException(Throwable cause){
      super(cause);
   }
   public WamlException(String message,Throwable cause){
      super(message,cause);
   }
}
