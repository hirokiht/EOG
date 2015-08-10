package tw.edu.ncku.simpleeog;

import android.annotation.TargetApi;
import android.app.Fragment;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import com.example.simpleeog.R;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedList;
import java.util.Queue;
import java.util.UUID;


/**
 * A simple {@link Fragment} subclass.
 */
public class BleFragment extends Fragment {
    private static abstract class BtRequest{
        public String action;
        public BtRequest(String action){
            this.action = action;
        }
        abstract boolean execute();
    }
    private static final String ARG_DEVICE = "btDevice";
    private static final UUID ADC_SERVICE_UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb");
    private static final UUID SAMPLE_PERIOD_UUID = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb");
    private static final UUID REALTIME_DATA_UUID = UUID.fromString("0000fff4-0000-1000-8000-00805f9b34fb");
    private static final UUID BUFFERED_DATA_UUID = UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb");
    private static final UUID BYTE_BUFFERED_UUID = UUID.fromString("0000fff3-0000-1000-8000-00805f9b34fb");
    private static final UUID CHAR_CONFIG_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private BluetoothGatt btGatt;
    private BluetoothDevice device;
    private Queue<BtRequest> btRequests = new LinkedList<>();
    private boolean busy = true;
    private BluetoothGattService adcService;
    private BluetoothGattCharacteristic samplingPeriodChar, realtimeDataChar, bufferedDataChar, byteBufferChar;
    private static ArrayAdapter<BluetoothDevice> arrayAdapter;
    private Spinner deviceSpinner;
    private AdcListener adcCallback;

    private short samplingPeriod = -1;

