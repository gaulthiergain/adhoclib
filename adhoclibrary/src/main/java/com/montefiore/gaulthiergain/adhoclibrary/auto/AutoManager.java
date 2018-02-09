package com.montefiore.gaulthiergain.adhoclibrary.auto;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import com.montefiore.gaulthiergain.adhoclibrary.aodv.Aodv;
import com.montefiore.gaulthiergain.adhoclibrary.aodv.Data;
import com.montefiore.gaulthiergain.adhoclibrary.aodv.EntryRoutingTable;
import com.montefiore.gaulthiergain.adhoclibrary.aodv.RREP;
import com.montefiore.gaulthiergain.adhoclibrary.aodv.RREQ;
import com.montefiore.gaulthiergain.adhoclibrary.aodv.TypeAodv;
import com.montefiore.gaulthiergain.adhoclibrary.bluetooth.BluetoothAdHocDevice;
import com.montefiore.gaulthiergain.adhoclibrary.bluetooth.BluetoothManager;
import com.montefiore.gaulthiergain.adhoclibrary.bluetooth.BluetoothServiceClient;
import com.montefiore.gaulthiergain.adhoclibrary.bluetooth.BluetoothServiceServer;
import com.montefiore.gaulthiergain.adhoclibrary.bluetooth.BluetoothUtil;
import com.montefiore.gaulthiergain.adhoclibrary.bluetooth.DiscoveryListener;
import com.montefiore.gaulthiergain.adhoclibrary.exceptions.BluetoothBadDuration;
import com.montefiore.gaulthiergain.adhoclibrary.exceptions.DeviceException;
import com.montefiore.gaulthiergain.adhoclibrary.exceptions.NoConnectionException;
import com.montefiore.gaulthiergain.adhoclibrary.network.NetworkObject;
import com.montefiore.gaulthiergain.adhoclibrary.service.MessageListener;
import com.montefiore.gaulthiergain.adhoclibrary.util.Header;
import com.montefiore.gaulthiergain.adhoclibrary.util.MessageAdHoc;

