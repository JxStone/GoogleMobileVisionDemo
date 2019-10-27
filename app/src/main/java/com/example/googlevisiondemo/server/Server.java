package com.example.googlevisiondemo.server;

import java.io.File;
import java.net.DatagramSocket;
import java.net.SocketException;

public class Server extends Thread {

    /* --------------------------- C o n s t a n t s ---------------------------- */

    private final static int

            RECEIVE_PACKET_TRIES    = 5,          // # of errors to accept while trying
            // to receive packet.
            MAX_CLIENTS             = 10,         // maximum # of workers
            // (1 worker per client).
            RCVBFR_SIZE             = 100;        // size of receiver buffer

    /* ----------------------- C l a s s    M e m b e r s ----------------------- */

    private     DatagramSocket      server;     // server socket
    private     Worker              workers[];  // workers to handle clients
    private     File                directory;  // directory to put files in

    private     Packet[]            rcvBfr;     // buffer
    private     int                 bfrStart;   // index in buffer
    private     int                 bfrEnd;
    private     int                 numPkts;    // packets in buffer
    private     Object              mutex;

    /* ----------------------- C l a s s    M e t h o d s ----------------------- */

    /**
     * @brief constructor for the server
     * @param[in] "int port" port to run server on
     * @param[in] "File directory" directory to place uploaded files in
     *
     * First, the constructor error checks to make sure the directory is valid.
     * Then, it binds a datagram socket to the port and it creates an array of
     * workers that have not been initialised.  A worker shall be created for
     * every incoming connection.
     */
    public Server(int port, File directory)
            throws SocketException, IllegalArgumentException {

        int i;

        // create new server socket
        server = new DatagramSocket(port);
        server.setSoTimeout(0);

        // error check directory
        if (!directory.exists() || !directory.isDirectory()) {
            throw new IllegalArgumentException(directory.getName()
                    + " is not a valid directory");
        }
        this.directory = directory;

        // create array of null workers
        workers = new Worker[MAX_CLIENTS];
        for (i = 0; i < MAX_CLIENTS; i++) {
            workers[i] = null;
        }

        mutex = new Object();

        rcvBfr = new Packet[RCVBFR_SIZE];
        bfrStart = 0;
        bfrEnd = 0;
        numPkts = 0;
    }

    public void RunServer() {
        Packet rcv;
        boolean flag;

        // run process that removes packets from rcv bfr
        this.start();

        // run process to receive packets and add them to buffer
        while (true) {

            // receive a packet
            rcv = ReceivePacket();
            if (rcv == null) {
                System.out.println("*** Error: could not receive packet\n\n");
                continue;
            }

            // pend on numPkt to be less than RCVBFR_SIZE
            do {
                synchronized (mutex) {
                    flag = (numPkts >= RCVBFR_SIZE);
                }
            } while (flag);

            // insert packet at tail of circular buffer
            rcvBfr[bfrEnd] = rcv;
            bfrEnd = (bfrEnd + 1) % RCVBFR_SIZE;

            // increment number of packets
            synchronized (mutex) {
                ++numPkts;
            }

        }
    }

    public void run() {
        Packet packet;
        boolean flag;
        int freeSpace;

        packet = null;
        freeSpace = 0;

        while (true) {

            // pend on numPkts to be nonzero
            do {
                synchronized (mutex) {
                    flag = (numPkts < 1);
                }
            } while (flag);

            // remove packet from head of circular buffer
            packet = rcvBfr[bfrStart];
            bfrStart = (bfrStart + 1) % RCVBFR_SIZE;

            // increment number of packets
            synchronized (mutex) {
                --numPkts;
                freeSpace = RCVBFR_SIZE - numPkts;
            }

            // handle packet
            HandlePacket(packet, freeSpace);
        }
    }