    public final BluetoothGattCallback btGattCb = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            if(newState == BluetoothProfile.STATE_CONNECTED){
                gatt.discoverServices();
            }else if(newState == BluetoothProfile.STATE_DISCONNECTED){
                setDevice(null);
                adcCallback.onConnectionStateChange(newState);
                if(deviceSpinner != null)
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            deviceSpinner.setSelection(0);
                        }
                    });
            }else adcCallback.onConnectionStateChange(newState);
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            adcService = btGatt.getService(ADC_SERVICE_UUID);
            samplingPeriodChar = adcService.getCharacteristic(SAMPLE_PERIOD_UUID);
            realtimeDataChar = adcService.getCharacteristic(REALTIME_DATA_UUID);
            bufferedDataChar = adcService.getCharacteristic(BUFFERED_DATA_UUID);
            byteBufferChar = adcService.getCharacteristic(BYTE_BUFFERED_UUID);
            if(samplingPeriodChar != null)
                btGatt.readCharacteristic(samplingPeriodChar);  //loads samplingPeriod when done
            else pollRequests();
            adcCallback.onConnectionStateChange(BluetoothProfile.STATE_CONNECTED);
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            if(characteristic != samplingPeriodChar)
                return;
            if(characteristic.getValue().length == 1)
                samplingPeriod = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8,0).shortValue();
            else Log.d("onCharacteristicRead", "Sampling Period Characteristics Read Callback byte array length is not 1!");
            pollRequests();
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status){
            super.onCharacteristicWrite(gatt, characteristic, status);
            if(characteristic != samplingPeriodChar)
                return;
            if(characteristic.getValue().length == 1) {
                samplingPeriod = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0).shortValue();
                adcCallback.onSamplingPeriodChanged(samplingPeriod);
            }else Log.d("onCharacteristicWrite","Sampling Period Characteristics Write Callback byte array length is not 1!");
            pollRequests();
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            ByteBuffer buffer = ByteBuffer.wrap(characteristic.getValue());
            super.onCharacteristicChanged(gatt, characteristic);
            if(characteristic == realtimeDataChar && buffer.capacity() == 2)
                adcCallback.onDataReceived(buffer.getShort());
            else if(characteristic == bufferedDataChar && buffer.capacity() == 16) {
                short[] array = new short[8];
                buffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(array);
                adcCallback.onDataBufferReceived(array);
            }else if(characteristic == byteBufferChar && buffer.capacity() == 16)
                adcCallback.onDataBufferReceived(buffer.array());
            else Log.d("onCharacteristicChanged","Invalid characteristic or buffer size received!");
        }

        @Override
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorRead(gatt, descriptor, status);
            pollRequests();
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorWrite(gatt, descriptor, status);
            pollRequests();
        }
    };

    // Container Activity must implement this interface
    public interface AdcListener {
        void onSamplingPeriodChanged(short sampling_period);
        void onDataReceived(short data);
        void onDataBufferReceived(byte[] buffer);
        void onDataBufferReceived(short[] buffer);
        void onConnectionStateChange(int newState);
    }

    public static void discoverDevices() {
        BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
        if(btAdapter == null || !btAdapter.isEnabled())
            return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if(btAdapter.getBluetoothLeScanner() != null)
                btAdapter.getBluetoothLeScanner().startScan(new ScanCallback() {
                    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
                    @Override
                    public void onScanResult(int callbackType, ScanResult result) {
                        super.onScanResult(callbackType, result);
                        if(arrayAdapter != null && arrayAdapter.getPosition(result.getDevice()) < 0)
                            arrayAdapter.add(result.getDevice());
                    }
                });
            else Log.e("discoverDevices","getBluetoothLeScanner() == null");
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            //noinspection deprecation
            btAdapter.startLeScan(new BluetoothAdapter.LeScanCallback() {
                @Override
                public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
                    if(arrayAdapter != null && arrayAdapter.getPosition(device) < 0)
                        arrayAdapter.add(device);
                }
            });
        } else throw new UnsupportedOperationException("Only support Android 4.3 and above!");
    }

    public BleFragment() {
        this.setRetainInstance(true);
    }

    @Override
    public void onSaveInstanceState(Bundle outState){
        super.onSaveInstanceState(outState);
        outState.putParcelable(ARG_DEVICE, device);
    }

    protected void setDeviceSpinner(Spinner spinner){
        deviceSpinner = spinner;
        deviceSpinner.setAdapter(arrayAdapter);
        if(device != null)
            deviceSpinner.setSelection(arrayAdapter.getPosition(device));
        deviceSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if(view == null)
                    return;
                if (device != arrayAdapter.getItem(position))
                    setDevice(arrayAdapter.getItem(position));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
    }

    private void setDevice(BluetoothDevice device){
        if(btGatt != null && device != this.device)
            btGatt.close();
        if(this.device == null) {
            this.device = device;
            btGatt = this.device != null? this.device.connectGatt(getActivity().getApplicationContext(), false, btGattCb) : null;
            return;
        }
        this.device = device;
        btRequests.clear();
        adcService = null;
        samplingPeriodChar = realtimeDataChar = bufferedDataChar = byteBufferChar = null;
        samplingPeriod = -1;
        busy = true;
        btGatt = this.device != null? this.device.connectGatt(getActivity().getApplicationContext(), false, btGattCb) : null;
        if(btGatt == null)
            adcCallback.onConnectionStateChange(BluetoothProfile.STATE_DISCONNECTED);
    }

    public void setAdcCallback(AdcListener cb){
        adcCallback = cb;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(savedInstanceState != null && savedInstanceState.containsKey(ARG_DEVICE))
            setDevice((BluetoothDevice) savedInstanceState.getParcelable(ARG_DEVICE));
        if(adcCallback == null)
            try {
                adcCallback = (AdcListener) getActivity();
            } catch (ClassCastException e) {
                throw new ClassCastException(getActivity().toString() + " must implement AdcListener");
            }
        if(arrayAdapter == null) {
            arrayAdapter = new ArrayAdapter<BluetoothDevice>(getActivity(), R.layout.support_simple_spinner_dropdown_item) {
                @Override
                public View getDropDownView(int position, View convertView, ViewGroup parent) {
                    BluetoothDevice device = getItem(position);
                    TextView textView = device == null ? (TextView) getActivity().getLayoutInflater().inflate(R.layout.support_simple_spinner_dropdown_item, parent, false) :
                            (TextView) super.getDropDownView(position, convertView, parent);
                    textView.setText(device == null ? getString(R.string.none_device) : device.getName() == null ? device.getAddress() :
                            device.getName() + " (" + device.getAddress() + ")");
                    textView.setTextAppearance(getContext(), R.style.TextAppearance_AppCompat_Widget_ActionBar_Subtitle_Inverse);
                    return textView;
                }

                @Override
                public View getView(int position, View convertView, ViewGroup parent) {
                    BluetoothDevice device = getItem(position);
                    TextView textView = device == null ? new TextView(getContext()) :
                            (TextView) super.getView(position, convertView, parent);
                    textView.setText(device == null ? getString(R.string.select_device) :
                            device.getName() == null ? device.getAddress() :
                                    device.getName() + " (" + device.getAddress() + ")");
                    textView.setTextAppearance(getContext(), R.style.TextAppearance_AppCompat_Widget_ActionBar_Subtitle_Inverse);
                    return textView;
                }
            };
            arrayAdapter.add(null);
        }
    }

    public BluetoothDevice getDevice(){
        return device;
    }


    public short getSamplingPeriod(){
        return samplingPeriod;
    }

    private boolean readSamplingPeriod(){
        if(adcService == null)
            throw new UnsupportedOperationException("Device doesn't provide ADC Service!");
        if(samplingPeriodChar == null)
            throw new UnsupportedOperationException("Device doesn't support Sampling Period Operations!");
        BtRequest request = new BtRequest("readSamplingPeriod") {
            @Override
            boolean execute() {
                busy = true;
                return btGatt.readCharacteristic(samplingPeriodChar);
            }
        };
        return busy? btRequests.offer(request) : request.execute();
    }

    public boolean saveSamplingPeriod(final short sampling_period){
        if(adcService == null || samplingPeriodChar == null)
            throw new UnsupportedOperationException("Device doesn't support Sampling Period Operations!");
        BtRequest request = new BtRequest("saveSamplingPeriod") {
            @Override
            boolean execute() {
                busy = true;
                samplingPeriodChar.setValue(sampling_period, BluetoothGattCharacteristic.FORMAT_UINT8, 0);
                return btGatt.writeCharacteristic(samplingPeriodChar);
            }
        };
        return busy? btRequests.offer(request) : request.execute();
    }

    public boolean setRealtimeAdcNotification(final boolean enable){
        if(adcService == null || realtimeDataChar == null)
            throw new UnsupportedOperationException("Device doesn't support Realtime ADC!");
        final BluetoothGattDescriptor descriptor = realtimeDataChar.getDescriptor(CHAR_CONFIG_UUID);
        if(descriptor == null)
            throw new UnsupportedOperationException("Realtime ADC doesn't support notification!");
        BtRequest request = new BtRequest("setRealtimeAdcNotification") {
            @Override
            boolean execute() {
                busy = true;
                btGatt.setCharacteristicNotification(realtimeDataChar,enable);
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                return btGatt.writeDescriptor(descriptor);
            }
        };
        return busy? btRequests.offer(request) : request.execute();
    }

    public boolean setBuffered12bitAdcNotification(final boolean enable){
        if(adcService == null || bufferedDataChar == null)
            throw new UnsupportedOperationException("Device doesn't support Buffered 12bit ADC!");
        final BluetoothGattDescriptor descriptor = bufferedDataChar.getDescriptor(CHAR_CONFIG_UUID);
        if(descriptor == null)
            throw new UnsupportedOperationException("Buffered 12bit ADC doesn't support notification!");
        BtRequest request = new BtRequest("setBuffered12bitAdcNotification") {
            @Override
            boolean execute() {
                busy = true;
                btGatt.setCharacteristicNotification(bufferedDataChar,enable);
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                return btGatt.writeDescriptor(descriptor);
            }
        };
        return busy? btRequests.offer(request) : request.execute();
    }

    public boolean setBuffered8bitAdcNotification(final boolean enable){
        if(adcService == null || byteBufferChar == null)
            throw new UnsupportedOperationException("Device doesn't support Buffered 8bit ADC!");
        final BluetoothGattDescriptor descriptor = byteBufferChar.getDescriptor(CHAR_CONFIG_UUID);
        if(descriptor == null)
            throw new UnsupportedOperationException("Buffered 8bit ADC doesn't support notification!");
        BtRequest request = new BtRequest("setBuffered8bitAdcNotification") {
            @Override
            boolean execute() {
                busy = true;
                btGatt.setCharacteristicNotification(byteBufferChar,enable);
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                return btGatt.writeDescriptor(descriptor);
            }
        };
        return busy? btRequests.offer(request) : request.execute();
    }

    private void pollRequests(){
        if(btRequests.isEmpty())
            busy = false;
        else btRequests.poll().execute();
    }

}
