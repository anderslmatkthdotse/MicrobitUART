package se.kth.anderslm.microbituart;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

/**
 * An example on how to use the Android BLE API to connect to a BLE device, in this case
 * a BBC Micro:bit, and read some data from UART.
 * The actual manipulation of the sensors services and characteristics is performed in the
 * DeviceActivity class.
 * NB! This example only aims to demonstrate the basic functionality in the Android BLE API.
 * Checks for life cycle connectivity, correct service, nulls et c. is not fully implemented.
 * This code should also be refactored into a set of suitable classes.
 * A state machine to handle the different states, not paired/paired/connected/receiving data/...
 * would be nice.
 * This is left for the student to implement.
 * <p/>
 * More elaborate example on Android BLE:
 * http://developer.android.com/guide/topics/connectivity/bluetooth-le.html
 * Documentation on the BBC Micro:bit:
 * https://lancaster-university.github.io/microbit-docs/ble/profile/
 */
public class MainActivity extends AppCompatActivity {

    public static final String BBC_MICRO_BIT = "BBC micro:bit";

    public static final int REQUEST_ENABLE_BT = 1000;
    public static final int REQUEST_ACCESS_LOCATION = 1001;

    // period for scan, 5000 ms
    private static final long SCAN_PERIOD = 5000;

    private BluetoothAdapter mBluetoothAdapter;
    private boolean mScanning;
    private Handler mHandler;

    private ArrayList<BluetoothDevice> mDeviceList;
    private BTDeviceArrayAdapter mAdapter;
    private TextView mScanInfoView;

    private void initBLE() {
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            showToast("BLE is not supported");
            finish();
        } else {
            showToast("BLE is supported");
            // Access Location is a "dangerous" permission
            int hasAccessLocation = ContextCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_COARSE_LOCATION);
            if (hasAccessLocation != PackageManager.PERMISSION_GRANTED) {
                // ask the user for permission
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                        REQUEST_ACCESS_LOCATION);
                // the callback method onRequestPermissionsResult gets the result of this request
            }
        }

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // turn on BT
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

    }

    // callback for ActivityCompat.requestPermissions
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[],
                                           @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_ACCESS_LOCATION: {
                // if request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // TODO:
                    // ...
                } else {
                    // stop this activity
                    this.finish();
                }
                break;
            }
        }
    }

    // callback for request to turn on BT
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // if user chooses not to enable Bluetooth.
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            finish();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    // device selected, start DeviceActivity (displaying data)
    private void onDeviceSelected(int position) {
        ConnectedDevice.setInstance(mDeviceList.get(position));
        showToast(ConnectedDevice.getInstance().toString());
        Intent intent = new Intent(MainActivity.this, DeviceActivity.class);
        startActivity(intent);
    }

    /*
     * Scan for BLE devices.
     */
    private void scanLeDevice(final boolean enable) {
        if (enable) {
            if (!mScanning) {
                // stop scanning after a pre-defined scan period, SCAN_PERIOD
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (mScanning) {
                            mScanning = false;
                            // stop/startLeScan is deprecated from API 21,
                            // but we support API 18 and up
                            mBluetoothAdapter.stopLeScan(mLeScanCallback);
                            showToast("BLE scan stopped");
                        }
                    }
                }, SCAN_PERIOD);

                mScanning = true;
                mBluetoothAdapter.startLeScan(mLeScanCallback);
                mScanInfoView.setText(getString(R.string.no_devices_msg));
                showToast("BLE scan started");
            }
        } else {
            if (mScanning) {
                mScanning = false;
                mBluetoothAdapter.stopLeScan(mLeScanCallback);
                showToast("BLE scan stopped");
            }
        }
    }

    /**
     * Implementation of the device scan callback.
     * Only adding devices matching name BBC_MICRO_BIT.
     */
    private BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback() {
                @Override
                public void onLeScan(final BluetoothDevice device, int rssi,
                                     byte[] scanRecord) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            String name = device.getName();
                            if (name != null
                                    && name.contains(BBC_MICRO_BIT)
                                    && !mDeviceList.contains(device)) {
                                mDeviceList.add(device);
                                mAdapter.notifyDataSetChanged();
                                String msg =
                                        getString(R.string.found_devices_msg, mDeviceList.size());
                                mScanInfoView.setText(msg);
                            }
                        }
                    });
                }
            };

    /**
     * Below: Manage activity, and hence bluetooth, life cycle,
     * via onCreate, onStart and onStop.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mHandler = new Handler();

        mScanInfoView = findViewById(R.id.scanInfo);

        Button startScanButton = findViewById(R.id.startScanButton);
        startScanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mDeviceList.clear();
                scanLeDevice(true);
            }
        });

        ListView scanListView = findViewById(R.id.scanListView);
        mDeviceList = new ArrayList<>();
        mAdapter = new BTDeviceArrayAdapter(this, mDeviceList);
        scanListView.setAdapter(mAdapter);
        scanListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> arg0, View arg1, int position, long arg3) {
                onDeviceSelected(position);
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        initBLE();
    }

    // TODO ...
    @Override
    protected void onStop() {
        super.onStop();
        // stop scanning
        scanLeDevice(false);
        mDeviceList.clear();
        mAdapter.notifyDataSetChanged();
        // NB !release additional resources
        // ...BleGatt...
    }

    // short messages
    protected void showToast(String msg) {
        Toast toast = Toast.makeText(this, msg, Toast.LENGTH_SHORT);
        toast.show();
    }
}
