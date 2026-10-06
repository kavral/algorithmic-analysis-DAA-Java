import java.util.NoSuchElementException;
import java.util.Objects;

/** Array-backed binary min-heap. Null elements are rejected. */
public final class MinHeap<T extends Comparable<? super T>> {
    private final DynamicArray<T> elements = new DynamicArray<>();

    public int size() { return elements.size(); }

    public void insert(T value) {
        Objects.requireNonNull(value, "Heap values must not be null");
        elements.add(value);
        int child = size() - 1;
        while (child > 0) {
            int parent = (child - 1) / 2;
            if (elements.get(parent).compareTo(elements.get(child)) <= 0) break;
            swap(parent, child);
            child = parent;
        }
    }

    public T peekMin() {
        if (size() == 0) throw new NoSuchElementException("Heap is empty");
        return elements.get(0);
    }

    public T extractMin() {
        T minimum = peekMin();
        T last = elements.remove(size() - 1);
        if (size() == 0) return minimum;
        elements.set(0, last);
        int parent = 0;
        // A parent below size/2 has at least one child; this avoids index overflow.
        while (parent < size() / 2) {
            int child = 2 * parent + 1;
            if (child + 1 < size()
                    && elements.get(child + 1).compareTo(elements.get(child)) < 0) child++;
            if (elements.get(parent).compareTo(elements.get(child)) <= 0) break;
            swap(parent, child);
            parent = child;
        }
        return minimum;
    }

    private void swap(int a, int b) {
        T value = elements.get(a);
        elements.set(a, elements.get(b));
        elements.set(b, value);
    }

    /** Full invariant check for tests; costs O(n), so excluded from benchmarking. */
    boolean isValidHeap() {
        for (int child = 1; child < size(); child++) {
            if (elements.get((child - 1) / 2).compareTo(elements.get(child)) > 0) return false;
        }
        return true;
    }
}
