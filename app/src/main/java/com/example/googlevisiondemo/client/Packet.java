package com.example.googlevisiondemo.client;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Random;

public class Packet {

/*
Packet setup:
don't care (5 bits)
isSyn (1 bit)
isAck (1 bit)
isFin (1 bit)
seqnum (4 bytes)
acknum (4 bytes)

remaining data

checksum (1 byte)
*/

    /* ---------------------------- S e t t i n g s ----------------------------- */

    /*
    Set the probability of a bit error.

        !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
        Warning: any either probability is 100%, it will be an infinite loop!
        !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
    */
    public final static int

            // probability of bit errors (% of 100)
            P_SERVER_BIT_ERROR = 0,        // for server
            P_CLIENT_BIT_ERROR = 0,        // for client

    // probability of packet loss (% of 100)
    P_SERVER_PACKET_LOSS = 0,      // for server
            P_CLIENT_PACKET_LOSS = 0;      // for client

    /* --------------------------- C o n s t a n t s ---------------------------- */

    public final static int
            NUM_IDS              = 2,       // number of possible IDs are 0 and 1
            MIN_PACKET_LENGTH    = 9,       // every DatagramPacket requires a length of
    // two -- a sequence # and a checksum
    BUFFER_LENGTH        = 65536,    // length of temporary packet buffer

    // error types
    NO_ERROR             = 0,
            BIT_ERROR            = 1,
            PACKET_LOSS          = 2,       // aka timeout
            SIZE_OF_INT          = 4,
            HEADER_SIZE          = MIN_PACKET_LENGTH;

    public final static byte

            // sequence numbers
            FILE_HEADER          = 1,       // first packet to upload of file (filename)
            SEQNUM_START         = 2,       // first sequence number

    DIVIDER              = 16;      // divides sequence number hex string from
    // packet buffer data

    private final static Random
            RANDOM               = new Random();    // random seed

    /* ----------------------- C l a s s    M e m b e r s ----------------------- */

    private     InetAddress     address;        // IP address of sender
    private     int             port;           // port of sender

    private     boolean         isSyn;          // synchronise packet?
    private     boolean         isAck;          // acknowledgement packet?
    private     boolean         isFin;          // final packet?

    private     int             seqNum;         // sequence number
    private     int             ackNum;         // acknowledgement number

    private     byte[]          buffer;         // data (id removed) inside packet
    private     int             error;          // checksum error?

    /* ----------------------- C l a s s    M e t h o d s ----------------------- */

    /**
     * @brief constructor
     */
    public Packet() {
        reset();
    }

    /**
     * @brief resets local variables to default values
     */
    public void reset() {
        address = null;
        port = -1;
        isSyn = false;
        isAck = false;
        isFin = false;
        seqNum = -1;
        ackNum = -1;
        buffer = null;
        error = NO_ERROR;

    }

    /* --------------- T r a n s m i t t e r  /  R e c e i v e r ---------------- */

    public static boolean send(DatagramSocket socket, Packet packet,
                               int P_bitError,  int P_packetLoss) {
        DatagramPacket pkt;

        // if no packet loss, send packet
        if (RANDOM.nextInt(100) >= P_packetLoss) {
            pkt = createPacket(packet, P_bitError);
            try {
                socket.send(pkt);
            } catch (IOException e) {
                return false;
            }
        }
        return true;
    }

    public static void receive(DatagramSocket socket, Packet packet) {
        DatagramPacket pkt;
        Packet temp;

        // initialise packet buffer
        pkt = new DatagramPacket(
                new byte[BUFFER_LENGTH], BUFFER_LENGTH);

        // receive packet
        try {
            socket.receive(pkt);

            // parse packet
            temp = parsePacket(pkt);

            // copy packet
            packet.setAddress(pkt.getAddress());
            packet.setPort(pkt.getPort());
            packet.setSyn(temp.isSyn());
            packet.setAck(temp.isAck());
            packet.setFin(temp.isFin());
            packet.setSequenceNumber(temp.getSequenceNumber());
            packet.setAcknowledgementNumber(temp.getAcknowledgementNumber());
            packet.setData(temp.getData());
            packet.error = temp.error;

        } catch (IOException err) {
            packet.error = Packet.PACKET_LOSS;
        }
    }

    public static void receive(DatagramSocket socket, Packet packet, long timeout) {
        long start;

        start = System.currentTimeMillis();
        do {
            receive(socket, packet);
        } while (packet.getErrorCode() == Packet.PACKET_LOSS
                && (System.currentTimeMillis() - start) < timeout);
    }

    /* ----------------------------- P a r s e r s ------------------------------ */

