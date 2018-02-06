package helper;

import dagger.Lazy;

public class FakeLazy<T> implements Lazy<T> {
    private final T t;
    public FakeLazy(T t) { this.t = t; }

    @Override
    public T get() { return t; }
}
