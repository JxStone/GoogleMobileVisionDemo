package com.example.googlevisiondemo.client;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;

public class Client {

    /* --------------------------- C o n s t a n t s ---------------------------- */

    private final static int
            BUFFER_LENGTH       = 16384,     // length of buffer when splitting file into
    // packets
            NUM_ATTEMPTS        = 30,       // maximum # of attempts for timeouts
            MAX_WINDOW          = 1000,     // maximum window size
            MSS                 = 1,        // congestion control window size default
            DUP_ACK_MAX         = 3;        // maximum # of duplicate ACKs

    private final static double
            ALPHA               = 0.125,    // exponential avg value for estimated RTT
            BETA                = 0.25;     // exponential avg for RTT deviation

    /* ----------------------- C l a s s    M e m b e r s ----------------------- */

    // server UDP connection
    private     InetAddress         address;    // address of server
    private     int                 port;       // server's port number
    private     DatagramSocket      client;     // client socket

    // TCP seq/ack numbers
    private     int                 clientIsn;
    private     int                 serverIsn;

    // dynamic timeout
    private     double              timeout;     // Timeout between packets
    private     double              curRTT;
    private     double              estRTT;
    private     double              devRTT;

    // dynamic window size
    private     int                 windowSize;     // Size of the window
    private     int                 flowWin;        // flow control window
    private     int                 congWin;        // congestion control window
    private     int                 congThreshold;  // congestion control threshold

    // Go-Back-N
    private     Record              window[];       // window of packets
    private     int                 base;
    private     int                 nextSeqNum;
    private     long                timer;          // for timeouts
    private     boolean             eof;            // true when end of file reached
    private     FileInputStream     in;             // stream from file

    /* -------------------- P u b l i c    F u n c t i o n s -------------------- */

    /**
     * @brief create a new client connection to server
     * @param[in] "InetAddress serverAddr" address of server
     * @param[in] "int serverPort"
     * @return a handle to client, or NULL on error
     */
    public static Client NewClient(InetAddress serverAddr, int serverPort) {

        Client ret;

        try {
            ret = new Client(serverAddr, serverPort);
        } catch (SocketException e) {
            ret = null;
        }

        return ret;

    }

    /**
     * @brief upload a file to the server
     * @param[in] "File f" file to send
     *
     * This functions uses Go-Back-N, congestion control, flow control, dynamic
     * window size, TCP connection setup, TCP connection teardown, and dynamic
     * timeouts to send a file.
     */
    public void sendFile(File f) {
        // open file
        if(!f.canRead()) {
            System.out.println("CANNOT READ!!!");
        }

        try {
            in = new FileInputStream(f);
        } catch (FileNotFoundException e) {
            System.out.println("*** Error: could not open file");
            return;
        }

        // reset
        reset();

        // setup a connection (and init serverIsn)
        System.out.println("Setting Up TCP Connection...");
        if (!TCP_Setup()) {
            System.out.println("*** Error: TCP connection could not be established");
            try { in.close(); } catch (IOException e) {}
            return;
        }
        System.out.println("Connection ESTABLISHED.");

        // send file header
        if (!SendFileHeader(f.getName())) {
            // timeout?
            try { in.close(); } catch (IOException e) {}
            System.out.println("*** Error: connection failed");
            return;
        }

        // set up base and nextSeqNum
        base = clientIsn;
        nextSeqNum = clientIsn;

        // run algorithm
        while (base != nextSeqNum || !eof) {
            System.out.println("----------------------");
            System.out.println("        Base = " + base);
            System.out.println("Next Seq Num = " + nextSeqNum);
            System.out.println("       Timer = " + (System.currentTimeMillis() - timer));
            System.out.println(" Window Size = " + windowSize);
            System.out.println("     FlowWin = " + flowWin);
            System.out.println("     CongWin = " + congWin);
            System.out.println("     Timeout = " + (int)Math.round(timeout));

            // send a bunch of packets to fill window
            if (!SendPackets()) {
                try { in.close(); } catch (IOException e) {}
                return; // error message printed from inside
            }

            // receive a bunch of acknowledgements
            if (!ReceiveAcks()) {
                try { in.close(); } catch (IOException e) {}
                return; // error message printed from inside
            }
        }

        // teardown TCP connections
        System.out.println("Tearing Down TCP Connection...");
        if (!TCP_Teardown()) {
            System.out.println("*** Non-fatal Error: Connection Teardown failed");
        }
        System.out.println("Connection CLOSED.");
    }

