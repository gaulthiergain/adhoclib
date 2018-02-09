package com.montefiore.gaulthiergain.adhoclibrary.aodv;

import android.util.Log;

import com.montefiore.gaulthiergain.adhoclibrary.auto.AutoConnectionActives;
import com.montefiore.gaulthiergain.adhoclibrary.exceptions.NoConnectionException;
import com.montefiore.gaulthiergain.adhoclibrary.network.NetworkObject;
import com.montefiore.gaulthiergain.adhoclibrary.util.Header;
import com.montefiore.gaulthiergain.adhoclibrary.util.MessageAdHoc;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;


public class AodvManager {

    private final Aodv aodv;
    private final boolean v;
    private final String ownName;
    private final String ownStringUUID;
    private final Timer timerRoutingTable;
    private final String TAG = "[Aodv][AodvManager]";
    private final AutoConnectionActives autoConnectionActives;

    public AodvManager(boolean v, String ownStringUUID, String ownName) {
        this.v = v;
        this.autoConnectionActives = new AutoConnectionActives();
        this.timerRoutingTable = new Timer();
        //this.initTimer();
        this.aodv = new Aodv();
        this.ownStringUUID = ownStringUUID;
        this.ownName = ownName;
    }


    public void send(MessageAdHoc msg, String address) throws IOException, NoConnectionException {

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
            startTimerRREQ(address, Aodv.RREQ_RETRIES);
        }
    }

    private void processRREQ(MessageAdHoc msg) throws IOException, NoConnectionException {

        // Get the RREQ message
        RREQ rreq = (RREQ) msg.getPdu();

        // Get previous hop and previous source address
        int hop = rreq.getHopCount();
        String originateAddr = msg.getHeader().getSenderAddr();

        if (v) Log.d(TAG, "Received RREQ from " + originateAddr);

        if (rreq.getDestIpAddress().equals(ownStringUUID)) {
            if (v) Log.d(TAG, ownStringUUID + " is the destination (stop RREQ broadcast)");

            // Update routing table
            EntryRoutingTable entry = aodv.addEntryRoutingTable(rreq.getOriginIpAddress(),
                    originateAddr, hop, rreq.getOriginSeqNum());
            if (entry != null) {

                // Generate RREP
                RREP rrep = new RREP(TypeAodv.RREP.getType(), Aodv.INIT_HOP_COUNT, rreq.getOriginIpAddress(),
                        1, ownStringUUID, Aodv.LIFE_TIME);

                if (v) Log.d(TAG, "Destination reachable via " + entry.getNext());

                // Send message to the next destination
                send(new MessageAdHoc(new Header(TypeAodv.RREP.getCode(), ownStringUUID, ownName), rrep),
                        entry.getNext());
            }
        } else {
            if (aodv.addBroadcastId(rreq.getOriginIpAddress() + rreq.getRreqId())) {
                try {

                    // Update PDU and Header
                    rreq.incrementHopCount();
                    msg.setPdu(rreq);
                    msg.setHeader(new Header(TypeAodv.RREQ.getCode(), ownStringUUID, ownName));

                    // Broadcast message to all directly connected devices
                    broadcastMsgExcept(msg, originateAddr);

                    // Update routing table
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

    private void processRREP(MessageAdHoc msg) throws IOException, NoConnectionException {

        // Get the RREP message
        RREP rrep = (RREP) msg.getPdu();

        // Get previous hop and previous source address
        int hopRcv = rrep.getHopCount();
        String originateAddrRcv = msg.getHeader().getSenderAddr();

        if (v) Log.d(TAG, "Received RREP from " + originateAddrRcv);

        if (rrep.getDestIpAddress().equals(ownStringUUID)) {
            if (v) Log.d(TAG, ownStringUUID + " is the destination (stop RREP)");
            //todo boolean with timer to manage the LIFE TIME of the entry
        } else {
            // Forward the RREP message to the destination by checking the routing table
            EntryRoutingTable destNext = aodv.getNextfromDest(rrep.getDestIpAddress());
            if (destNext == null) {
                if (v) Log.d(TAG, "No destNext found in the routing Table for "
                        + rrep.getDestIpAddress());
            } else {
                if (v) Log.d(TAG, "Destination reachable via " + destNext.getNext());
                // Increment HopCount and send message to the next destination
                rrep.incrementHopCount();
                send(new MessageAdHoc(new Header(TypeAodv.RREP.getCode(), ownStringUUID, ownName)
                        , rrep), destNext.getNext());

            }
        }

        // Update routing table
        EntryRoutingTable entryRRep = aodv.addEntryRoutingTable(rrep.getOriginIpAddress(),
                originateAddrRcv, hopRcv, rrep.getDestSeqNum());
    }

    private void processRERR(MessageAdHoc msg) throws IOException {

        // Get the RERR message
        RERR rerr = (RERR) msg.getPdu();

        // Get previous source address
        String originateAddr = msg.getHeader().getSenderAddr();

        Log.d(TAG, "Received RERR from " + originateAddr + " -> Node " +
                rerr.getUnreachableDestIpAddress() + " is unreachable");

        if (rerr.getUnreachableDestIpAddress().equals(ownStringUUID)) {
            Log.d(TAG, "RERR received on the destination (stop forward)");
        } else if (aodv.containsDest(rerr.getUnreachableDestIpAddress())) {
            aodv.getRoutingTable().removeEntry(rerr.getUnreachableDestIpAddress());
            msg.setHeader(new Header(TypeAodv.RERR.getCode(), ownStringUUID, ownName));
            // Broadcast message to all directly connected devices
            broadcastMsgExcept(msg, originateAddr);
        } else {
            Log.d(TAG, "Node doesn't contain dest: " + rerr.getUnreachableDestIpAddress());
        }
    }

    private void processData(MessageAdHoc msg) throws IOException, NoConnectionException {

        // Get the DATA message
        Data data = (Data) msg.getPdu();

        // Update the data path
        autoConnectionActives.updateDataPath(data.getDestIpAddress());

        if (v) Log.d(TAG, "Data message received from: " + msg.getHeader().getSenderAddr());

        if (data.getDestIpAddress().equals(ownStringUUID)) {
            if (v) Log.d(TAG, ownStringUUID + " is the destination (stop DATA message " +
                    "and send ACK)");

            // Update PDU and Header
            String destinationAck = msg.getHeader().getSenderAddr();
            msg.setPdu(new Data(destinationAck, TypeAodv.DATA_ACK.getCode()));
            msg.setHeader(new Header(TypeAodv.DATA_ACK.getCode(), ownStringUUID, ownName));

            // Send message to the destination
            send(msg, destinationAck);
        } else {
            // Forward the DATA message to the destination by checking the routing table
            EntryRoutingTable destNext = aodv.getNextfromDest(data.getDestIpAddress());
            if (destNext == null) {
                if (v) Log.d(TAG, "No destNext found in the routing Table for " +
                        data.getDestIpAddress());
            } else {
                if (v) Log.d(TAG, "Destination reachable via " + destNext.getNext());
                // Send message to the next destination
                send(msg, destNext.getNext());
            }
        }
    }

    private void processDataAck(MessageAdHoc msg) throws IOException, NoConnectionException {

        // Get the ACK message
        Data dataAck = (Data) msg.getPdu();

        // Update the data path
        autoConnectionActives.updateDataPath(dataAck.getDestIpAddress());

        if (v) Log.d(TAG, "ACK message received from: " + msg.getHeader().getSenderAddr());

        if (dataAck.getDestIpAddress().equals(ownStringUUID)) {
            if (v) Log.d(TAG, ownStringUUID + " is the destination (stop data-ack message)");
        } else {
            // Forward the ACK message to the destination by checking the routing table
            EntryRoutingTable destNext = aodv.getNextfromDest(dataAck.getDestIpAddress());
            if (destNext == null) {
                if (v)
                    Log.d(TAG, "No destNext found in the routing Table for " +
                            dataAck.getDestIpAddress());
            } else {
                if (v) Log.d(TAG, "Destination reachable via " + destNext.getNext());
                // Send message to the next destination
                send(msg, destNext.getNext());
            }
        }
    }

    private void startTimerRREQ(final String address, final int retry) throws IOException {

        // No destination was found, send RREQ request (with timer)
        Log.d(TAG, "No connection to " + address + "-> send RREQ message");

        // Broadcast message to all directly connected devices
        broadcastMsg(new MessageAdHoc(new Header(TypeAodv.RREQ.getCode(), ownStringUUID, ownName),
                new RREQ(TypeAodv.RREQ.getType(), Aodv.INIT_HOP_COUNT, aodv.getIncrementRreqId(), address,
                        1, ownStringUUID, 1)));

        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {

                EntryRoutingTable entry = aodv.getNextfromDest(address);
                if (entry != null) {
                    //test seq num here todo
                } else {
                    if (retry == 0) {
                        if (v) Log.d(TAG, "Expired time: no RREP received for " + address);
                        //todo event here
                    } else {
                        if (v) Log.d(TAG, "Expired time: no RREP received for " + address +
                                " Retry: " + retry);

                        try {
                            startTimerRREQ(address, retry - 1);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }, Aodv.NET_TRANVERSAL_TIME);
    }

    private void sendRRER(String remoteUuid) throws IOException, NoConnectionException {

        if (aodv.getRoutingTable().containsNext(remoteUuid)) {
            String dest = aodv.getRoutingTable().getDestFromNext(remoteUuid);

            if (dest.equals(ownStringUUID)) {
                if (v) Log.d(TAG, "RERR received on the destination (stop forward)");
            } else {
                // Remove the destination from Routing table
                aodv.getRoutingTable().removeEntry(dest);
                // Send RERR message
                RERR rrer = new RERR(TypeAodv.RERR.getType(), 0, dest, 1);
                for (Map.Entry<String, NetworkObject> entry : autoConnectionActives.getActivesConnections().entrySet()) {
                    send(new MessageAdHoc(
                                    new Header(TypeAodv.RERR.getCode(), ownStringUUID, ownName), rrer),
                            entry.getKey());
                }
            }
        }
    }

    private void initTimer() {
        timerRoutingTable.schedule(new TimerTask() {
            @Override
            public void run() {

                Iterator<Map.Entry<String, EntryRoutingTable>> it = aodv.getRoutingTable().getRoutingTable().entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<String, EntryRoutingTable> entry = it.next();

                    // Check if data is recently sent/received
                    if (autoConnectionActives.getActivesDataPath().containsKey(entry.getKey())) {
                        long lastChanged = autoConnectionActives.getActivesDataPath().get(entry.getKey());
                        if (System.currentTimeMillis() - lastChanged > Aodv.EXPIRED_TIME) {
                            Log.d(TAG, "No data on " + entry.getKey() + " since " + Aodv.EXPIRED_TIME + "ms -> Purge Entry in RIB");
                            autoConnectionActives.getActivesDataPath().remove(entry.getKey());
                            it.remove();
                        } else {
                            Log.d(TAG, ">>> data on " + entry.getKey() + " since " + Aodv.EXPIRED_TIME + "ms");
                        }
                    } else {
                        //purge entry in RIB
                        Log.d(TAG, "No data on " + entry.getKey() + " since " + Aodv.EXPIRED_TIME + "ms -> Purge Entry in RIB");
                        it.remove();
                    }
                }
            }
        }, Aodv.EXPIRED_TABLE, Aodv.EXPIRED_TABLE);
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

    public void getRemoteConnections(String remoteUuid) {


        // Remove remote connections
        if (autoConnectionActives.getActivesConnections().containsKey(remoteUuid)) {
            if (v) Log.d(TAG, "Remove active connection with " + remoteUuid);
            NetworkObject networkObject = autoConnectionActives.getActivesConnections().get(remoteUuid);
            autoConnectionActives.getActivesConnections().remove(remoteUuid);
            networkObject.closeConnection();
        }

        if (aodv.getRoutingTable().containsDest(remoteUuid)) {
            if (v) Log.d(TAG, "Remote " + remoteUuid + " from RIB");
            aodv.getRoutingTable().removeEntry(remoteUuid);
        }

        if (aodv.getRoutingTable().getRoutingTable().size() > 0) {
            if (v) Log.d(TAG, "Send RRER ");
            try {
                sendRRER(remoteUuid);
            } catch (IOException e) {
                e.printStackTrace();
            } catch (NoConnectionException e) {
                e.printStackTrace();
            }
        }
    }

    public void processMsgReceived(MessageAdHoc message) throws IOException, NoConnectionException {
        switch (message.getHeader().getType()) {
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
                if (v) Log.e(TAG, "Default Message");
        }
    }

    public ConcurrentHashMap<String, NetworkObject> getConnections() {
        return autoConnectionActives.getActivesConnections();
    }

    public void addConnection(String key, NetworkObject network) {
        autoConnectionActives.addConnection(key, network);
    }
}
