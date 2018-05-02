package com.montefiore.gaulthiergain.adhoclibrary.datalink.sockets;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.montefiore.gaulthiergain.adhoclibrary.datalink.util.MessageAdHoc;
import com.montefiore.gaulthiergain.adhoclibrary.datalink.util.Utils;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;

public class SocketManager {

    private final ISocket isocket;
    private final ObjectMapper mapper;
    private final DataInputStream ois;
    private final DataOutputStream oos;
    private final String remoteSocketAddress;
    private final boolean json;

    public ISocket getISocket() {
        return isocket;
    }

    public SocketManager(ISocket isocket, boolean json) throws IOException {
        this.isocket = isocket;
        this.remoteSocketAddress = isocket.getRemoteSocketAddress();
        this.oos = new DataOutputStream(isocket.getOutputStream());
        this.ois = new DataInputStream(isocket.getInputStream());
        this.mapper = new ObjectMapper();
        this.json = json;
    }

    public void sendMessage(MessageAdHoc msg) throws IOException {

        if (json) {
            PrintWriter pw = new PrintWriter(oos);
            pw.println(mapper.writeValueAsString(msg));
            pw.flush();
        } else {
            byte[] byteArray = Utils.serialize(msg);
            if (byteArray != null) {
                oos.writeInt(byteArray.length);
                oos.write(byteArray);
            }
        }
    }

    public MessageAdHoc receiveMessage() throws IOException, ClassNotFoundException {

        if (json) {
            BufferedReader in = new BufferedReader(new InputStreamReader(ois));
            MessageAdHoc msg;
            try {
                msg = mapper.readValue(in.readLine(), MessageAdHoc.class);
            } catch (NullPointerException e) {
                throw new IOException("Closed remote socket");
            }
            return msg;
        } else {
            int length = ois.readInt();
            if (length > 0) {
                byte[] message = new byte[length];
                ois.readFully(message, 0, message.length);
                return Utils.deserialize(message);
            }
            return null;
        }
    }

    public void closeConnection() throws IOException {
        oos.close();
        ois.close();
        isocket.close();
    }

    public String getRemoteSocketAddress() {
        return remoteSocketAddress;
    }
}
