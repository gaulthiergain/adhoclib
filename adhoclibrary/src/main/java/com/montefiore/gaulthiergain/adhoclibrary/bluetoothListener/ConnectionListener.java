package com.montefiore.gaulthiergain.adhoclibrary.bluetoothListener;

import android.bluetooth.BluetoothDevice;

import com.montefiore.gaulthiergain.adhoclibrary.bluetooth.BluetoothAdHocDevice;

import java.util.HashMap;

/**
 * Created by gaulthiergain on 8/11/17.
 */

public interface ConnectionListener {
    void onDiscoveryFinished(HashMap<String, BluetoothAdHocDevice> hashMapBluetoothDevice);
    void onDiscoveryStarted();
    void onDeviceFound(BluetoothDevice device);
    void onScanModeChange(int currentMode, int oldMode);
}