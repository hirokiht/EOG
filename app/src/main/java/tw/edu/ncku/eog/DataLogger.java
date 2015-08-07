package tw.edu.ncku.eog;

import android.content.Context;
import android.support.v4.util.SimpleArrayMap;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.util.ArrayList;
import java.util.Arrays;

public class DataLogger {
    private String filename;
    private File dir;
    private static SimpleArrayMap<String,BufferedWriter> dataWriter = new SimpleArrayMap<>();

    public DataLogger(Context ctx){
        this("",ctx);
    }

    public DataLogger(File dir){
        this("",dir);
    }

    public DataLogger(String name, Context ctx){
        this(name, ctx.getExternalFilesDir(null));
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
        for(int i = 0 ; i < dataWriter.size() ; i++)
            try {
                dataWriter.valueAt(i).newLine();
                dataWriter.valueAt(i).flush();
            }catch(IOException ioe){
                keys.add(dataWriter.keyAt(i) + ": " + ioe.getMessage());
            }
        if(keys.isEmpty())
            return;
        throw new IOException("Flush failed for the following files:\n"+Arrays.toString(keys.toArray()));
    }
}
