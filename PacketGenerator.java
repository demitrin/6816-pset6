import java.util.zip.CRC32;
import java.util.zip.Checksum;
import java.lang.Object;
import java.lang.Math;

class PacketWorker implements Runnable {
    private final AddressConfigTable table;
    private final PaddedPrimitiveNonVolatile<Boolean> done;
    private final int workerNum;
    private final WaitFreeQueue<Packet>[] queues;
    private long fingerprint = 0;

    public PacketWorker(PaddedPrimitiveNonVolatile<Boolean> done, int workerNum,
                                    WaitFreeQueue<Packet>[] queues, AddressConfigTable table) {
        this.table = table;
        this.done = done;
        this.queues = queues;
        this.workerNum = workerNum;
    }

    public void run() {
        Packet pkt;
        while (!done.value || !queues[workerNum].isEmpty()) {
            try {
                pkt = queues[workerNum].deq();
                if (pkt.type == Packet.MessageType.ConfigPacket) {
                    table.insert(pkt.config.address, pkt.config.addressBegin,
                            pkt.config.addressEnd, pkt.config.acceptingRange, pkt.config.personaNonGrata);
                } else {
                    if (table.isPacketAllowed(pkt.header.source, pkt.header.dest)) {
                        fingerprint += Fingerprint.getFingerprint(pkt.body.iterations, pkt.body.seed);
                    }
                }
            } catch (EmptyException e) {}
        }
    }
}


class Dispatcher implements Runnable {
    PaddedPrimitiveNonVolatile<Boolean> done;
    final PacketGenerator gen;
    long totalPackets = 0;
    final int numSources;
    final WaitFreeQueue<Packet>[] queues;

    public Dispatcher(PaddedPrimitiveNonVolatile<Boolean> done, PacketGenerator gen,
                      int numSources, WaitFreeQueue<Packet>[] queues) {

        this.done = done;
        this.gen = gen;
        this.numSources = numSources;
        this.queues = queues;
    }

    public void run() {
        Packet tmp;
        int queueNum = 0;

        while (!done.value || queueNum != 0) {
            for (int i = 0; i < numSources; i++) {
                tmp = gen.getPacket();

                WaitFreeQueue<Packet> myQueue = queues[queueNum];

                while (true) {
                    if (!myQueue.isFull()) {
                        try {
                            myQueue.enq(tmp);
                            totalPackets++;
                            break;
                        } catch (FullException e) {
                        }
                    }
                }

                queueNum = (queueNum + 1) % queues.length;
            }
        }
    }
}
class PacketGeneratorApp {
    @SuppressWarnings({"unchecked"})
    public static void main(String[] args) {
        final int numAddressesLog = Integer.parseInt(args[0]);
        final int numTrainsLog = Integer.parseInt(args[1]);
        final double meanTrainSize = Double.parseDouble(args[2]);
        final double meanTrainsPerComm = Double.parseDouble(args[3]);
        final int meanWindow = Integer.parseInt(args[4]);
        final int meanCommsPerAddress = Integer.parseInt(args[5]);
        final int meanWork = Integer.parseInt(args[6]);
        final double configFraction = Double.parseDouble(args[7]);
        final double pngFraction = Double.parseDouble(args[8]);
        final double acceptingFraction = Double.parseDouble(args[9]);
        final int numWorkers = Integer.parseInt(args[10]);
        final int numMilliseconds = Integer.parseInt(args[11]);
        PacketGenerator gen = new PacketGenerator(
                numAddressesLog,
                numTrainsLog,
                meanTrainSize,
                meanTrainsPerComm,
                meanWindow,
                meanCommsPerAddress,
                meanWork,
                configFraction,
                pngFraction,
                acceptingFraction
        );
        AddressConfigTable table = new AddressConfigTable(numAddressesLog);
        // initialize the table with config packets
        double a = Math.pow(Math.pow(2, numAddressesLog), 3. / 2);
        Packet pkt;
        for (int i = 0; i < a; i++) {
            pkt = gen.getConfigPacket();
            table.insert(pkt.config.address, pkt.config.addressBegin,
                    pkt.config.addressEnd, pkt.config.acceptingRange, pkt.config.personaNonGrata);
        }
        System.out.println("Finished the initial config");
        // initialize queues for workers
        WaitFreeQueue<Packet>[] queues = new WaitFreeQueue[numWorkers];
        for (int i = 0; i < queues.length; i++) {
            // we can have 256 packets in the flight at once
            // split them evenly across all workers
            queues[i] = new WaitFreeQueue<>((256 - numWorkers) / numWorkers);
        }

        // allocate and initialize locks and any signals used to marshal threads (eg. done signals)
        PaddedPrimitiveNonVolatile<Boolean> done = new PaddedPrimitiveNonVolatile<>(false);
        //
        // allocate and inialize Dispatcher and Worker threads
        Dispatcher dispatchData = new Dispatcher(done, gen, numWorkers, queues);
        Thread dispatchThread = new Thread(dispatchData);

        Thread workerThreads[] = new Thread[queues.length];
        for (int i = 0; i < workerThreads.length; i++) {
            PacketWorker workerData = new PacketWorker(done, i, queues, table);
            workerThreads[i] = new Thread(workerData);
        }

        // call .start() on your Workers
        for (Thread workerThread : workerThreads)
            workerThread.start();

        StopWatch timer = new StopWatch();
        // start the timer
        timer.startTimer();
        // call .start() on your Dispatcher
        dispatchThread.start();

        try {
            Thread.sleep(numMilliseconds);
        } catch (InterruptedException ignore) {}

        // assert signals to stop Dispatcher
        // call .join() on Dispatcher
        done.value = true;
        try {
            dispatchThread.join();
        } catch (InterruptedException e) {
            System.out.println("broke in dispatcher join");
        }

        // assert signals to stop Workers - they are responsible for leaving
        // the queues empty
        //
        // call .join() for each Worker
        //
        try {
            for (Thread workerThread : workerThreads) {
                workerThread.join();
            }
        } catch (InterruptedException e) {
            System.out.println("broke in worker join");
        }

        timer.stopTimer();
        final long totalCount = dispatchData.totalPackets;
        // report the total number of packets processed and total time
        System.out.println("total time: " + timer.getElapsedTime());
        System.out.println("total count: " + totalCount);
        System.out.println("Packets per ms: " + (totalCount / timer.getElapsedTime()));
    }
}

