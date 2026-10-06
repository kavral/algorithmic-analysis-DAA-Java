import java.util.ArrayList;
import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.function.Supplier;

/** Dependency-free differential and boundary tests. Checks are always enabled. */
public final class Tests {
    private static long checks;

    public static void main(String[] args) {
        testList(DynamicArray::new);
        testList(LinkedList::new);
        testHeap();
        System.out.println("PASS: " + checks + " checks (boundaries, randomized differential tests, invariants)");
    }

    private static void testList(Supplier<IndexedList<Integer>> factory) {
        IndexedList<Integer> list = factory.get();
        equal(0, list.size());
        equal(false, list.contains(null));
        expect(IndexOutOfBoundsException.class, () -> list.get(0));
        expect(IndexOutOfBoundsException.class, () -> list.remove(0));
        expect(IndexOutOfBoundsException.class, () -> list.add(1, 5));
        expect(IndexOutOfBoundsException.class, () -> list.add(-1, 5));
        list.add(null);
        list.add(0, 7);
        list.add(2, 7);
        equal(true, list.contains(null));
        equal(7, list.remove(0));
        equal(7, list.remove(list.size() - 1));
        equal(null, list.remove(0));
        equal(0, list.size());
        checkLinks(list);
        // Cross many resize boundaries, then empty and reuse the structure.
        for (int i = 0; i < 4097; i++) list.add(i);
        for (int i = 4096; i >= 0; i--) equal(i, list.remove(list.size() - 1));
        list.add(42);
        equal(42, list.remove(0));
        for (long seed : new long[] {1, 42, 20260926}) {
            IndexedList<Integer> actual = factory.get();
            java.util.List<Integer> reference = new ArrayList<>();
            Random random = new Random(seed);
            for (int step = 0; step < 12000; step++) {
                Integer value = random.nextInt(10) == 0 ? null : random.nextInt(101) - 50;
                int operation = random.nextInt(5);
                if (reference.isEmpty() || operation == 0) {
                    actual.add(value);
                    reference.add(value);
                } else if (operation == 1 && reference.size() < 150) {
                    int index = random.nextInt(reference.size() + 1);
                    actual.add(index, value);
                    reference.add(index, value);
                } else if (operation == 2 || reference.size() >= 150) {
                    int index = random.nextInt(reference.size());
                    equal(reference.remove(index), actual.remove(index));
                } else if (operation == 3) {
                    int index = random.nextInt(reference.size());
                    equal(reference.get(index), actual.get(index));
                } else {
                    equal(reference.contains(value), actual.contains(value));
                }
                equal(reference.size(), actual.size());
                for (int i = 0; i < reference.size(); i++) equal(reference.get(i), actual.get(i));
                checkLinks(actual);
            }
            expect(IndexOutOfBoundsException.class, () -> actual.get(-1));
            expect(IndexOutOfBoundsException.class, () -> actual.get(actual.size()));
            expect(IndexOutOfBoundsException.class, () -> actual.remove(-1));
            expect(IndexOutOfBoundsException.class, () -> actual.remove(actual.size()));
            expect(IndexOutOfBoundsException.class, () -> actual.add(actual.size() + 1, 0));
            equal(reference.size(), actual.size());
        }
    }

    private static void checkLinks(IndexedList<Integer> list) {
        if (list instanceof LinkedList<?>) equal(true, ((LinkedList<?>) list).isValidStructure());
    }

    private static void testHeap() {
        MinHeap<Integer> heap = new MinHeap<>();
        expect(NoSuchElementException.class, heap::peekMin);
        expect(NoSuchElementException.class, heap::extractMin);
        expect(NullPointerException.class, () -> heap.insert(null));
        equal(0, heap.size());
        for (int[] input : new int[][] {
                {}, {9}, {2, 2, 2}, {1, 2, 3, 4, 5, 6}, {6, 5, 4, 3, 2, 1},
                {Integer.MAX_VALUE, 0, Integer.MIN_VALUE, -1, 0}}) {
            for (int value : input) {
                heap.insert(value);
                equal(true, heap.isValidHeap());
            }
            int[] sorted = input.clone();
            Arrays.sort(sorted);
            for (int value : sorted) {
                equal(value, heap.peekMin());
                equal(value, heap.extractMin());
                equal(true, heap.isValidHeap());
            }
            equal(0, heap.size());
        }
        for (long seed : new long[] {1, 42, 20260926}) {
            Random random = new Random(seed);
            PriorityQueue<Integer> reference = new PriorityQueue<>();
            for (int step = 0; step < 20000; step++) {
                if (reference.isEmpty() || random.nextBoolean()) {
                    int value = random.nextInt(1001) - 500;
                    reference.add(value);
                    heap.insert(value);
                } else equal(reference.remove(), heap.extractMin());
                equal(reference.size(), heap.size());
                equal(true, heap.isValidHeap());
                if (!reference.isEmpty()) equal(reference.element(), heap.peekMin());
            }
            int previous = Integer.MIN_VALUE;
            while (!reference.isEmpty()) {
                int next = heap.extractMin();
                equal(reference.remove(), next);
                equal(true, previous <= next);
                equal(true, heap.isValidHeap());
                previous = next;
            }
        }
    }

    private static void equal(Object expected, Object actual) {
        checks++;
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError("Expected " + expected + ", got " + actual);
        }
    }

    private static void expect(Class<? extends Throwable> type, Runnable action) {
        checks++;
        try {
            action.run();
        } catch (Throwable failure) {
            if (type.isInstance(failure)) return;
            throw new AssertionError("Expected " + type.getSimpleName(), failure);
        }
        throw new AssertionError("Expected " + type.getSimpleName());
    }
}
