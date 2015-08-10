package tw.edu.ncku.simpleeog;

import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothProfile;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.widget.Spinner;

import com.example.simpleeog.R;

import org.jtransforms.fft.FloatFFT_1D;

import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.util.Arrays;

import tw.edu.ncku.DataLogger;
import tw.edu.ncku.QuickSelect;


public class SimpleEogActivity extends AppCompatActivity implements BleFragment.AdcListener, TimerFragment.OnTimerListener{
    private final static int REQUEST_ENABLE_BT = 1;
    private final static int BUFFER_SIZE = 128;
    private static DataLogger dataLogger;
    private FragmentManager fragmentManager;
    private static BleFragment bleFragment = new BleFragment();
    private TimerFragment timerFragment;
    private static GraphFragment graphFragment;
    private static ActivityState state = ActivityState.ENABLE_BLE;
    private static short sampling_period = 8;
    private static int medianFilterWindowSize = 0;  //window size in ms for median filter
    private static float[] windowFunction = new float[BUFFER_SIZE]; //Window Function for Raw Data (Hamming Window is used)
    private static Buffer dataBuffer;

    @Override
    public void onTimerStateChange(boolean started, boolean finished) {
        state = finished? ActivityState.COMPLETE : started? ActivityState.BEGIN_TEST : ActivityState.READY;
        checkState();
        if(!started)
            graphFragment.resetData();
        else dataLogger.setFilename(String.valueOf(System.currentTimeMillis())+"-");
    }

