package com.montefiore.gaulthiergain.adhoclibrary.service;

import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.util.Log;

import com.montefiore.gaulthiergain.adhoclibrary.network.AdHocSocketWifi;
import com.montefiore.gaulthiergain.adhoclibrary.network.NetworkObject;
import com.montefiore.gaulthiergain.adhoclibrary.util.MessageAdHoc;

import java.io.IOException;

/**
 * Created by gaulthiergain on 8/12/17.
 */

class ListenServiceThread extends Thread {
    private static final String TAG = "[AdHoc][ListenService]";
    private final boolean v;
    private final NetworkObject network;
    private final Handler handler;

    ListenServiceThread(boolean verbose, NetworkObject network, Handler handler) {
        this.v = verbose;
        this.network = network;
        this.handler = handler;
    }

    @Override
    public void run() {
        if (v) Log.d(TAG, "start Listening ...");
        MessageAdHoc message;
        while (true) {
            // Read response
            try {
                if (v) Log.d(TAG, "Waiting response from server ...");

                // Get response MessageAdHoc
                message = (MessageAdHoc) network.receiveObjectStream();
                if (v) Log.d(TAG, "Response: " + message);
                handler.obtainMessage(Service.MESSAGE_READ, message).sendToTarget();
            } catch (IOException e) {
                if (network.getISocket() != null) {

                    if (network.getISocket() instanceof AdHocSocketWifi) {
                        // Notify handler
                        String handleConnectionAborted[] = new String[2];
                        // Get remote device name
                        handleConnectionAborted[0] = "name"; //TODO
                        // Get remote device address
                        handleConnectionAborted[1] = network.getISocket().getRemoteSocketAddress();
                        handler.obtainMessage(Service.CONNECTION_ABORTED, handleConnectionAborted).sendToTarget();
                    } else {

                        // Get Socket
                        BluetoothSocket socket = (BluetoothSocket) network.getISocket().getSocket();
                        String handleConnectionAborted[] = new String[2];
                        // Get remote device name
                        handleConnectionAborted[0] = socket.getRemoteDevice().getName();
                        // Get remote device address
                        handleConnectionAborted[1] = socket.getRemoteDevice().getAddress();
                        // Notify handler
                        handler.obtainMessage(Service.CONNECTION_ABORTED, handleConnectionAborted).sendToTarget();
                    }

                    network.closeConnection();
                }
                break;
            } catch (ClassNotFoundException e) {
                e.printStackTrace(); //TODO update
            }
        }
    }

    void cancel() {
        if (network.getISocket() != null) { //TODO !network.getSocket().isConnected()
            network.closeConnection();
        }
    }
}