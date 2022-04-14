import java.util.concurrent.locks.*;

public class AddressConfigTable {
    private final ReadWriteLock[] locks;
    private final AddressConfig[] configs;
    public AddressConfigTable(int logSize) {
        // it is initialized to the max size. no need to resize
        configs = new AddressConfig[1 << logSize];
        locks = new ReentrantReadWriteLock[1 << logSize];
        for (int i = 0; i < configs.length; i++) {
            configs[i] = new AddressConfig();
        }

        for (int i = 0; i < locks.length; i++) {
            locks[i] = new ReentrantReadWriteLock();
        }
    }

    public boolean isPacketAllowed(int source, int destination) {
        // first check personaNonGrata of source
        int sourceLockIndex = source % locks.length;
        locks[sourceLockIndex].readLock().lock();
        if (configs[source].isPersonaNonGrata()) {
            locks[sourceLockIndex].readLock().unlock();
            return false;
        }

        // then check if source is in the destination interval tree
        int destinationLockIndex = destination % locks.length;
        locks[destinationLockIndex].readLock().lock();
        boolean result = configs[destination].root.isAddressAllowed(source);

        //release locks
        locks[sourceLockIndex].readLock().unlock();
        locks[destinationLockIndex].readLock().unlock();
        return result;
    }

    public void insert(int address, int start, int end, boolean addressAllowed, boolean personaNonGrata) {
        int lockIndex = address % locks.length;
//        System.out.println("inserting " + address + " " +  start + " " + end + " " + addressAllowed);
        locks[lockIndex].writeLock().lock();
        // it is [start, end) therefore we do end-1
        configs[address].root.insert(start, end - 1, addressAllowed);
        configs[address].setPersonaNonGrata(personaNonGrata);
        locks[lockIndex].writeLock().unlock();
    }

    private class AddressConfig {
        private volatile boolean personaNonGrata = false;
        private final IntervalTree root;
        private AddressConfig() {
            root = new IntervalTree();
        }

        private boolean isPersonaNonGrata() {
            return personaNonGrata;
        }

        private void setPersonaNonGrata(boolean png) {
            personaNonGrata = png;
        }
    }
}