    /**
     * @brief parses this packet from a datagram packet
     * @param[in] "DatagramPacket pkt" the datagram packet to parse
     * @return Packet created, or null on checksum error
     */
    private static Packet parsePacket(DatagramPacket pkt) {

        Packet      ret;            // return value
        byte        checksum;       // rolling checksum of bytes
        int         i;
        byte        temp[];

        // error check
        if (pkt == null || pkt.getData() == null) {
            return null;
        }

        // initialise and parse sender data
        ret = new Packet();
        ret.address = pkt.getAddress();
        ret.port = pkt.getPort();
        checksum = 0;
        temp = new byte[SIZE_OF_INT]; // size of int

        // error check
        if (pkt.getLength() < MIN_PACKET_LENGTH) {
            ret.error = BIT_ERROR;
            return ret;
        }

        // read first byte for syn AND ack
        ret.isSyn = ((pkt.getData()[0] & 0x01) != 0);
        ret.isAck = ((pkt.getData()[0] & 0x02) != 0);
        ret.isFin = ((pkt.getData()[0] & 0x04) != 0);
        checksum ^= pkt.getData()[0];

        // read four bytes to get sequence number
        for (i = 0; i < SIZE_OF_INT; i++) {
            temp[i] = pkt.getData()[i + 1];
            checksum ^= temp[i];
        }
        ret.seqNum = ByteBuffer.wrap(temp).getInt();

        // read four bytes to get ack number
        for (i = 0; i < SIZE_OF_INT; i++) {
            temp[i] = pkt.getData()[i + 1 + SIZE_OF_INT];
            checksum ^= temp[i];
        }
        ret.ackNum = ByteBuffer.wrap(temp).getInt();

        // read data bytes into buffer
        ret.buffer = new byte[pkt.getLength() - 1 - HEADER_SIZE];
        for (i = HEADER_SIZE; i < pkt.getLength() - 1; i++) {
            ret.buffer[i - HEADER_SIZE] = pkt.getData()[i];
            checksum ^= ret.buffer[i - HEADER_SIZE];
        }

        // read checksum
        checksum ^= pkt.getData()[pkt.getLength() - 1];
        if (checksum != 0) {
            ret.error = BIT_ERROR;
            return ret;
        }

        ret.error = NO_ERROR;
        return ret;
    }

    /**
     * @brief creates a datagram packet from Packet
     * @param[in] "Packet pkt" packet
     * @param[in] "int Perror" probability of bit error
     * @return DatagramPacket created (null on error)
     * @note packet is header[] + divider + data[] + checksum
     */
    private static DatagramPacket createPacket(Packet pkt, int Perror) {
        byte        data[];         // buffer for DatagramPacket
        byte        checksum;       // checksum of data bytes and ID
        int         i;
        byte        temp[];

        // error check
        if (pkt == null || pkt.buffer == null) {
            return null;
        }

        // initialise
        data = new byte[pkt.buffer.length + HEADER_SIZE + 1];
        checksum = 0;

        // write isSyn and isAck
        data[0] = 0;
        if (pkt.isSyn) {
            data[0] |= 0x01;
        }
        if (pkt.isAck) {
            data[0] |= 0x02;
        }
        if (pkt.isFin) {
            data[0] |= 0x04;
        }
        checksum ^= data[0];

        // write sequence number
        temp = ByteBuffer.allocate(SIZE_OF_INT).putInt(pkt.seqNum).array();
        for (i = 0; i < SIZE_OF_INT; i++) {
            data[i + 1] = temp[i];
            checksum ^= temp[i];
        }

        // write ack number
        temp = ByteBuffer.allocate(SIZE_OF_INT).putInt(pkt.ackNum).array();
        for (i = 0; i < SIZE_OF_INT; i++) {
            data[i + 1 + SIZE_OF_INT] = temp[i];
            checksum ^= temp[i];
        }

        // copy all data bytes
        for (i = 0; i < pkt.buffer.length; i++) {
            data[i + 1 + SIZE_OF_INT + SIZE_OF_INT] = pkt.buffer[i];
            checksum ^= pkt.buffer[i];
        }

        // write checksum
        data[data.length - 1] = checksum;

        // create error
        if (Perror > RANDOM.nextInt(100)) {
            // change a random byte to a random value
            data[
                    RANDOM.nextInt(pkt.buffer.length + HEADER_SIZE)
                    ] = (byte)(RANDOM.nextInt() & 0xFF);
        }

        return new DatagramPacket(data, 0, data.length, pkt.address, pkt.port);
    }

    /* ---------------------- G e t t e r    M e t h o d s ---------------------- */

    public InetAddress getAddress() {
        return address;
    }

    public int getPort() {
        return port;
    }

    public boolean isSyn() {
        return isSyn;
    }

    public boolean isAck() {
        return isAck;
    }

    public boolean isFin() {
        return isFin;
    }

    public int getSequenceNumber() {
        return seqNum;
    }

    public int getAcknowledgementNumber() {
        return ackNum;
    }

    public byte[] getData() {
        return buffer;
    }

    public int getHeaderLength() {
        return HEADER_SIZE;
    }

    public int getDataLength() {
        if (getData() == null) {
            return 0;
        } else {
            return getData().length;
        }
    }

    /* ---------------------- S e t t e r    M e t h o d s ---------------------- */

    public void setAddress(InetAddress addr) {
        this.address = addr;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public void setSyn(boolean isSyn) {
        this.isSyn = isSyn;
    }

    public void setAck(boolean isAck) {
        this.isAck = isAck;
    }

    public void setFin(boolean isFin) {
        this.isFin = isFin;
    }

    public void setSequenceNumber(int seqNum) {
        this.seqNum = seqNum;
    }

    public void setAcknowledgementNumber(int ackNum) {
        this.ackNum = ackNum;
    }

    public void setData(byte[] data) {
        buffer = null;
        buffer = data;
    }

    public void setData(byte[] data, int len) {
        buffer = null;
        buffer = new byte[len];
        System.arraycopy(data, 0, buffer, 0, len);
    }

    public int getErrorCode() {
        return error;
    }

    public boolean hasError() {
        return (error != NO_ERROR);
    }

} /* End of Class */