    private enum ActivityState{
        NO_BLE, ENABLE_BLE, SELECT_DEVICE, READY, BEGIN_TEST, COMPLETE
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final float alpha = 0.54f, beta = 0.46f;
        for(int i = 0 ; i < BUFFER_SIZE ; i++)
            windowFunction[i] = alpha-beta*(float)Math.cos(2*Math.PI*i/(BUFFER_SIZE-1));
        fragmentManager = getFragmentManager();
        if(dataLogger == null)
            dataLogger = new DataLogger(getApplicationContext().getExternalFilesDir(null));
        setContentView(R.layout.activity_main);
        if(fragmentManager.findFragmentByTag("bleFragment") == null)
            fragmentManager.beginTransaction().add(bleFragment,"bleFragment").commit();
        timerFragment = (TimerFragment) fragmentManager.findFragmentById(R.id.fragment);
        graphFragment = (GraphFragment) fragmentManager.findFragmentById(R.id.graphFragment);
        if(savedInstanceState != null && savedInstanceState.containsKey("state")) {
            state = (ActivityState) savedInstanceState.getSerializable("state");
            sampling_period = bleFragment.getSamplingPeriod();
        }
        graphFragment.setSamplingPeriod(sampling_period/1000f); //convert into second
        checkState();
        if(savedInstanceState == null && state == ActivityState.ENABLE_BLE)  //request bt if it is the first time starting this app
            startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP), REQUEST_ENABLE_BT);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        finishActivity(REQUEST_ENABLE_BT);
        outState.putSerializable("state", state);
        try {
            dataLogger.flushPostfixedData("RawData");
        }catch(IOException ioe){
            Log.d("onSaveInstanceState","IOException: "+ioe.getMessage());
        }
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onActivityResult(int requestCode, final int resultCode, Intent data){
        if(requestCode == REQUEST_ENABLE_BT){
            if(checkState() == ActivityState.ENABLE_BLE){    //wait for a while for BT to be enabled
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if(!isChangingConfigurations() && !isDestroyed() && !isFinishing() &&
                                checkState() == ActivityState.ENABLE_BLE ) {    //the wait is over
                            state = ActivityState.NO_BLE;
                            checkState();
                        }
                    }
                }, 2000);
            }else if (fragmentManager.findFragmentByTag("waitForBt") != null)
                ((DialogFragment)fragmentManager.findFragmentByTag("waitForBt")).dismiss();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        Spinner deviceSelector = (Spinner) menu.findItem(R.id.deviceSpinner).getActionView();
        bleFragment.setDeviceSpinner(deviceSelector);
        return true;
    }

    @Override
    public void onSamplingPeriodChanged(short sampling_period) {
        if(sampling_period == SimpleEogActivity.sampling_period || state == ActivityState.SELECT_DEVICE || state == ActivityState.ENABLE_BLE)
            return;
        bleFragment.saveSamplingPeriod(SimpleEogActivity.sampling_period);
    }

    @Override
    public void onDataReceived(short data) {
        if(dataBuffer == null || !(dataBuffer instanceof ShortBuffer))
            return;
        ((ShortBuffer)dataBuffer).put(data);
        if(!dataBuffer.hasRemaining())
            processBuffer((ShortBuffer)dataBuffer);
        try {
            dataLogger.logPostfixedData("RawData", new short[]{data});
        }catch(IOException ioe){
            Log.d("onDataBufferReceived","IOException: "+ioe.getMessage());
        }
    }

    @Override
    public void onDataBufferReceived(byte[] buffer) {
        if(dataBuffer == null || !(dataBuffer instanceof ByteBuffer))
            return;
        ((ByteBuffer)dataBuffer).put(buffer);
        if(!dataBuffer.hasRemaining())
            processBuffer((ByteBuffer)dataBuffer);
        try {
            dataLogger.logPostfixedData("RawData", buffer);
        }catch(IOException ioe){
            Log.d("onDataBufferReceived","IOException: "+ioe.getMessage());
        }
    }

    @Override
    public void onDataBufferReceived(short[] buffer) {
        if(dataBuffer == null || !(dataBuffer instanceof ShortBuffer))
            return;
        ((ShortBuffer)dataBuffer).put(buffer);
        if(!dataBuffer.hasRemaining())
            processBuffer((ShortBuffer)dataBuffer);
        try {
            dataLogger.logPostfixedData("RawData", buffer);
        }catch(IOException ioe){
            Log.d("onDataBufferReceived","IOException: "+ioe.getMessage());
        }
    }

    @Override
    public void onConnectionStateChange(int newState) {
        state = newState == BluetoothProfile.STATE_CONNECTED? ActivityState.READY : ActivityState.SELECT_DEVICE;
        checkState();
    }

    private ActivityState checkState(){
        switch(state){
            case NO_BLE:
                if (fragmentManager.findFragmentByTag("waitForBt") != null)
                    ((DialogFragment)fragmentManager.findFragmentByTag("waitForBt")).dismiss();
                if(fragmentManager.findFragmentByTag("NoBtAlertDialog") == null)
                    new DialogFragment(){
                        @Override
                        public Dialog onCreateDialog(Bundle savedInstanceState) {
                            this.setRetainInstance(true);
                            AlertDialog dialog = new AlertDialog(getActivity()){
                                @Override
                                public void dismiss(){
                                    this.setDismissMessage(null);
                                    super.dismiss();
                                }

                                @Override
                                public void cancel(){
                                    super.cancel();
                                    getActivity().finish();
                                }
                            };
                            dialog.setTitle(R.string.need_bt);
                            dialog.setMessage(getString(R.string.bt_not_found));
                            dialog.setButton(DialogInterface.BUTTON_NEUTRAL, "OK", new DialogInterface.OnClickListener(){
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.cancel();
                                }
                            });
                            return dialog;
                        }
                    }.show(fragmentManager, "NoBtAlertDialog");
                break;
            case ENABLE_BLE:
                timerFragment.reset();
                if(BluetoothAdapter.getDefaultAdapter() == null){
                    state = ActivityState.NO_BLE;
                    checkState();
                }else if(!BluetoothAdapter.getDefaultAdapter().isEnabled()){
                    if(fragmentManager.findFragmentByTag("waitForBt") == null)
                        new DialogFragment() {
                            @Override
                            public void onDestroyView() {   //Fix issue: https://code.google.com/p/android/issues/detail?id=17423
                                if (getDialog() != null && getRetainInstance())
                                    getDialog().setDismissMessage(null);
                                super.onDestroyView();
                            }

                            @Override
                            public Dialog onCreateDialog(Bundle savedInstanceState) {
                                this.setRetainInstance(true);
                                ProgressDialog dialog = new ProgressDialog(getActivity()){
                                    @Override
                                    public void dismiss() {
                                        this.setDismissMessage(null);
                                        super.dismiss();
                                    }

                                    @Override
                                    public void cancel(){
                                        super.cancel();
                                        state = ActivityState.NO_BLE;
                                        checkState();
                                    }
                                };
                                dialog.setTitle(R.string.bt_not_enabled);
                                dialog.setMessage(getString(R.string.wait_for_bt_enable));
                                dialog.setIndeterminate(true);
                                dialog.setCancelable(true);
                                return dialog;
                            }
                        }.show(fragmentManager, "waitForBt");
                }else {
                    state = ActivityState.SELECT_DEVICE;
                    return checkState();
                }
                break;
            case SELECT_DEVICE:
                timerFragment.reset();
                if(!BluetoothAdapter.getDefaultAdapter().isEnabled()){
                    state = ActivityState.ENABLE_BLE;
                    return checkState();
                }
                if (fragmentManager.findFragmentByTag("waitForBt") != null)
                    ((DialogFragment)fragmentManager.findFragmentByTag("waitForBt")).dismiss();
                if(bleFragment != null && bleFragment.getDevice() != null) {
                    state = ActivityState.READY;
                    return checkState();
                }
                BleFragment.discoverDevices();
                break;
            case READY:
                if(!BluetoothAdapter.getDefaultAdapter().isEnabled() || bleFragment == null || bleFragment.getDevice() == null){
                    state = BluetoothAdapter.getDefaultAdapter().isEnabled()? ActivityState.SELECT_DEVICE : ActivityState.ENABLE_BLE;
                    return checkState();
                }
                if(dataBuffer == null)
                    (dataBuffer = ShortBuffer.allocate(BUFFER_SIZE)).limit(BUFFER_SIZE);
                else dataBuffer.clear();
                if(bleFragment.getSamplingPeriod() != sampling_period)
                    bleFragment.saveSamplingPeriod(sampling_period);
                timerFragment.reset(true);
                break;
            case BEGIN_TEST:
                if(!BluetoothAdapter.getDefaultAdapter().isEnabled() || bleFragment == null || bleFragment.getDevice() == null){
                    state = BluetoothAdapter.getDefaultAdapter().isEnabled()? ActivityState.SELECT_DEVICE : ActivityState.ENABLE_BLE;
                    return checkState();
                }
                bleFragment.setBuffered12bitAdcNotification(true);
                break;
            case COMPLETE:
                if(!BluetoothAdapter.getDefaultAdapter().isEnabled() || bleFragment == null || bleFragment.getDevice() == null){
                    state = BluetoothAdapter.getDefaultAdapter().isEnabled()? ActivityState.SELECT_DEVICE : ActivityState.ENABLE_BLE;
                    return checkState();
                }
                break;
        }
        return state;
    }

    private static void processBuffer(ByteBuffer dataBuffer){
        float[] data = new float[dataBuffer.capacity()];
        for(int i = 0 ; i < data.length ; i++)
            data[i] = (0xFF&dataBuffer.get(i))/256f;
        processBuffer(data);
        byte[] buffer = new byte[dataBuffer.capacity()/2];
        dataBuffer.position(buffer.length); //go to middle
        dataBuffer.get(buffer);             //get from middle to end
        dataBuffer.clear();                 //then shift the data start from middle to front
        dataBuffer.put(buffer);
    }

    private static void processBuffer(ShortBuffer dataBuffer){
        float[] data = new float[dataBuffer.capacity()];
        for(int i = 0 ; i < data.length ; i++)
            data[i] = dataBuffer.get(i)/ 4096f;
        processBuffer(data);
        short[] buffer = new short[dataBuffer.capacity()/2];
        dataBuffer.position(buffer.length); //go to middle
        dataBuffer.get(buffer);             //get from middle to end
        dataBuffer.clear();                 //then shift the data start from middle to front
        dataBuffer.put(buffer);
    }

    private static void processBuffer(float[] data){
        if(state != ActivityState.BEGIN_TEST)
            bleFragment.setBuffered12bitAdcNotification(false);
        Log.d("processBuffer", "start processing data...");
        if(medianFilterWindowSize > 0) {    //medianFilter is enabled and set
            int size = medianFilterWindowSize / sampling_period;
            for (int i = size / 2; i < data.length - size / 2; i++) {
                float[] window = Arrays.copyOfRange(data, i - size / 2, i + size / 2);
                data[i] = QuickSelect.getMedian(window);
            }
        }
        for(int i = 0 ; i < data.length ; i++)
            data[i] *= windowFunction[i];
        FloatFFT_1D fft = new FloatFFT_1D(data.length);
        fft.realForward(data);
        int fftSize = 32*BUFFER_SIZE*sampling_period/1000;  //only take 1-32Hz data for Graphing
        float[] fftResult = new float[fftSize];   //freq500/period
        for(int i = 2 ; i < fftResult.length*2 ; i+=2)
            fftResult[i>>1] = data[i] * data[i] + data[i + 1] * data[i + 1];
        try {
            dataLogger.logPostfixedData("FFT", fftResult);
            dataLogger.flushPostfixedData("FFT");
        }catch(IOException ioe){
            Log.d("onDataBufferReceived","IOException: "+ioe.getMessage());
        }
        graphFragment.resetData(fftResult);
    }
}