    /**
     * @brief close client socket
     */
    public void close() {
        client.close();
    }

    /* ------------------- P r i v a t e    F u n c t i o n s ------------------- */

    /**
     * @brief send a series of packets
     * @return true on success
     *
     * This function sends a series of packets to fill the remaining window
     * spaces.  This function returns false on a send exception.
     */
    private boolean SendPackets() {
        Record record;

        // create a packet if space in window
        while (nextSeqNum < (base + windowSize) && !eof) {

            // get record from window
            record = window[nextSeqNum % MAX_WINDOW];

            // if record is empty, create packet from file data
            if (record.empty) {
                try {
                    record.packet = makePacket();
                } catch (IOException e) {
                    System.out.println("*** Error: failed to read from file");
                    return false;
                }
                record.resent = false;
                record.empty = false;
                record.sendTime = System.currentTimeMillis();
                record.numAcks = 0;

                // check if end-of-file
                if (record.packet == null) {
                    // if end of file, indicate
                    eof = true;
                } else {
                    // otherwise, initialise packet more and send it
                    record.packet.setAddress(address);
                    record.packet.setPort(port);
                    record.packet.setSyn(false);
                    record.packet.setAck(true);
                    record.packet.setFin(false);
                    record.packet.setAcknowledgementNumber(serverIsn);
                    record.packet.setSequenceNumber(nextSeqNum);

                    if (!Packet.send(client, record.packet,
                            Packet.P_CLIENT_BIT_ERROR, Packet.P_CLIENT_PACKET_LOSS)) {
                        System.out.println("*** Error: failed to send packet #"
                                + nextSeqNum);
                        return false;
                    }

                    // start timer if applicable
                    if (nextSeqNum == base) {
                        timer = System.currentTimeMillis();
                    }

                    // increment sequence number
                    ++nextSeqNum;
                    clientIsn = nextSeqNum;
                }
            }
        }

        return true;
    }

