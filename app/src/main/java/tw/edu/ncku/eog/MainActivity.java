package tw.edu.ncku.eog;

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

import org.jtransforms.fft.FloatFFT_1D;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;


public class MainActivity extends AppCompatActivity implements BleFragment.AdcListener, TimerFragment.OnTimerListener{
    private final static int REQUEST_ENABLE_BT = 1;
    private final static int BUFFER_SIZE = 128;
    private FragmentManager fragmentManager;
    private static BleFragment bleFragment = new BleFragment();
    private TimerFragment timerFragment;
    private static GraphFragment graphFragment;
    private ActivityState state = ActivityState.ENABLE_BLE;
    private short sampling_period = 8;
    private Buffer dataBuffer;

    @Override
    public void onTimerStateChange(boolean started, boolean finished) {
        state = finished? ActivityState.COMPLETE : started? ActivityState.BEGIN_TEST : ActivityState.READY;
        checkState();
        if(finished || !started)
            graphFragment.resetData();
    }

    private enum ActivityState{
        NO_BLE, ENABLE_BLE, SELECT_DEVICE, READY, BEGIN_TEST, COMPLETE
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        fragmentManager = getFragmentManager();
        setContentView(R.layout.activity_main);
        if(fragmentManager.findFragmentByTag("bleFragment") == null)
            fragmentManager.beginTransaction().add(bleFragment,"bleFragment").commit();
        timerFragment = (TimerFragment) fragmentManager.findFragmentById(R.id.fragment);
        graphFragment = (GraphFragment) fragmentManager.findFragmentById(R.id.graphFragment);
        if(savedInstanceState != null && savedInstanceState.containsKey("state")) {
            state = (ActivityState) savedInstanceState.getSerializable("state");
            sampling_period = bleFragment.getSamplingPeriod();
        }
        checkState();
        if(savedInstanceState == null && state == ActivityState.ENABLE_BLE)  //request bt if it is the first time starting this app
            startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP), REQUEST_ENABLE_BT);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        finishActivity(REQUEST_ENABLE_BT);
        outState.putSerializable("state", state);
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
        if(checkState() == ActivityState.READY && sampling_period != this.sampling_period)
            bleFragment.saveSamplingPeriod(this.sampling_period);
    }

    @Override
    public void onDataReceived(short data) {
        if(dataBuffer == null || !(dataBuffer instanceof ShortBuffer))
            return;
        ((ShortBuffer)dataBuffer).put(data);
        if(!dataBuffer.hasRemaining())
            processBuffer();
    }

    @Override
    public void onDataBufferReceived(byte[] buffer) {
        if(dataBuffer == null || !(dataBuffer instanceof ByteBuffer))
            return;
        ((ByteBuffer)dataBuffer).put(buffer);
        if(!dataBuffer.hasRemaining())
            processBuffer();
    }

    @Override
    public void onDataBufferReceived(short[] buffer) {
        if(dataBuffer == null || !(dataBuffer instanceof ShortBuffer))
            return;
        ((ShortBuffer)dataBuffer).put(buffer);
        if(!dataBuffer.hasRemaining())
            processBuffer();
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
                BleFragment.discoverDevices(this);
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
                bleFragment.setBuffered12bitAdcNotification(false);
                break;
            case BEGIN_TEST:
                if(!BluetoothAdapter.getDefaultAdapter().isEnabled() || bleFragment == null || bleFragment.getDevice() == null){
                    state = BluetoothAdapter.getDefaultAdapter().isEnabled()? ActivityState.SELECT_DEVICE : ActivityState.ENABLE_BLE;
                    return checkState();
                }
                bleFragment.setBuffered12bitAdcNotification(true);
                timerFragment.start();
                break;
            case COMPLETE:
                if(!BluetoothAdapter.getDefaultAdapter().isEnabled() || bleFragment == null || bleFragment.getDevice() == null){
                    state = BluetoothAdapter.getDefaultAdapter().isEnabled()? ActivityState.SELECT_DEVICE : ActivityState.ENABLE_BLE;
                    return checkState();
                }
                bleFragment.setBuffered12bitAdcNotification(false);
                break;
        }
        return state;
    }

    private void processBuffer(){
        float[] data = new float[BUFFER_SIZE];
        if(dataBuffer instanceof  ShortBuffer)
            for(int i = 0 ; i < BUFFER_SIZE ; i++)
                data[i] = ((ShortBuffer)dataBuffer).get(i)/4096f;
        else if(dataBuffer instanceof ByteBuffer)
            for(int i = 0 ; i < BUFFER_SIZE ; i++)
                data[i] = (0xFF&((ByteBuffer)dataBuffer).get(i))/256f;
        else throw new UnsupportedOperationException("Invalid buffer found when processing buffer!");
        dataBuffer.clear();
        Log.d("processBuffer", "start processing data...");
        //TODO: apply median/mean filter to remove spikes.
        FloatFFT_1D fft = new FloatFFT_1D(data.length);
        fft.realForward(data);
        float[] fftResult = new float[BUFFER_SIZE/2];
        for(int i = 0 ; i < data.length ; i+=2)
            fftResult[i>>1] = data[i] * data[i] + data[i + 1] * data[i + 1];
        graphFragment.resetData(fftResult);
    }
}
