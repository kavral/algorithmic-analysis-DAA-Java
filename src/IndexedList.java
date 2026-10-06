/** The indexed operations shared by the two custom lists. Null values are allowed. */
public interface IndexedList<T> {
    int size();
    void add(T value);
    void add(int index, T value);
    T remove(int index);
    T get(int index);
    boolean contains(T value);
}