    /**
     * @brief handles an incoming packet
     * @return TRUE on success
     *
     * This method will receive a packet.  Next, it will check to see if the
     * packet matches an existing worker.  A worker is responsible to putting
     * UDP data into a file.  If a worker exists for this client, the packet
     * shall be forwarded to that worker.  If no worker exists, a new worker
     * shall be created from the packet.
     */
    public boolean HandlePacket(Packet packet, int RcvWindow) {
        int                 matchFound;     // index of matching worker
        int                 emptyFound;     // index of an empty spot
        int                 i;

        // print packet info
        System.out.printf("Packet received from %s\n", packet.getAddress().getHostName());
        System.out.printf("  Rcv Window : %d\n", RcvWindow);
        System.out.printf("        Port : %d\n", packet.getPort());
        System.out.printf("      Length : %d bytes\n",
                packet.getDataLength() + (packet.getHeaderLength() + 1));
        System.out.printf("        SYN? : %s\n", packet.isSyn() ? "Yes" : "No");
        System.out.printf("        ACK? : %s\n", packet.isAck() ? "Yes" : "No");
        System.out.printf("        FIN? : %s\n", packet.isFin() ? "Yes" : "No");
        System.out.printf("  Sequence # : %d\n", packet.getSequenceNumber());
        System.out.printf("       Ack # : %d\n", packet.getAcknowledgementNumber());

        // search for packet match and an empty space
        matchFound = -1; // no matches found yet
        emptyFound = -1; // no availabilities found yet
        for (i = 0; i < MAX_CLIENTS; i++) {
            if (workers[i] == null) {               // is this spot available?
                emptyFound = i;
            } else {
                if (workers[i].matches(packet)) {   // is this worker a match?
                    matchFound = i;
                }
            }
        }

        // forward packet appropriately
        if (matchFound >= 0) {
            // if match found, fwd to existing worker
            forwardPacketToWorker(matchFound, packet, RcvWindow);
        } else if (emptyFound >= 0) {
            // if no match, create worker at an available spot in worker array
            newWorker(emptyFound, packet, RcvWindow);
        } else {
            // if no match and no more space, indicate error
            System.out.printf("*** Error: no more workers available\n\n");
            return false;
        }

        System.out.flush();

        return true;
    }

    /**
     * @brief forwards a packet to a worker for processing
     * @param[in] "int found" index of worker that is responsible for packet
     * @param[in] "Packet packet" received packet
     *
     * This function prints information about where the data is going.  Then, it
     * passes the packet to the worker with a public method.  If the worker
     * indicates that the end of the file has been reached, free worker and
     * indicate successful file transfer.
     */
    public void forwardPacketToWorker(int found, Packet packet, int freeSpace) {

        // print information
        if (workers[found] != null && workers[found].getFile() != null) {
            System.out.printf("Writing to \"%s\"\n", workers[found].getFile().getName());
        }

        // forward packet to worker
        workers[found].addPacket(packet, freeSpace);

        // check for end of file
        if (workers[found].isDone()) {
            workers[found] = null;
            System.out.printf("  File closed\n");
        }

        System.out.println();
    }

    /**
     * @brief create a new worker
     * @param[in] "int open" index to place worker at (should be open!)
     * @param[in] "DatagramPacket packet" first packet from particular client
     *
     * This method creates a worker at an index.  Status messages shall be
     * printed.
     */
    public void newWorker(int open, Packet packet, int freeSpace) {
        workers[open] = Worker.newWorker(packet, directory, freeSpace);

        // error check
        if (workers[open] != null) {
            System.out.printf("New file created\n");
        }
        System.out.println();
    }

    /**
     * @brief receives a packet from the datagram socket
     * @return packet; null on error
     *
     * This function will simply receive a packet without a timeout.  In the case
     * of an exception, the operation shall be re-tried for a set # of times.
     */
    public Packet ReceivePacket() {
        Packet              packet;     // packet to receive
        int                 attempts;   // number of attempts

        // initialise packet
        packet = new Packet();

        // try to read packet; return from inside loop upon success
        for (attempts = 0; attempts < RECEIVE_PACKET_TRIES; attempts++) {

            // try to read a packet
            Packet.receive(server, packet);
            if (packet.hasError()) {
                if (packet.getErrorCode() == Packet.BIT_ERROR) {
                    System.out.print("*** Checksum Error\n");
                }
            } else {
                return packet;
            }

        } // end of for-loop

        // allow garbage collection
        packet = null;

        return null; // return error
    }

} /* End of Class */
