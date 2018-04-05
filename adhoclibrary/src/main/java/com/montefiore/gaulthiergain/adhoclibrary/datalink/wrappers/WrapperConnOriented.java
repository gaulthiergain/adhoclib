package com.montefiore.gaulthiergain.adhoclibrary.datalink.wrappers;

import android.content.Context;

import com.montefiore.gaulthiergain.adhoclibrary.appframework.Config;
import com.montefiore.gaulthiergain.adhoclibrary.appframework.ListenerApp;
import com.montefiore.gaulthiergain.adhoclibrary.datalink.exceptions.NoConnectionException;
import com.montefiore.gaulthiergain.adhoclibrary.datalink.service.AdHocDevice;
import com.montefiore.gaulthiergain.adhoclibrary.datalink.service.ServiceServer;
import com.montefiore.gaulthiergain.adhoclibrary.datalink.sockets.SocketManager;
import com.montefiore.gaulthiergain.adhoclibrary.network.datalinkmanager.ListenerDataLink;
import com.montefiore.gaulthiergain.adhoclibrary.util.MessageAdHoc;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;


public abstract class WrapperConnOriented extends AbstractWrapper {


    final short attemps;
    final short nbThreads;
    final boolean background;
    final Neighbors neighbors;
    final HashMap<String, String> mapAddrLabel;
    final HashMap<String, SocketManager> mapAddrNetwork;

    ServiceServer serviceServer;

    WrapperConnOriented(boolean v, Context context, Config config, short nbThreads, HashMap<String, AdHocDevice> mapAddressDevice,
                        ListenerApp listenerApp, ListenerDataLink listenerDataLink) {
        super(v, context, config.getName(), config.isJson(), config.getLabel(),
                mapAddressDevice, listenerApp, listenerDataLink);
        this.neighbors = new Neighbors();
        this.attemps = config.getAttemps();
        this.nbThreads = nbThreads;
        this.background = config.isBackground();
        this.mapAddrLabel = new HashMap<>();
        this.mapAddrNetwork = new HashMap<>();
    }

    public void disconnect(String remoteLabel) throws IOException {

        SocketManager socketManager = neighbors.getNeighbor(remoteLabel);
        if (socketManager != null) {
            socketManager.closeConnection();
            neighbors.remove(remoteLabel);
        }
    }

    public boolean isDirectNeighbors(String address) {
        return neighbors.getNeighbors().containsKey(address);
    }

    public void disconnectAll() throws IOException {

        if (neighbors.getNeighbors().size() > 0) {
            for (Map.Entry<String, SocketManager> entry : neighbors.getNeighbors().entrySet()) {
                entry.getValue().closeConnection();
            }
            neighbors.getNeighbors().clear();
        }
    }

    public void sendMessage(MessageAdHoc message, String address) throws IOException {

        SocketManager socketManager = neighbors.getNeighbors().get(address);
        if (socketManager != null) {
            socketManager.sendMessage(message);
        }
    }

    public void broadcastExcept(MessageAdHoc message, String excludedAddress) throws IOException {

        if (neighbors.getNeighbors().size() > 0) {
            for (Map.Entry<String, SocketManager> entry : neighbors.getNeighbors().entrySet()) {
                if (!entry.getKey().equals(excludedAddress)) {
                    entry.getValue().sendMessage(message);
                }
            }
        }
    }

    public void broadcast(MessageAdHoc message) throws IOException {
        if (neighbors.getNeighbors().size() > 0) {
            for (Map.Entry<String, SocketManager> entry : neighbors.getNeighbors().entrySet()) {
                entry.getValue().sendMessage(message);
            }
        }
    }
}
