import java.util.Objects;

/** Resizable array with doubling growth. Removing elements does not shrink capacity. */
public final class DynamicArray<T> implements IndexedList<T> {
    private Object[] elements = new Object[8];
    private int size;

    public int size() { return size; }

    public void add(T value) { add(size, value); }

    public void add(int index, T value) {
        checkPosition(index);
        ensureCapacity();
        // Shift backward so a destination never destroys an unread source.
        for (int j = size; j > index; j--) elements[j] = elements[j - 1];
        elements[index] = value;
        size++;
    }

    public T remove(int index) {
        checkElement(index);
        T removed = element(index);
        for (int j = index; j < size - 1; j++) elements[j] = elements[j + 1];
        elements[--size] = null;
        return removed;
    }

    public T get(int index) {
        checkElement(index);
        return element(index);
    }

    /** Internal constant-time replacement used by MinHeap. */
    void set(int index, T value) {
        checkElement(index);
        elements[index] = value;
    }

    public boolean contains(T value) {
        for (int i = 0; i < size; i++) {
            if (Objects.equals(elements[i], value)) return true;
        }
        return false;
    }

    private void ensureCapacity() {
        if (size < elements.length) return;
        // Compute in long to avoid silently overflowing the capacity.
        int capacity = (int) Math.min((long) elements.length * 2, Integer.MAX_VALUE - 8L);
        if (capacity <= size) throw new OutOfMemoryError("Array capacity exhausted");
        Object[] larger = new Object[capacity];
        System.arraycopy(elements, 0, larger, 0, size);
        elements = larger;
    }

    @SuppressWarnings("unchecked")
    private T element(int index) { return (T) elements[index]; }

    private void checkElement(int index) {
        if (index < 0 || index >= size) throw new IndexOutOfBoundsException(index);
    }

    private void checkPosition(int index) {
        if (index < 0 || index > size) throw new IndexOutOfBoundsException(index);
    }
}
