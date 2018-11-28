package se.kth.anderslm.microbituart;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.UUID;

/**
 * This is where we manage the BLE device and the corresponding services, characteristics et c.
 * BluetoothGattCallback.onCharacteristicChanged receives some text data from the Micro:bit
 * and displays it.
 * <p>
 * NB! In this simple example there is no other way to turn off notifications than to
 * leave the activity (the BluetoothGatt is disconnected and closed in activity.onStop).
 * The code should also be refactored into a set of suitable classes.
 * This is left for the student to implement.
 */
public class DeviceActivity extends AppCompatActivity {

    /**
     * Documentation on UUID:s and such for services on a BBC Micro:bit.
     * Characteristics et c. are found at
     * https://lancaster-university.github.io/microbit-docs/resources/bluetooth/bluetooth_profile.html
     */
    public static final UUID CLIENT_CHARACTERISTIC_CONFIG =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    public static final UUID  UARTSERVICE_SERVICE_UUID =
            UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E");
    public static final UUID UART_TX_CHARACTERISTIC_UUID = // receive data(!)
            UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E");
    public static final UUID UART_RX_CHARACTERISTIC_UUID = // transmit data (!)
            UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E");

    private BluetoothDevice mConnectedDevice = null;
    private BluetoothGatt mBluetoothGatt = null;
    private BluetoothGattService mUartService = null;

    private Handler mHandler; // callbacks executed on background thread (it seems)

    @Override
    protected void onStart() {
        super.onStart();
        mConnectedDevice = ConnectedDevice.getInstance();
        if (mConnectedDevice != null) {
            mDeviceView.setText(mConnectedDevice.toString());
            connect();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mBluetoothGatt != null) {
            mBluetoothGatt.close();
        }
        ConnectedDevice.removeInstance();
        mConnectedDevice = null;
        finish();
    }

    private void connect() {
        if (mConnectedDevice != null) {
            // register call backs for bluetooth gatt
            mBluetoothGatt = mConnectedDevice.connectGatt(this, false, mBtGattCallback);
            Log.i("connect", "connectGatt called");
        }
    }

    /**
     * Callbacks for bluetooth gatt changes/updates
     * The documentation is not clear, but (some of?) the callback methods seems to
     * be executed on a worker thread - hence use a Handler when updating the ui.
     */
    private BluetoothGattCallback mBtGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            Log.i("BluetoothGattCallback", "onConnectionStateChange");

            if (newState == BluetoothGatt.STATE_CONNECTED) {
                mBluetoothGatt = gatt;
                gatt.discoverServices();
                mHandler.post(new Runnable() {
                    public void run() {
                        mDataView.setText(getString(R.string.connected_msg));
                    }
                });
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                // close connection and display info in ui
                mBluetoothGatt = null;
                mHandler.post(new Runnable() {
                    public void run() {
                        mDataView.setText(getString(R.string.disconnected_msg));
                    }
                });
            }
        }

        @Override
        public void onServicesDiscovered(final BluetoothGatt gatt, int status) {
            Log.i("BluetoothGattCallback", "onServicesDiscovered");

            if (status == BluetoothGatt.GATT_SUCCESS) {
                // debug, list services
                BleLogger.logServices(gatt.getServices());

                // Get the UART service
                mUartService = gatt.getService(UARTSERVICE_SERVICE_UUID);
                Log.i("mUartService",
                        mUartService==null? "null" : mUartService.getUuid().toString() );

                if (mUartService != null) {
                    // debug, list characteristics
                    BleLogger.logCharacteristicsForService(mUartService);

                    // Enable indications for UART data
                    // 1. Enable notification/indication on ble peripheral (Micro:bit)
                    BluetoothGattCharacteristic txCharac =
                            mUartService.getCharacteristic(UART_TX_CHARACTERISTIC_UUID);
                    BluetoothGattDescriptor descriptor =
                            txCharac.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG);
                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
                    mBluetoothGatt.writeDescriptor(descriptor);

                    // 2. Enable indications/notification locally (this android device)
                    mBluetoothGatt.setCharacteristicNotification(txCharac, true);
                    Log.i("onServiceDiscovered", "notification/indication set");
                } else {
                    mHandler.post(new Runnable() {
                        public void run() {
                            showToast("Uart-data characteristic not found");
                        }
                    });
                }
            }
        }

        @Override
        public void onDescriptorWrite(final BluetoothGatt gatt, BluetoothGattDescriptor
                descriptor, int status) {
            Log.i("BluetoothGattCallback", "onDescriptorWrite");

            Log.i("onDescriptorWrite", "descriptor " + descriptor.getUuid());
            Log.i("onDescriptorWrite", "status " + status);

            if (CLIENT_CHARACTERISTIC_CONFIG.equals(descriptor.getUuid()) &&
                    status == BluetoothGatt.GATT_SUCCESS) {

                mHandler.post(new Runnable() {
                    public void run() {
                        showToast("Uart-data notifications enabled");
                        mDeviceView.setText(getString(R.string.uart_sensor_info));
                    }
                });
            }
        }

        /**
         * Callback called on characteristic changes, e.g. when a data value is changed.
         * This is where we receive notifications on updates of accelerometer data.
         */
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic
                characteristic) {
            Log.i("BluetoothGattCallback", "onCharacteristicChanged: " + characteristic.toString());

            // TODO: check which service and characteristic caused this call
            BluetoothGattCharacteristic uartTxCharacteristic =
                    mUartService.getCharacteristic(UART_TX_CHARACTERISTIC_UUID);

            // We assume we receive a string from the Micro:bit
            final String msg = uartTxCharacteristic.getStringValue(0);
            mHandler.post(new Runnable() {
                public void run() {
                    showToast(msg);
                    mDataView.setText(msg);
                }
            });
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic
                characteristic, int status) {
            Log.i("BluetoothGattCallback",
                    "onCharacteristicWrite: " + characteristic.getUuid().toString());
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic
                characteristic, int status) {
            Log.i("BluetoothGattCallback",
                    "onCharacteristicRead: " + characteristic.getUuid().toString());
        }
    };

    // Below: gui stuff...
    private TextView mDeviceView;
    private TextView mDataView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device);

        mDeviceView = findViewById(R.id.deviceView);
        mDataView = findViewById(R.id.dataView);

        mHandler = new Handler();
    }

    protected void showToast(String msg) {
        Toast toast = Toast.makeText(this, msg, Toast.LENGTH_SHORT);
        toast.show();
    }
}