    /**
     * @brief receive a series of acknowledgements
     * @return true on success
     *
     * This function shall receive a series of acknowledgements while updating
     * the base.  This function exits once all acknowledgements are received or
     * once the process times out.  On a timeout, all unacknowledged packets are
     * re-sent and the timer is restarted.  In addition, the window size is
     * updated based on the server's receiver window free space (sent in ACKs via
     * flow control) and based on the congestion control window.  This functions
     * returns false on a send exception.
     */
    private boolean ReceiveAcks() {
        Packet rcv;
        int i;
        Record ack;
        long now;

        // initialise packet
        rcv = new Packet();
        now = System.currentTimeMillis();

        // while no timeout and while acks to receive
        while ((now - timer) < timeout && base < nextSeqNum) {

            // try to receive a packet
            Packet.receive(client, rcv);

            // if no error, handle acknowledgement and update flow control window
            if (!rcv.hasError()) {

                ack = window[(rcv.getAcknowledgementNumber() - 1) % MAX_WINDOW];

                if (base < rcv.getAcknowledgementNumber()) {
                    // only look at new acknowledgements

                    // invalidate data from base to before ack #
                    for (i = base; i < rcv.getAcknowledgementNumber(); i++) {
                        window[i % MAX_WINDOW].empty = true;
                    }

                    // update base
                    base = rcv.getAcknowledgementNumber();
                    serverIsn = rcv.getSequenceNumber();

                    // update flow control window (newest data only)
                    flowWin = ByteBuffer.wrap(rcv.getData()).getInt();
                }

                // check for duplicate ACKs and update congWin
                ++ack.numAcks;
                if (ack.numAcks > DUP_ACK_MAX) {
                    congWin = congThreshold;
                } else {
                    if (congWin < congThreshold) {
                        congWin <<= 1;
                    } else {
                        congWin += MSS;
                    }
                }

                // update timeout
                if (!ack.resent) {
                    // get current RTT (round-trip time)
                    curRTT = System.currentTimeMillis() - ack.sendTime;
                    // estimate RTT based
                    estRTT = ((1 - ALPHA) * estRTT) + (ALPHA * curRTT);
                    // update RTT deviation
                    devRTT = ((1 - BETA) * devRTT) +
                            (BETA * Math.abs(curRTT - estRTT));
                    // update timeout
                    timeout = estRTT + (devRTT * 4);
                }
            }

            // update current time
            now = System.currentTimeMillis();
        }

        // resend timed out packets
        if (base != nextSeqNum) {
            congThreshold = congWin >> 1;
            congWin = MSS;
        }
        for (i = base; i < nextSeqNum; i++) {
            window[i % MAX_WINDOW].resent = true;
            if (!Packet.send(client, window[i % MAX_WINDOW].packet,
                    Packet.P_CLIENT_BIT_ERROR, Packet.P_CLIENT_PACKET_LOSS)) {
                System.out.println("*** Error: failed to send packet");
                return false;
            }
            if (i == base) {
                timer = System.currentTimeMillis();
            }
        }

        // update wndow size
        windowSize = (congWin < flowWin) ? congWin : flowWin;
        return true;
    }

    /**
     * @brief setup TCP
     * @return true on success
     *
     * This function sends a SYN packet and waits for a SYN acknowledgement from
     * the server.  This function must also match the server ISN to the incoming
     * sequence packet.  If the maximum number of attempts is exhausted, the
     * client shall give up.
     */
    private boolean TCP_Setup() {
        Packet              send;
        Packet              receive;                // parsed packet received
        int                 sendErrors,         // # of send errors
                receiveErrors;      // # of receive errors
        boolean             sendFlag,
                receiveFlag;

        receive = new Packet();

        send = new Packet();
        send.setAddress(address);
        send.setPort(port);
        send.setSyn(true);
        send.setAck(false);
        send.setFin(false);
        send.setSequenceNumber(clientIsn);
        send.setAcknowledgementNumber(0);
        send.setData("".getBytes());

        while (true) {

            // send and receive datagram packets
            receiveErrors = 0;
            do {

                // send datagram packet
                sendErrors = 0;
                do {
                    if (Packet.send(client, send, Packet.P_CLIENT_BIT_ERROR, Packet.P_CLIENT_PACKET_LOSS)) {
                        sendFlag = false;
                    } else {
                        ++sendErrors;
                        if (sendErrors >= NUM_ATTEMPTS) {
                            return false;
                        }
                        sendFlag = true;
                    }
                } while (sendFlag);

                // receive a packet
                Packet.receive(client, receive, 1000);
                if (receive.getErrorCode() == Packet.PACKET_LOSS) {
                    ++receiveErrors;
                    if (receiveErrors >= NUM_ATTEMPTS) {
                        return false;
                    }
                    receiveFlag = true;
                } else {
                    receiveFlag = false;
                }

            } while (receiveFlag);

            // error check
            if (receive.hasError()) {
                // checksum error?

            } else if (!receive.isSyn()) {

            } else if (receive.getAcknowledgementNumber() != clientIsn + 1) {

            } else {
                ++clientIsn;
                serverIsn = receive.getSequenceNumber();    // success otherwise
                return true;
            }

        }
    }

