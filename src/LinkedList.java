import java.util.Objects;

/** Doubly linked list with head and tail pointers and traversal from the nearer end. */
public final class LinkedList<T> implements IndexedList<T> {
    private static final class Node<T> {
        T value;
        Node<T> previous;
        Node<T> next;
        Node(T value) { this.value = value; }
    }

    private Node<T> head;
    private Node<T> tail;
    private int size;

    public int size() { return size; }

    public void add(T value) { add(size, value); }

    public void add(int index, T value) {
        if (index < 0 || index > size) throw new IndexOutOfBoundsException(index);
        Node<T> next = index == size ? null : node(index);
        Node<T> previous = next == null ? tail : next.previous;
        Node<T> inserted = new Node<>(value);
        inserted.previous = previous;
        inserted.next = next;
        if (previous == null) head = inserted;
        else previous.next = inserted;
        if (next == null) tail = inserted;
        else next.previous = inserted;
        size++;
    }

    public T remove(int index) {
        Node<T> removed = node(index);
        if (removed.previous == null) head = removed.next;
        else removed.previous.next = removed.next;
        if (removed.next == null) tail = removed.previous;
        else removed.next.previous = removed.previous;
        size--;
        return removed.value;
    }

    public T get(int index) { return node(index).value; }

    public boolean contains(T value) {
        for (Node<T> current = head; current != null; current = current.next) {
            if (Objects.equals(current.value, value)) return true;
        }
        return false;
    }

    private Node<T> node(int index) {
        if (index < 0 || index >= size) throw new IndexOutOfBoundsException(index);
        if (index < size / 2) {
            Node<T> current = head;
            for (int i = 0; i < index; i++) current = current.next;
            return current;
        }
        Node<T> current = tail;
        for (int i = size - 1; i > index; i--) current = current.previous;
        return current;
    }

    /** Package-private structural check for tests; never called inside timed regions. */
    boolean isValidStructure() {
        int count = 0;
        Node<T> previous = null;
        for (Node<T> current = head; current != null; current = current.next) {
            if (current.previous != previous || ++count > size) return false;
            previous = current;
        }
        return count == size && previous == tail
                && (head == null || head.previous == null)
                && (tail == null || tail.next == null);
    }
}
