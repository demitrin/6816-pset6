


// from java.util.Random
class Fingerprint {
	private final static long m = 0xFFFFFFFFFFFFL;
	private final static long a = 25214903917L;
	private final static long c = 11L;

	static long getFingerprint(long iterations, long startSeed) {
		long seed = startSeed;
		for (long i = 0; i < iterations; i++) {
			seed = (seed * a + c) & m;
		}
		return (seed >> 12) & 0xFFFFL;
	}
}
