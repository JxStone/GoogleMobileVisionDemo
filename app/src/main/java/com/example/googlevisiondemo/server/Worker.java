package com.example.googlevisiondemo.server;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;


public class Worker extends Thread {

    /* --------------------------- C o n s t a n t s ---------------------------- */

    // states
    public final static int
            FNAME                 = 0,
            GETDATA               = 1,
            FIN                   = 2;

    /* ----------------------- C l a s s    M e m b e r s ----------------------- */

    /* client info */
    private     DatagramSocket  socket;         // socket to client
    private     InetAddress     address;        // client address
    private     int             port;           // client port

    /* file info */
    private     File            file;           // output file
    private     File            directory;
    private     OutputStream    out;            // stream to file
    private     boolean         done;           // end of file reached?

    private     int             clientIsn;      // client sequence number
    private     int             serverIsn;      // server sequence number
    private     int             state;

    /* ----------------------- C l a s s    M e t h o d s ----------------------- */

    /**
     * @brief initialise a Worker from a starting packet
     * @param[in] "DatagramPacket packet"
     *
     * This function is called from the Server when it receives a packet with
     * no worker attached.  In this case, a worker needs to be created.  This
     * function shall parse the starting packet to extract the IP, port, and
     * filename (which is the entire body of the packet).  These parameters shall
     * be used to create a Worker.  An acknowledgement should be returned.
     */
    public static Worker newWorker(Packet packet, File directory, int freeSpace) {
        Worker      ret;

        ret = new Worker();

        // create socket
        try {
            ret.socket = new DatagramSocket();
        } catch (SocketException e) {
            System.out.println("*** Error: could not create socket");
            return null;
        }

        // error check
        if (packet.hasError()) {
            System.out.println("*** Error: bad checksum");
            /* SEND BACK NONSENSE */
            Worker.acknowledge(ret.socket, packet.getAddress(), packet.getPort(), false, false, false, -1, -1, freeSpace);
            return null;
        }
        if (!packet.isSyn()) {

            System.out.println("*** Error: could not create Worker.  " +
                    "Invalid file header sequence number");

        /*
        This can happen in the following scenario:

        The client sends a good ACK.  The server sends a bad ACK and quits.  The
        client re-sends the ACK.  The server tries to set up a new worker to
        handle a seemingly new client.

        Acknowledge a -1 to tell client to exit.

        If this is the case, acknowledge the end of the file without trying to
        create a new worker.
        */
            return null;
        }
        if (!directory.exists() || !directory.isDirectory()) {
            System.out.println("*** Server-side Error: bad directory");
            // do not acknowledge -- let client timeout
            return null;
        }

        // init Worker
        ret.address = packet.getAddress();
        ret.port = packet.getPort();
        ret.directory = directory;
        ret.state = FNAME;
        ret.done = false;
        ret.clientIsn = packet.getSequenceNumber();
        ret.serverIsn = (new java.util.Random()).nextInt(100);

        Worker.acknowledge(ret.socket, ret.address, ret.port, true, true, false, ret.serverIsn, ret.clientIsn + 1, freeSpace);

        return ret;
    }

    public void run() {
        long start;
        start = System.currentTimeMillis() - 1000;
        while (!done) {
            if ((System.currentTimeMillis() - start) >= 500) {
                Worker.acknowledge(socket, address, port, false, false, true, serverIsn + 1, clientIsn + 2, 0);
                start = System.currentTimeMillis();
            }
        }
    }

    /**
     * @brief test if packet belongs to this worker
     * @param[in] "DatagramPacket packet" packet to test
     * @return TRUE if packet belongs here
     *
     * If the address and port of the packet match the address and port that
     * this Worker is responsible for, then return TRUE.
     */
    public boolean matches(Packet packet) {
        return (
                address.equals(packet.getAddress())
                        && port == packet.getPort()
        );
    }

    public void acknowledge(int freeSpace) {
        acknowledge(socket, address, port, false, true, false, serverIsn, clientIsn + 1, freeSpace);
    }

    /**
     * @brief acknowledges last received ID
     */
    public static void acknowledge(DatagramSocket socket, InetAddress address,
                                   int port,
                                   boolean isSyn, boolean isAck, boolean isFin,
                                   int seqNum, int ackNum, int freeSpace) {
        Packet pkt;

        pkt = new Packet();

        pkt.setAddress(address);
        pkt.setPort(port);

        pkt.setSyn(isSyn);
        pkt.setAck(isAck);
        pkt.setFin(isFin);

        pkt.setSequenceNumber(seqNum);
        pkt.setAcknowledgementNumber(ackNum);

        pkt.setData(ByteBuffer.allocate(4).putInt(freeSpace).array());

        if (!Packet.send(socket, pkt,
                Packet.P_SERVER_BIT_ERROR, Packet.P_SERVER_PACKET_LOSS)) {
            System.out.println("*** Error: could not send acknowledgement packet");
        }
    }

    /**
     * @brief add a packet to output file
     * @param[in] "Packet pkt" received packet
     * @return true if successful
     *
     * This function writes a packet to file.
     */
    public boolean addPacket(Packet pkt, int freeSpace) {
        int expectedAck;

        expectedAck = clientIsn + 1;

        // error check
        if (pkt.hasError()) {                                      // checksum error?
            System.out.println("*** Error: bad checksum");
            acknowledge(freeSpace);
            return false;
        }

        // handle repeat SYN packets
        if (pkt.isSyn()) {
            // not right, but if it has been re-sent, just send back another SYN.
            clientIsn = pkt.getSequenceNumber();

            // send ack
            Worker.acknowledge(socket, address, port, true, true, false, serverIsn, clientIsn + 1, freeSpace);
            state = FNAME;
            return true;
        }

        // error check sequence number
        if (pkt.getSequenceNumber() != expectedAck) {    // weird seq num?
            acknowledge(freeSpace);
            return false;
        } else {
            ++clientIsn;
        }

        // handle fin
        if (pkt.isFin()) {
            // start teardown

            // close file
            try {
                out.close();
            } catch (IOException e) {}
            acknowledge(freeSpace);

            // send fin
            if (state != FIN) {
                this.start();
            }
            state = FIN;
            return true;
        }

        if (pkt.getAcknowledgementNumber() > serverIsn) {
            serverIsn = pkt.getAcknowledgementNumber();
        }

        // write to file or close file?
        switch(state) {
            case FNAME:
                file = new File(
                        directory,
                        new File(
                                new String(pkt.getData())
                        ).getName());
                if (!file.exists()) {
                    try {
                        file.createNewFile();
                    } catch (IOException e) {
                        System.out.println("*** Fatal-error: could not create file");
                    }
                }

                try {
                    out = new FileOutputStream(file);
                } catch (FileNotFoundException err) {
                    System.out.println("*** Fatal-error: could not open file");
                }

                state = GETDATA;
                break;
            case GETDATA:
                try {
                    out.write(pkt.getData(), 0, pkt.getData().length);
                } catch (IOException e) {
                    System.out.println("*** Fatal-error: could not write to file");
                    return false;
                }
                break;
            default:
                done = true;
                return true;
        }

        acknowledge(freeSpace);
        return true;
    }

    /**
     * @brief has this Worker received the last packet?
     * @return TRUE if last packet received
     */
    public boolean isDone() {
        return done;
    }

    /**
     * @brief get the name of the file where packets are going
     * @return the file name
     */
    public File getFile() {
        return file;
    }

    /**
     * @brief release handle to output file by closing stream
     */
    public void close() {
        try {
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

} /* End of Class */
