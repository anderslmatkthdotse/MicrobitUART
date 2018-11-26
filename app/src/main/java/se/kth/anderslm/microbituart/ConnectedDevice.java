package se.kth.anderslm.microbituart;

import android.bluetooth.BluetoothDevice;

/**
 * An ugly hack to administrate the selected Bluetooth device
 * between activities.
 */
class ConnectedDevice {

    private static BluetoothDevice theDevice = null;
    private static final Object lock = new Object();

    private ConnectedDevice() {
    }

    static BluetoothDevice getInstance() {
        synchronized (lock) {
            return theDevice;
        }
    }

    static void setInstance(BluetoothDevice newDevice) {
        synchronized (lock) {
            theDevice = newDevice;
        }
    }

    static void removeInstance() {
        synchronized(lock) {
            theDevice = null;
        }
    }
}
