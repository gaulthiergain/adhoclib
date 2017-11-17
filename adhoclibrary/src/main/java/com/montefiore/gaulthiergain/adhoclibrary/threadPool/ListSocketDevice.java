package com.montefiore.gaulthiergain.adhoclibrary.threadPool;

import android.bluetooth.BluetoothSocket;
import android.util.Log;

import com.montefiore.gaulthiergain.adhoclibrary.network.BluetoothNetwork;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by gaulthiergain on 10/11/17.
 *
 */
public class ListSocketDevice {

    private static final String TAG = "[AdHoc]";

    private ArrayList<BluetoothSocket> listTasks;
    private ConcurrentHashMap<String, BluetoothNetwork> hashMapNetwork;

    public ListSocketDevice() {
        listTasks = new ArrayList<>();
        hashMapNetwork = new ConcurrentHashMap<>();
    }

    public synchronized BluetoothSocket getSocketDevice() throws InterruptedException {
        Log.d(TAG, "Waiting Socket...");
        while (listTasks.isEmpty()) {
            wait();
        }
        return listTasks.remove(0);
    }


    public synchronized void addSocketClient(BluetoothSocket socket) {

        String key = socket.getRemoteDevice().toString();
        if (!hashMapNetwork.containsKey(key)) {
            hashMapNetwork.put(key, new BluetoothNetwork(socket, false));
        }

        listTasks.add(socket);
        Log.d(TAG, "Add waiting Socket");
        notify();
    }

    public ConcurrentHashMap<String, BluetoothNetwork> getActiveConnexion() {
        return hashMapNetwork;
    }

    public void removeActiveConnexion(BluetoothSocket socket) {
        hashMapNetwork.remove(socket.getRemoteDevice().toString());
    }

}