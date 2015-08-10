package tw.edu.ncku;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

public class DataLogger {
    private String filename;
    private File dir;
    private static HashMap<String,BufferedWriter> dataWriter = new HashMap<>();

    public DataLogger(File dir){
        this("",dir);
    }

    public DataLogger(String name, File directory){
        filename = name;
        dir = directory;
    }

    public void setFilename(String name){
        filename = name;
    }

    public void logData( Object data) throws IOException{
        logData(dir, filename, data);
    }

    public void logPrefixedData( String prefix, Object data) throws IOException{
        logData(dir, prefix + filename, data);
    }

    public void logPostfixedData( String postfix, Object data) throws IOException{
        logData(dir, filename + postfix, data);
    }

    public static void logData(File dir, String key, Object data) throws IOException{
        final File fp = new File(dir,key+".csv");
        BufferedWriter writer = dataWriter.containsKey(key)? dataWriter.get(key) : new BufferedWriter(new FileWriter(fp,true));
        if(!dataWriter.containsKey(key))
            dataWriter.put(key,writer);
        else writer.write(',');
        String csv = data instanceof float[]? Arrays.toString((float[])data) :
                data instanceof double[]? Arrays.toString((double[]) data) :
                data instanceof long[]? Arrays.toString((long[]) data) :
                data instanceof int[]? Arrays.toString((int[]) data) :
                data instanceof short[]? Arrays.toString((short[]) data) :
                data instanceof byte[]? Arrays.toString((byte[])data) :
                data instanceof char[]? Arrays.toString((char[])data) :
                data instanceof boolean[]? Arrays.toString((boolean[])data) :
                data instanceof Object[]? Arrays.toString((Object[]) data) : null;
        if(csv == null)
            throw new InvalidObjectException("Object provided is not an array!");
        writer.write(csv.substring(1, csv.length() - 1));
    }

    public void flushPrefixedData(String prefix) throws IOException{
        flushData(prefix + filename);
    }

    public void flushPostfixedData(String postfix) throws IOException{
        flushData(filename + postfix);
    }

    public static void flushData(String filename) throws IOException{
        if(!dataWriter.containsKey(filename))
            throw new FileNotFoundException(filename+" is not found in dataLogger!");
        dataWriter.get(filename).newLine();
        dataWriter.get(filename).flush();
    }

    public static void flush() throws IOException{
        ArrayList<String> keys = new ArrayList<>();
        for(String key : dataWriter.keySet()){
            try{
                dataWriter.get(key).newLine();
                dataWriter.get(key).flush();
            }catch (IOException ioe){
                keys.add(key+": "+ioe.getMessage());
            }
        }
        if(keys.isEmpty())
            return;
        throw new IOException("Flush failed for the following files:\n"+Arrays.toString(keys.toArray()));
    }
}