class PacketGenerator {
    final AddressPairGenerator pairGen;
    final ExponentialGenerator expGen;
    final UniformGenerator uniGen = new UniformGenerator();
    final int mask; // numTrains - 1
    final int addressesMask;
    final double meanTrainSize;
    final double meanTrainsPerComm;
    final double meanWork;
    final double pngFraction;
    final double acceptingFraction;
    int timeToNextConfigPacket = 0;
    int lastConfigAddress;
    int numConfigPackets = 0;
    int configAddressMask;
    PacketStruct[] trains;

    public PacketGenerator(
            int numAddressesLog,
            int numTrainsLog,
            double meanTrainSize,
            double meanTrainsPerComm,
            int meanWindow,
            int meanCommsPerAddress,
            int meanWork,
            double configFraction,
            double pngFraction,
            double acceptingFraction) {
        this.expGen = new ExponentialGenerator((1.0d / configFraction) - 1);
        this.pairGen = new AddressPairGenerator(meanCommsPerAddress,
                numAddressesLog, (double) meanWindow);
        this.mask = (1 << numTrainsLog) - 1;
        this.addressesMask = (1 << numAddressesLog) - 1;
        this.meanTrainSize = meanTrainSize;
        this.meanTrainsPerComm = meanTrainsPerComm;
        this.meanWork = (double) meanWork;
        this.lastConfigAddress = pairGen.getPair().source;
        this.configAddressMask = (1 << (numAddressesLog >> 1)) - 1;
        this.pngFraction = pngFraction;
        this.acceptingFraction = acceptingFraction;
        this.trains = new PacketStruct[mask + 1];
        for (int i = 0; i <= mask; i++) {
            this.trains[i] = new PacketStruct(pairGen.getPair(),
                    expGen.getRand(meanTrainSize), expGen.getRand(meanTrainsPerComm),
                    expGen.getRand(this.meanWork), uniGen.getRand());
        }
    }

    public Packet getPacket() {
        if (timeToNextConfigPacket == 0) {
            numConfigPackets++;
            timeToNextConfigPacket = expGen.getRand();
            return getConfigPacket();
        } else
            return getDataPacket();
    }

    public Packet getConfigPacket() {
        lastConfigAddress = pairGen.getPair().source;
        int addressBegin = uniGen.getRand(addressesMask - configAddressMask);
        return new Packet(new Config(lastConfigAddress, uniGen.getUnitRand() < pngFraction,
                uniGen.getUnitRand() < acceptingFraction, addressBegin,
                uniGen.getRand(addressBegin + 1, addressBegin + configAddressMask)));
    }

    public Packet getDataPacket() {
        if (timeToNextConfigPacket > 0)
            timeToNextConfigPacket--;
        int trainIndex = uniGen.getRand() & mask;
        PacketStruct pkt = trains[trainIndex];
        Packet packet = new Packet(
                new Header(pkt.pair.source, pkt.pair.dest, pkt.sequenceNumber, pkt.trainSize, pkt.tag),
                new Body(expGen.getRand(pkt.meanWork), uniGen.getRand()));
        pkt.sequenceNumber++;
        if (pkt.sequenceNumber == pkt.trainSize) {// this was the last packet
            pkt.sequenceNumber = 0;
            pkt.trainNumber++;
        }
        if (pkt.trainNumber == pkt.totalTrains) {// this was the last train
            trains[trainIndex] = new PacketStruct(pairGen.getPair(),
                    expGen.getRand(meanTrainSize), expGen.getRand(meanTrainsPerComm),
                    expGen.getRand(meanWork), uniGen.getRand());
        }
        return packet;
    }
}