import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AutoManager {

    //Helper
    private final static int LOW = 24;
    private final static int END = 36;

    private final static int NB_THREAD = 8;
    private final static int ATTEMPTS = 3;

    private final boolean v;
    private final Context context;
    private final String TAG = "[AdHoc][AutoManager]";
    private ListenerGUI listenerGUI;

    private final String ownStringUUID;
    private final String ownName;
    private final String ownMac;

    private final Aodv aodv;
    private final AutoConnectionActives autoConnectionActives;

    private BluetoothManager bluetoothManager;
    private HashMap<String, BluetoothAdHocDevice> hashMapDevices;

    private BluetoothServiceServer bluetoothServiceServer;


    public AutoManager(boolean v, Context context, UUID ownUUID) throws IOException, DeviceException {

        this.bluetoothManager = new BluetoothManager(true, context);

        this.v = v;
        this.context = context;
        this.autoConnectionActives = new AutoConnectionActives();
        this.hashMapDevices = new HashMap<>();
        // Take only the last part (24-36) to optimize the process
        this.ownStringUUID = ownUUID.toString().substring(LOW, END);
        this.ownName = BluetoothUtil.getCurrentName();
        this.ownMac = BluetoothUtil.getCurrentMac(context);

        this.listenServer(ownUUID);
        this.updateName();

        this.aodv = new Aodv();
    }

    private void updateName() {
        //TODO update this
        //bluetoothManager.updateDeviceName(Code.ID_APP );
    }

    public void discovery(int duration) throws DeviceException, BluetoothBadDuration {

        // Create instance of Bluetooth Manager
        bluetoothManager = new BluetoothManager(true, context);

        // Check if Bluetooth is enabled
        if (!bluetoothManager.isEnabled()) {

            // If not, enable bluetooth and enable the discovery
            bluetoothManager.enable();
            bluetoothManager.enableDiscovery(duration);
        }

        // Add paired devices into the hashMapDevices
        for (Map.Entry<String, BluetoothAdHocDevice> entry : bluetoothManager.getPairedDevices().entrySet()) {
            if (entry.getValue().getDevice().getName().contains(Code.ID_APP)) {
                hashMapDevices.put(entry.getValue().getShortUuid(), entry.getValue());
                if (v) Log.d(TAG, "Add paired " + entry.getValue().getShortUuid()
                        + " into hashMapDevices");
            }
        }

        // Start the discovery process
        bluetoothManager.discovery(new DiscoveryListener() {
            @Override
            public void onDiscoveryCompleted(HashMap<String, BluetoothAdHocDevice> hashMapBluetoothDevice) {
                // Add no paired devices into the hashMapDevices
                for (Map.Entry<String, BluetoothAdHocDevice> entry : hashMapBluetoothDevice.entrySet()) {
                    if (entry.getValue().getDevice().getName() != null &&
                            entry.getValue().getDevice().getName().contains(Code.ID_APP)) {
                        hashMapDevices.put(entry.getValue().getShortUuid(), entry.getValue());
                        if (v) Log.d(TAG, "Add no paired " + entry.getValue().getShortUuid()
                                + " into hashMapDevices");
                    }
                }
                // Stop and unregister to the discovery process
                bluetoothManager.unregisterDiscovery();

                // Execute onDiscoveryCompleted in the GUI
                if (listenerGUI != null) {
                    listenerGUI.onDiscoveryCompleted(hashMapBluetoothDevice);
                }
            }

            @Override
            public void onDiscoveryStarted() {
                // Execute onDiscoveryStarted in the GUI
                if (listenerGUI != null) {
                    listenerGUI.onDiscoveryStarted();
                }
            }

            @Override
            public void onDeviceFound(BluetoothDevice device) {
                // Execute onDeviceFound in the GUI
                if (listenerGUI != null) {
                    listenerGUI.onDeviceFound(device);
                }
            }

            @Override
            public void onScanModeChange(int currentMode, int oldMode) {
                // Execute onScanModeChange in the GUI
                if (listenerGUI != null) {
                    listenerGUI.onScanModeChange(currentMode, oldMode);
                }
            }
        });
    }

    public void connect() {
        for (Map.Entry<String, BluetoothAdHocDevice> entry : hashMapDevices.entrySet()) {
            if (!autoConnectionActives.getActivesConnections().containsKey(entry.getValue().getShortUuid())) {
                //TODO remove
                if (ownName.equals("#eO91#SamsungGT3") && entry.getValue().getDevice().getName().equals("#e091#Samsung_gt")) {

                } else {
                    _connect(entry.getValue());
                }
            } else {
                if (v) Log.d(TAG, entry.getValue().getShortUuid() + " is already connected");
            }
        }
    }

    private void _connect(final BluetoothAdHocDevice bluetoothAdHocDevice) {
        final BluetoothServiceClient bluetoothServiceClient = new BluetoothServiceClient(true, context, new MessageListener() {
            @Override
            public void onMessageReceived(MessageAdHoc message) {
                processMsgReceived(message);
            }

            @Override
            public void onMessageSent(MessageAdHoc message) {
                if (v) Log.d(TAG, "Message sent: " + message.getPdu().toString());
            }

            @Override
            public void onForward(MessageAdHoc message) {
                if (v) Log.d(TAG, "OnForward: " + message.getPdu().toString());
            }

            @Override
            public void onConnectionClosed(String deviceName, String deviceAddress) {

                if (v) Log.d(TAG, "Link broken with " + deviceAddress);

                //remove remote connections
                /*
                if (autoConnectionActives.getActivesConnections().containsKey(remoteUuid)) {
                    if (v) Log.d(TAG, "Remove active connection with " + remoteUuid);
                    NetworkObject networkObject = autoConnectionActives.getActivesConnections().get(remoteUuid);
                    autoConnectionActives.getActivesConnections().remove(remoteUuid);
                    networkObject.closeConnection();
                    networkObject = null;
                }

                if (aodv.getRoutingTable().containsDest(remoteUuid)) {
                    if (v) Log.d(TAG, "Remove " + remoteUuid + " entry from RIB");
                    aodv.getRoutingTable().removeEntry(remoteUuid);
                }

                if (aodv.getRoutingTable().getRoutingTable().size() > 0) {
                    if (v) Log.d(TAG, "Send RRER ");
                    sendRRER(remoteUuid);
                }*/
            }

            @Override
            public void onConnection(String deviceName, String deviceAddress, String localAddress) {
                if (v) Log.d(TAG, "Connected to server: " + deviceAddress + " - " + deviceName);

                // Execute onConnection in the GUI
                if (listenerGUI != null) {
                    listenerGUI.onConnection(deviceName, deviceAddress, localAddress);
                }
            }
        }, true, true, ATTEMPTS, bluetoothAdHocDevice);

        bluetoothServiceClient.setListenerAutoConnect(new ListenerAutoConnect() {
            @Override
            public void connected(UUID uuid, NetworkObject network) throws IOException, NoConnectionException {

                // Add the active connection into the autoConnectionActives object
                autoConnectionActives.addConnection(uuid.toString().substring(LOW, END).toLowerCase()
                        , network);

                // Send CONNECT message to establish the pairing
                bluetoothServiceClient.send(new MessageAdHoc(
                        new Header("CONNECT", ownMac, ownName), ownStringUUID));

            }
        });

        // Start the bluetoothServiceClient thread
        new Thread(bluetoothServiceClient).start();
    }

    public void stopListening() throws IOException {
        bluetoothServiceServer.stopListening();
    }

    private void listenServer(UUID ownUUID) throws IOException {
        bluetoothServiceServer = new BluetoothServiceServer(true, context, new MessageListener() {
            @Override
            public void onMessageReceived(MessageAdHoc message) {
                processMsgReceived(message);
            }

            @Override
            public void onMessageSent(MessageAdHoc message) {
                if (v) Log.d(TAG, "Message sent: " + message.getPdu().toString());
            }

            @Override
            public void onForward(MessageAdHoc message) {
                if (v) Log.d(TAG, "OnForward: " + message.getPdu().toString());
            }

            @Override
            public void onConnectionClosed(String deviceName, String deviceAddress) {
                if (v) Log.d(TAG, "onConnectionClosed");

                String remoteUuid = "";
                NetworkObject networkObject = bluetoothServiceServer.getActiveConnexion().get(deviceAddress);
                if (networkObject != null) {
                    for (Map.Entry<String, NetworkObject> entry : autoConnectionActives.getActivesConnections().entrySet()) {
                        if (entry.getValue().equals(networkObject)) {
                            remoteUuid = entry.getKey();
                            if (v) Log.d(TAG, "Link broken with " + remoteUuid);
                            break;
                        }
                    }

                    // Remove remote connections
                    if (autoConnectionActives.getActivesConnections().containsKey(remoteUuid)) {
                        if (v) Log.d(TAG, "Remove active connection with " + remoteUuid);
                        autoConnectionActives.getActivesConnections().remove(remoteUuid);
                    }

                    /*if (aodv.getRoutingTable().containsDest(remoteUuid)) {
                        Log.traceAodv(TAG, "Remote " + remoteUuid.substring(LOW, END) + " from RIB");
                        aodv.getRoutingTable().removeEntry(remoteUuid);
                    }

                    if (aodv.getRoutingTable().getRoutingTable().size() > 0) {
                        Log.traceAodv(TAG, "Send RRER ");
                        sendRRER(remoteUuid);
                    }*/

                    networkObject.closeConnection();
                    networkObject = null;
                } else {
                    if (v) Log.e(TAG, "onConnectionClosed >>> Not Found");
                }

            }

            @Override
            public void onConnection(String deviceName, String deviceAddress, String localAddress) {
                if (v) Log.d(TAG, "Connected to client: " + deviceAddress);

                // Execute onConnection in the GUI
                if (listenerGUI != null) {
                    listenerGUI.onConnection(deviceName, deviceAddress, localAddress);
                }

            }
        });

        // Start the bluetoothServiceServer listening process
        bluetoothServiceServer.listen(NB_THREAD, true, "secure",
                BluetoothAdapter.getDefaultAdapter(), ownUUID);

    }

    private void processMsgReceived(MessageAdHoc message) {
        Log.d(TAG, "Message received: " + message.getPdu().toString());
        switch (message.getHeader().getType()) {
            case "CONNECT":
                NetworkObject networkObject = bluetoothServiceServer.getActiveConnexion().get(message.getHeader().getSenderAddr());
                if (networkObject != null) {
                    // Add the active connection into the autoConnectionActives object
                    autoConnectionActives.addConnection((String) message.getPdu(), networkObject);
                }
                break;
            case "RREP":
                processRREP(message);
                break;
            case "RREQ":
                processRREQ(message);
                break;
            case "RERR":
                processRERR(message);
                break;
            case "DATA":
                processData(message);
                break;
            case "DATA_ACK":
                processDataAck(message);
                break;

            default:
                if (v) Log.e(TAG, "DEFAULT MSG");
        }
    }

    public void sendMessage(String address, Serializable serializable) throws IOException, NoConnectionException {

        Header header = new Header(TypeAodv.DATA.getCode(), ownStringUUID, ownName);
        MessageAdHoc msg = new MessageAdHoc(header, new Data(address, serializable));

        // Send message to remote device
        send(msg, address);
    }

    private void send(MessageAdHoc msg, String address) throws IOException, NoConnectionException {

        if (autoConnectionActives.getActivesConnections().containsKey(address)) {
            // The destination is directly connected
            NetworkObject networkObject = autoConnectionActives.getActivesConnections().get(address);
            networkObject.sendObjectStream(msg);
            if (v) Log.d(TAG, "Send to " + address);

        } else if (aodv.containsDest(address)) {
            // The destination learned from neighbors -> send to next by checking the routing table
            EntryRoutingTable destNext = aodv.getNextfromDest(address);
            if (destNext == null) {
                if (v) Log.d(TAG, "No destNext found in the routing Table for " + address);
            } else {

                if (v) Log.d(TAG, "Routing table contains " + destNext.getNext());
                // Update the connexion
                autoConnectionActives.updateDataPath(address);
                // Send message to remote device
                send(msg, destNext.getNext());
            }
        } else if (msg.getHeader().getType().equals(TypeAodv.RERR.getCode())) {
            if (v) Log.d(TAG, ">>>>>RERR");
            // TODO change message displayed here
        } else {
            //RREQ
            //TODO startTimerRREQ(uuid, Aodv.RREQ_RETRIES);
            if (v) Log.d(TAG, "No connection to " + address + "-> send RREQ message");
            broadcastMsg(new MessageAdHoc(new Header(TypeAodv.RREQ.getCode(), ownStringUUID, ownName),
                    new RREQ(TypeAodv.RREQ.getType(), Aodv.INIT_HOP_COUNT, aodv.getIncrementRreqId(), address,
                            1, ownStringUUID, 1)));
        }
    }

    private void broadcastMsg(MessageAdHoc msg) throws IOException {
        for (Map.Entry<String, NetworkObject> entry : autoConnectionActives.getActivesConnections().entrySet()) {
            entry.getValue().sendObjectStream(msg);
            if (v) Log.d(TAG, "Broadcast Message to " + entry.getKey());
        }
    }

    private void broadcastMsgExcept(MessageAdHoc message, String address) throws IOException {
        for (Map.Entry<String, NetworkObject> entry : autoConnectionActives.getActivesConnections().entrySet()) {
            if (!entry.getKey().equals(address)) {
                entry.getValue().sendObjectStream(message);
                if (v) Log.d(TAG, "Broadcast Message to " + entry.getKey());
            }
        }
    }

    private void processRREQ(MessageAdHoc msg) {

        RREQ rreq = (RREQ) msg.getPdu();
        // Get previous hop
        int hop = rreq.getHopCount();
        // Get previous src
        String originateAddr = msg.getHeader().getSenderAddr();

        if (v) Log.d(TAG, "Received RREQ from " + originateAddr);

        if (rreq.getDestIpAddress().equals(ownStringUUID)) {
            if (v) Log.d(TAG, ownStringUUID + " is the destination (stop RREQ broadcast)");

            //Update routing table
            EntryRoutingTable entry = aodv.addEntryRoutingTable(rreq.getOriginIpAddress(),
                    originateAddr, hop, rreq.getOriginSeqNum());
            if (entry != null) {

                //Generate RREP
                RREP rrep = new RREP(TypeAodv.RREP.getType(), Aodv.INIT_HOP_COUNT, rreq.getOriginIpAddress(),
                        1, ownStringUUID, Aodv.LIFE_TIME);

                try {
                    if (v) Log.d(TAG, "Destination reachable via " + entry.getNext());

                    send(new MessageAdHoc(new Header(TypeAodv.RREP.getCode(), ownStringUUID, ownName), rrep),
                            entry.getNext());
                } catch (IOException | NoConnectionException e) {
                    e.printStackTrace();
                }
            }
        } else {
            if (aodv.addBroadcastId(rreq.getOriginIpAddress() + rreq.getRreqId())) {
                try {
                    if (v) Log.d(TAG, "Received RREQ from " + rreq.getOriginIpAddress());

                    rreq.incrementHopCount();
                    msg.setPdu(rreq);

                    msg.setHeader(new Header(TypeAodv.RREQ.getCode(), ownStringUUID, ownName));

                    broadcastMsgExcept(msg, originateAddr);

                    //Update routing table
                    EntryRoutingTable entry = aodv.addEntryRoutingTable(rreq.getOriginIpAddress(),
                            originateAddr, hop, rreq.getOriginSeqNum());

                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                if (v) Log.d(TAG, "Already received this RREQ from " + rreq.getOriginIpAddress());
            }
        }
    }

    private void processRREP(MessageAdHoc msg) {
        RREP rrepRcv = (RREP) msg.getPdu();
        // Get previous hop
        int hopRcv = rrepRcv.getHopCount();
        // Get previous src
        String originateAddrRcv = msg.getHeader().getSenderAddr();

        if (v) Log.d(TAG, "Received RREP from " + originateAddrRcv);
        if (rrepRcv.getDestIpAddress().equals(ownStringUUID)) {
            if (v) Log.d(TAG, ownStringUUID + " is the destination (stop RREP)");
            //todo boolean with timer to manage the LIFE TIME of the entry
        } else {
            //Forward message depending the next entry on the routing table
            EntryRoutingTable destNext = aodv.getNextfromDest(rrepRcv.getDestIpAddress());
            if (destNext == null) {
                if (v) Log.d(TAG, "No destNext found in the routing Table for "
                        + rrepRcv.getDestIpAddress());
            } else {
                if (v) Log.d(TAG, "Destination reachable via " + destNext.getNext());
                try {
                    rrepRcv.incrementHopCount();
                    send(new MessageAdHoc(new Header(TypeAodv.RREP.getCode(), ownStringUUID, ownName)
                            , rrepRcv), destNext.getNext());
                } catch (IOException | NoConnectionException e) {
                    e.printStackTrace();
                }
            }
        }

        //Update routing table
        EntryRoutingTable entryRRep = aodv.addEntryRoutingTable(rrepRcv.getOriginIpAddress(),
                originateAddrRcv, hopRcv, rrepRcv.getDestSeqNum());
    }

    private void processRERR(MessageAdHoc msg) {

    }

    private void processData(MessageAdHoc msg) {
        // Check if dest otherwise forward to path
        Data data = (Data) msg.getPdu();

        // Update the connexion
        autoConnectionActives.updateDataPath(data.getDestIpAddress());


        if (v) Log.d(TAG, "Data message received from: " + msg.getHeader().getSenderAddr());

        if (data.getDestIpAddress().equals(ownStringUUID)) {
            if (v) Log.d(TAG, ownStringUUID + " is the destination (stop data message)");
            if (v) Log.d(TAG, "send DATA-ACK");

            try {
                String destinationAck = msg.getHeader().getSenderAddr();
                msg.setPdu(new Data(destinationAck, "ACK"));
                msg.setHeader(new Header("DATA_ACK", ownStringUUID, ownName));
                send(msg, destinationAck);
            } catch (IOException | NoConnectionException e) {
                e.printStackTrace();
            }
        } else {
            EntryRoutingTable destNext = aodv.getNextfromDest(data.getDestIpAddress());
            if (destNext == null) {
                if (v)
                    Log.d(TAG, "No destNext found in the routing Table for " + data.getDestIpAddress());
            } else {
                try {
                    send(msg, destNext.getNext());
                } catch (IOException | NoConnectionException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void processDataAck(MessageAdHoc msg) {

        // Get the ACK
        Data dataAck = (Data) msg.getPdu();

        // Update the data path
        autoConnectionActives.updateDataPath(dataAck.getDestIpAddress());

        if (v) Log.d(TAG, "DataAck message received from: " + msg.getHeader().getSenderAddr());

        if (dataAck.getDestIpAddress().equals(ownStringUUID)) {
            if (v) Log.d(TAG, ownStringUUID + " is the destination (stop data-ack message)");
        } else {
            EntryRoutingTable destNext = aodv.getNextfromDest(dataAck.getDestIpAddress());
            if (destNext == null) {
                if (v)
                    Log.d(TAG, "No  destNext found in the routing Table for " + dataAck.getDestIpAddress());
            } else {
                // Send message to the next destination
                try {
                    send(msg, destNext.getNext());
                } catch (IOException | NoConnectionException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void setListenerGUI(ListenerGUI listenerGUI) {
        this.listenerGUI = listenerGUI;
    }
}
