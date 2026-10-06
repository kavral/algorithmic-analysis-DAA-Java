import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;

/** Controlled single-threaded batch timings; see README for limitations versus JMH. */
public final class Benchmark {
    static final long SEED = 20260926L;
    static final int M = 1000;
    static final int WARMUPS = 3;
    static final int REPETITIONS = 5;
    static volatile long blackhole;
    private record Case(String structure, String workload, int n) {}
    private record Timed(long nanos, long operations, long checksum) {}

    private static final class Input {
        final Integer[] initial;
        final Integer[] heapValues;
        final Integer[] hits = new Integer[M];
        final Integer[] misses = new Integer[M];
        final Integer[] updates = new Integer[M];
        final int[] indices = new int[M];
        Input(int n) {
            Random random = new Random(SEED + n);
            initial = new Integer[n];
            heapValues = new Integer[n];
            for (int i = 0; i < n; i++) {
                initial[i] = i;
                heapValues[i] = random.nextInt();
            }
            for (int i = 0; i < M; i++) {
                indices[i] = random.nextInt(n);
                // Distinct boxing from initial data where Integer caching does not apply.
                hits[i] = random.nextInt(n);
                misses[i] = -1 - random.nextInt(n);
                updates[i] = random.nextInt();
            }
        }
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 2) throw new IllegalArgumentException("Usage: Benchmark output.csv forkNumber");
        Locale.setDefault(Locale.ROOT);
        Path output = Path.of(args[0]);
        int fork = Integer.parseInt(args[1]);
        Files.createDirectories(output.toAbsolutePath().getParent());
        writeEnvironment(output.resolveSibling("environment-" + fork + ".txt"), fork);
        List<Case> cases = new ArrayList<>();
        Map<Integer, Input> inputs = new HashMap<>();
        for (int n : new int[] {1000, 10000, 100000}) {
            inputs.put(n, new Input(n));
            for (String structure : new String[] {"DynamicArray", "LinkedList", "ArrayList", "JavaLinkedList"}) {
                for (String workload : new String[] {"append", "random_get", "contains_hit", "contains_miss",
                        "front_pairs", "middle_pairs", "tail_pairs", "mixed"}) {
                    cases.add(new Case(structure, workload, n));
                }
            }
            for (String structure : new String[] {"MinHeap", "PriorityQueue"}) {
                for (String workload : new String[] {"peek_min", "insert_extract", "build_drain"}) {
                    cases.add(new Case(structure, workload, n));
                }
            }
        }
        Map<String, Long> expected = new HashMap<>();
        Random order = new Random(SEED + fork);
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(output))) {
            writer.println("fork,round,structure,workload,n,m,operations,elapsed_ns,ns_per_op,checksum");
            for (int round = -WARMUPS; round < REPETITIONS; round++) {
                Collections.shuffle(cases, order);
                for (Case c : cases) {
                    Input input = inputs.get(c.n());
                    Timed result = c.structure().equals("MinHeap") || c.structure().equals("PriorityQueue")
                            ? timeHeap(c, input) : timeList(c, input);
                    blackhole = result.checksum();
                    String key = c.workload() + ":" + c.n();
                    Long previous = expected.putIfAbsent(key, result.checksum());
                    if (previous != null && previous.longValue() != result.checksum()) {
                        throw new AssertionError("Benchmark checksum mismatch: " + c);
                    }
                    if (round >= 0) {
                        int m = c.workload().equals("append") || c.workload().equals("build_drain") ? c.n() : M;
                        writer.printf("%d,%d,%s,%s,%d,%d,%d,%d,%.6f,%d%n", fork, round + 1,
                                c.structure(), c.workload(), c.n(), m, result.operations(), result.nanos(),
                                (double) result.nanos() / result.operations(), result.checksum());
                    }
                }
                writer.flush();
                System.out.printf("Fork %d: %s %d/%d complete%n", fork,
                        round < 0 ? "warmup" : "measurement", round < 0 ? round + WARMUPS + 1 : round + 1,
                        round < 0 ? WARMUPS : REPETITIONS);
            }
            if (writer.checkError()) throw new IOException("Could not write benchmark output");
        }
    }

    private static Timed timeList(Case c, Input input) {
        IndexedList<Integer> list = switch (c.structure()) {
            case "DynamicArray" -> new DynamicArray<>();
            case "LinkedList" -> new LinkedList<>();
            case "ArrayList" -> new JavaListAdapter(new ArrayList<>());
            case "JavaLinkedList" -> new JavaListAdapter(new java.util.LinkedList<>());
            default -> throw new IllegalArgumentException(c.structure());
        };
        if (!c.workload().equals("append")) for (Integer value : input.initial) list.add(value);
        long checksum = 0;
        long operations;
        long start = System.nanoTime();
        switch (c.workload()) {
            case "append":
                for (Integer value : input.initial) list.add(value);
                operations = c.n();
                break;
            case "random_get":
                for (int index : input.indices) checksum += list.get(index);
                operations = M;
                break;
            case "contains_hit":
                for (Integer value : input.hits) checksum += list.contains(value) ? 1 : 0;
                operations = M;
                break;
            case "contains_miss":
                for (Integer value : input.misses) checksum += list.contains(value) ? 1 : 0;
                operations = M;
                break;
            case "front_pairs", "middle_pairs", "tail_pairs":
                int index = c.workload().equals("front_pairs") ? 0
                        : c.workload().equals("middle_pairs") ? c.n() / 2 : c.n();
                for (Integer value : input.updates) {
                    list.add(index, value);
                    checksum += list.remove(index);
                }
                operations = 2L * M;
                break;
            case "mixed":
                for (int i = 0; i < M; i++) {
                    if (i % 10 < 8) checksum += list.get(input.indices[i]);
                    else if (i % 10 == 8) checksum += list.contains(input.hits[i]) ? 1 : 0;
                    else {
                        list.add(input.indices[i], input.updates[i]);
                        checksum += list.remove(input.indices[i]);
                    }
                }
                operations = M + M / 10;
                break;
            default: throw new IllegalArgumentException(c.workload());
        }
        long elapsed = System.nanoTime() - start;
        // Consume final state outside timing, including the append workload.
        checksum += list.size() + list.get(list.size() - 1);
        return new Timed(elapsed, operations, checksum);
    }

    private interface HeapOps {
        void insert(Integer value);
        int peek();
        int extract();
        int size();
    }

    private static Timed timeHeap(Case c, Input input) {
        HeapOps heap;
        if (c.structure().equals("MinHeap")) {
            MinHeap<Integer> custom = new MinHeap<>();
            heap = new HeapOps() {
                public void insert(Integer value) { custom.insert(value); }
                public int peek() { return custom.peekMin(); }
                public int extract() { return custom.extractMin(); }
                public int size() { return custom.size(); }
            };
        } else {
            PriorityQueue<Integer> standard = new PriorityQueue<>();
            heap = new HeapOps() {
                public void insert(Integer value) { standard.add(value); }
                public int peek() { return standard.element(); }
                public int extract() { return standard.remove(); }
                public int size() { return standard.size(); }
            };
        }
        if (!c.workload().equals("build_drain")) for (Integer value : input.heapValues) heap.insert(value);
        long checksum = 0;
        long operations;
        long start = System.nanoTime();
        switch (c.workload()) {
            case "peek_min":
                for (int i = 0; i < M; i++) checksum += heap.peek();
                operations = M;
                break;
            case "insert_extract":
                for (Integer value : input.updates) {
                    heap.insert(value);
                    checksum += heap.extract();
                }
                operations = 2L * M;
                break;
            case "build_drain":
                for (Integer value : input.heapValues) heap.insert(value);
                for (int i = 0; i < c.n(); i++) checksum = 31 * checksum + heap.extract();
                operations = 2L * c.n();
                break;
            default: throw new IllegalArgumentException(c.workload());
        }
        long elapsed = System.nanoTime() - start;
        checksum += heap.size();
        return new Timed(elapsed, operations, checksum);
    }

    private static final class JavaListAdapter implements IndexedList<Integer> {
        private final List<Integer> delegate;
        JavaListAdapter(List<Integer> delegate) { this.delegate = delegate; }
        public int size() { return delegate.size(); }
        public void add(Integer value) { delegate.add(value); }
        public void add(int index, Integer value) { delegate.add(index, value); }
        public Integer remove(int index) { return delegate.remove(index); }
        public Integer get(int index) { return delegate.get(index); }
        public boolean contains(Integer value) { return delegate.contains(value); }
    }

    private static void writeEnvironment(Path path, int fork) throws IOException {
        StringBuilder info = new StringBuilder();
        info.append("started_utc=").append(Instant.now()).append('\n');
        for (String property : new String[] {"java.version", "java.vendor", "java.vm.name", "os.name", "os.version", "os.arch"}) {
            info.append(property).append('=').append(System.getProperty(property)).append('\n');
        }
        info.append("processor=").append(System.getenv("PROCESSOR_IDENTIFIER")).append('\n');
        info.append("available_processors=").append(Runtime.getRuntime().availableProcessors()).append('\n');
        info.append("max_heap_bytes=").append(Runtime.getRuntime().maxMemory()).append('\n');
        info.append("jvm_arguments=").append(ManagementFactory.getRuntimeMXBean().getInputArguments()).append('\n');
        info.append("seed=").append(SEED).append("\nfork=").append(fork)
                .append("\nwarmups=").append(WARMUPS).append("\nrepetitions=").append(REPETITIONS).append('\n');
        Files.writeString(path, info);
    }
}