    /**
     * @brief send file name
     * @return true on success
     *
     * For the server to write to a file, the server needs the file name.  The
     * first packet shall contain nothing except for the file name.  The client
     * cannot continue until the file header packet is sent and acknowledged.
     * The client will give up if the maximum number of attempts is exhausted.
     */
    private boolean SendFileHeader(String fname) {
        Packet              send;
        Packet              receive;                // parsed packet received
        int                 sendErrors,         // # of send errors
                receiveErrors;      // # of receive errors
        boolean             sendFlag,
                receiveFlag;

        receive = new Packet();

        send = new Packet();
        send.setAddress(address);
        send.setPort(port);
        send.setSyn(false);
        send.setAck(true);
        send.setFin(false);
        send.setSequenceNumber(clientIsn);
        send.setAcknowledgementNumber(serverIsn + 1);
        send.setData(fname.getBytes());

        while (true) {

            // send and receive datagram packets
            receiveErrors = 0;
            do {

                // send datagram packet
                sendErrors = 0;
                do {
                    if (Packet.send(client, send, Packet.P_CLIENT_BIT_ERROR, Packet.P_CLIENT_PACKET_LOSS)) {
                        sendFlag = false;
                    } else {
                        ++sendErrors;
                        if (sendErrors >= NUM_ATTEMPTS) {
                            return false;
                        }
                        sendFlag = true;
                    }
                } while (sendFlag);

                // receive a packet
                Packet.receive(client, receive, 1000);
                if (receive.getErrorCode() == Packet.PACKET_LOSS) {
                    ++receiveErrors;
                    if (receiveErrors >= NUM_ATTEMPTS) {
                        return false;
                    }
                    receiveFlag = true;
                } else {
                    receiveFlag = false;
                }

            } while (receiveFlag);

            // error check
            if (receive.hasError()) {
                // checksum error?

            } else if (!receive.isAck() || receive.getAcknowledgementNumber() != clientIsn + 1) {

            } else {
                ++clientIsn;
                serverIsn = receive.getSequenceNumber();    // success otherwise
                return true;
            }

        }
    }

    /**
     * @brief start TCP teardown.
     * @return true on success
     *
     * First, send a FIN and get an acknowledgement.  Then, wait for the server
     * to send a FIN.  Acknowledge all FINs (including duplicates) until the
     * server is silent for a full three seconds.
     */
    private boolean TCP_Teardown() {
        boolean flag;
        Packet rcv;
        Packet finalAck;
        long start;

        rcv = new Packet();

        // send fin and get ack
        if (!SendFin()) {
            return false;
        }

        // create a final ack
        finalAck = new Packet();
        finalAck.setAddress(address);
        finalAck.setPort(port);
        finalAck.setAck(true);
        finalAck.setSyn(false);
        finalAck.setFin(false);
        finalAck.setSequenceNumber(clientIsn);
        finalAck.setData("".getBytes());

        // go 3 seconds without any packets from server
        start = System.currentTimeMillis();
        flag = false;
        while ((System.currentTimeMillis() - start) < 3000) {
            Packet.receive(client, rcv);

            if (rcv.getErrorCode() != Packet.PACKET_LOSS) {
                // restart timer
                start = System.currentTimeMillis();
                flag = false;

                // re-send ACK if another FIN
                if (!rcv.hasError() && rcv.isFin()) {
                    serverIsn = rcv.getSequenceNumber();
                    finalAck.setAcknowledgementNumber(serverIsn + 1);
                    Packet.send(client, finalAck, Packet.P_CLIENT_BIT_ERROR, Packet.P_CLIENT_PACKET_LOSS);
                    flag = true;
                }
            }
        }

        return flag;
    }

