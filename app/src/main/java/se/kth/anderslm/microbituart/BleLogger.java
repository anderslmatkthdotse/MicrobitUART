package se.kth.anderslm.microbituart;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.util.Log;

import java.util.List;

/**
 * For debugging purposes.
 */
class BleLogger {

    private BleLogger() {
    }

    static void logServices(List<BluetoothGattService> services) {
        for (BluetoothGattService service : services) {
            String uuid = service.getUuid().toString();
            Log.i("service", uuid);
        }
    }

    static void logCharacteristicsForService(BluetoothGattService service) {
        List<BluetoothGattCharacteristic> characteristics =
                service.getCharacteristics();
        for (BluetoothGattCharacteristic charac : characteristics) {
            Log.i("characteristic", charac.getUuid().toString());
            Log.i("properties", "" + charac.getProperties());
            if(hasProperty(charac, BluetoothGattCharacteristic.PROPERTY_INDICATE)) {
                Log.i("indicate", "yes");
            }
            else {
                Log.i("indicate", "no");
            }
        }
    }

    // debug
    static private boolean hasProperty(BluetoothGattCharacteristic chara, int property) {
        int prop = chara.getProperties() & property;
        return prop == property;
    }
}
