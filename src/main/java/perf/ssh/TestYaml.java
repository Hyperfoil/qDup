package perf.ssh;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.events.Event;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

public class TestYaml {

    public static void main(String[] args) {
        String path = ClassLoader.getSystemResource("specjms.yaml").getPath();

        Yaml yaml = new Yaml();

        try(FileReader reader = new FileReader(path)){
            //yaml.loadAll(reader);

            try{
                for(Event e : yaml.parse(reader)){
                    System.out.println(e.toString());
                }
            }catch(Exception e){
                System.out.println(e.getMessage());
                e.printStackTrace();
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