class PacketStruct {
    final AddressPair pair;
    final int trainSize;
    final int totalTrains;
    final double meanWork;
    final int tag;
    int sequenceNumber = 0;
    int trainNumber = 0;

    public PacketStruct(AddressPair pair, int trainSize, int totalTrains,
                        double meanWork, int tag) {
        this.pair = pair;
        this.trainSize = trainSize;
        this.totalTrains = totalTrains;
        this.meanWork = meanWork;
        this.tag = tag;
    }
}

class AddressPairTestApp {
    public static void main(String[] args) {
        AddressPairGenerator gen = new AddressPairGenerator(3, 5, 2.0d);
        UniformGenerator uniGen = new UniformGenerator();
        for (int i = 0; i < 20; i++) {
            AddressPair pair = gen.getPair();
            System.out.println(pair.source + ", " + pair.dest);
            double tmp = uniGen.getUnitRand();
            System.out.println(tmp);
        }
    }
}


class AddressPairGenerator {
    final double speed;
    final int mask;
    final int logSize;
    int source;
    int dest;
    double sourceResidue;
    double destResidue;
    ExponentialGenerator expGen;
    UniformGenerator uniGen;

    public AddressPairGenerator(int meanCommsPerAddress, int logSize, double mean) {
        this.speed = 2.0d / ((double) meanCommsPerAddress);
        this.mask = (1 << logSize) - 1;
        this.logSize = logSize;
        this.source = 0;
        this.dest = 0;
        this.sourceResidue = 0.0d;
        this.destResidue = 0.0d;
        this.expGen = new ExponentialGenerator(mean);
        this.uniGen = new UniformGenerator();
    }

    public AddressPair getPair() {
        sourceResidue = sourceResidue + speed * uniGen.getUnitRand();
        destResidue = destResidue + speed * uniGen.getUnitRand();
        while (sourceResidue > 0.0d) {
            source = (source + 1) & mask;
            sourceResidue = sourceResidue - 1.0d;
        }
        while (destResidue > 0.0d) {
            dest = (dest + mask) & mask; // he's walking backward...
            destResidue = destResidue - 1.0d;
        }
        return new AddressPair(uniGen.mangle(1 + ((source + expGen.getRand()))) & mask,
                uniGen.mangle(1 + ((dest + expGen.getRand()))) & mask);
    }
}

class AddressPair {
    final int source;
    final int dest;

    public AddressPair(int source, int dest) {
        this.source = source;
        this.dest = dest;
    }
}

class Packet {
    public enum MessageType {ConfigPacket, DataPacket}

    final Config config;
    final Header header;
    final Body body;
    final MessageType type;

    public Packet(Config config) {
        this.config = config;
        this.header = null;
        this.body = null;
        this.type = MessageType.ConfigPacket;
    }

    public Packet(Header header, Body body) {
        this.config = null;
        this.header = header;
        this.body = body;
        this.type = MessageType.DataPacket;
    }

    public void printPacket() {
        if (type == MessageType.ConfigPacket) {
            System.out.println("CONFIG: " + config.address + " <" + config.personaNonGrata +
                    "," + config.acceptingRange + ">" + " [" + config.addressBegin +
                    "," + config.addressEnd + ")");
        } else {
            System.out.println("data:   " + "<" + header.source + "," + header.dest +
                    ">" + " " + header.sequenceNumber + "/" + header.trainSize + " (" +
                    header.tag + ")");
        }
    }
}

class Config {
    final int address;
    final boolean personaNonGrata;
    final boolean acceptingRange;
    final int addressBegin;
    final int addressEnd;

    public Config(int address, boolean personaNonGrata, boolean acceptingRange,
                  int addressBegin, int addressEnd) {
        this.address = address;
        this.personaNonGrata = personaNonGrata;
        this.acceptingRange = acceptingRange;
        this.addressBegin = addressBegin;
        this.addressEnd = addressEnd;
    }
}

class Header {
    final int source;
    final int dest;
    final int sequenceNumber;
    final int trainSize;
    final int tag;

    public Header(int source, int dest, int seq, int trainSize, int tag) {
        this.source = source;
        this.dest = dest;
        this.sequenceNumber = seq;
        this.trainSize = trainSize;
        this.tag = tag;
    }
}

class Body {
    final long iterations;
    final long seed;

    public Body() {
        iterations = 0;
        seed = 0;
    }

    public Body(long iterations, long seed) {
        this.iterations = iterations;
        this.seed = seed;
    }
}