    /**
     * @brief send a FIN packet
     * @return true on success
     *
     * This function shall send FIN packets until the server acknowledges a FIN.
     * If a maximum number of attempts is exhausted, then return false.
     */
    private boolean SendFin() {
        Packet              send;
        Packet              receive;                // parsed packet received
        int                 sendErrors,         // # of send errors
                receiveErrors;      // # of receive errors
        boolean             sendFlag,
                receiveFlag;

        receive = new Packet();

        send = new Packet();
        send.setAddress(address);
        send.setPort(port);
        send.setSyn(false);
        send.setAck(false);
        send.setFin(true);
        send.setSequenceNumber(clientIsn);
        send.setAcknowledgementNumber(serverIsn + 1);
        send.setData("".getBytes());

        while (true) {

            // send and receive datagram packets
            receiveErrors = 0;
            do {
                // send datagram packet
                sendErrors = 0;
                do {
                    if (Packet.send(client, send, Packet.P_CLIENT_BIT_ERROR, Packet.P_CLIENT_PACKET_LOSS)) {
                        sendFlag = false;
                    } else {
                        ++sendErrors;
                        if (sendErrors >= NUM_ATTEMPTS) {
                            return false;
                        }
                        sendFlag = true;
                    }
                } while (sendFlag);

                // receive a packet
                Packet.receive(client, receive, 1000);
                if (receive.getErrorCode() == Packet.PACKET_LOSS) {
                    ++receiveErrors;
                    if (receiveErrors >= NUM_ATTEMPTS) {
                        return false;
                    }
                    receiveFlag = true;
                } else {
                    receiveFlag = false;
                }

            } while (receiveFlag);

            // error check
            if (receive.hasError()) {
                // checksum error?

            } else if (!receive.isAck()) {

            } else if (receive.getAcknowledgementNumber() != clientIsn + 1) {

            } else {
                ++clientIsn;
                serverIsn = receive.getSequenceNumber();    // success otherwise
                return true;
            }
        }
    }

    /**
     * @brief creates a packet from a file
     * @param[in] "FileInputStream in" file
     * @param[out] "Packet out" only updates data -- seq num should be set before
     *                          calling this function
     * @return NULL of no more data in file
     */
    private Packet makePacket() throws IOException {
        byte buffer[];
        int i;
        int read;
        Packet ret;

        ret = new Packet();
        ret.setSequenceNumber(nextSeqNum);

        // initialise buffer
        buffer = new byte[BUFFER_LENGTH - 1 - ret.getHeaderLength()];

        // initialise file reader
        read = in.read();

        if (read < 0) {
            return null;
        }

        // read from file into buffer
        i = 0;
        while (read >= 0) {
            // put byte from file into buffer
            buffer[i] = (byte) (read & 0xFF);
            ++i;

            if (i >= buffer.length) {
                // create a packet from the buffer
                ret.setData(buffer);
                return ret;
            }

            // read next
            read = in.read();
        }

        ret.setData(buffer, i);
        return ret;
    }

    /**
     * @brief constructor
     * @param[in] "InetAddress address" address of server
     * @param[in] "int port" port number of server
     *
     * The constructor store's the information of the server and it initialises a
     * socket for packet-wise communication.
     */
    private Client(InetAddress address, int port) throws SocketException {
        // initialise UDP connection to server
        this.address = address;
        this.port = port;
        client = new DatagramSocket(); // Create the new client datagram socket

        // eliminate timeout
        try {
            client.setSoTimeout(1);
        } catch (SocketException e1) {
            System.out.println("*** Error: failed to set timeout");
        }
    }

    /**
     * @brief reset all TCP values
     */
    private void reset() {
        int i;

        // initialise client ISN (serverIsn is unknown)
        clientIsn = (new java.util.Random()).nextInt(100);

        // initialise dynamic timeouts
        timeout     = 100;
        curRTT      = 100;
        estRTT      = 100;
        devRTT      = 100;

        // initialise dynamic window size (flow control + congestion control)
        windowSize      = MSS;
        flowWin         = 1;            // arbitrary
        congWin         = MSS;
        congThreshold   = 10 * MSS;     // arbitrary

        // initialise Go-Back-N
        window = new Record[MAX_WINDOW];
        for (i = 0; i < MAX_WINDOW; i++) {
            window[i] = new Record();
            window[i].numAcks = 0;
            window[i].empty = true;
        }
        eof = false;
    }

} /* End of Class */

class Record {
    Packet packet;
    long sendTime;
    boolean resent;
    boolean empty;

    int numAcks;
}
