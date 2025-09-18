import java.util.function.Function;
import java.util.function.Supplier;

public abstract class Try<T> {

    private Try() {}

    public abstract T get() throws Throwable;

    public abstract boolean isSuccess();

    public abstract boolean isFailure();

    public static <T> Try<T> of(Supplier<T> supplier) {
        try {
            return success(supplier.get());
        } catch (Throwable t) {
            return failure(t);
        }
    }

    public static <T> Try<T> failure(Throwable t) {
        return new Failure<>(t);
    }

    public static <T> Try<T> success(T value) {
        return new Success<>(value);
    }

    public <U> Try<U> map(Function<T, U> mapper) {
        if (isSuccess()) {
            try {
                return success(mapper.apply(get()));
            } catch (Throwable t) {
                return failure(t);
            }
        } else {
            return failure(getException());
        }
    }

    public <U> Try<U> flatMap(Function<T, Try<U>> mapper) {
        if (isSuccess()) {
            try {
                return mapper.apply(get());
            } catch (Throwable t) {
                return failure(t);
            }
        } else {
            return failure(getException());
        }
    }


    public Throwable getException() {
        if (isFailure()) {
            try {
                get();
            } catch (Throwable t) {
                return t;
            }
        }
        return null;
    }



    public static final class Failure<T> extends Try<T> {
        private final Throwable exception;

        public Failure(Throwable value){
            this.exception = value;
        }

        @Override
        public T get() throws Throwable {
            throw exception;
        }

        @Override
        public boolean isSuccess() {
            return false;
        }

        @Override
        public boolean isFailure() {
            return true;
        }
    }


    public static final class Success<T> extends Try<T> {

        private final T value;

        public Success(T value) {
            this.value = value;
        }

        @Override
        public T get() throws Throwable {
            return value;
        }

        @Override
        public boolean isSuccess() {
            return true;
        }

        @Override
        public boolean isFailure() {
            return false;
        }
    }


    public static void main(String[] args) {

        Try<Integer> successTry = Try.of(() -> 42);
        System.out.println("isSuccess: " + successTry.isSuccess()); // true
        System.out.println("get: " + safeGet(successTry));          // 42


        Try<Integer> failureTry = Try.of(() -> 1 / 0); // ArithmeticException
        System.out.println("isFailure: " + failureTry.isFailure()); // true
        System.out.println("exception: " + failureTry.getException());


        Try<String> mapped = successTry.map(x -> "Value: " + (x * 2));
        System.out.println("mapped: " + safeGet(mapped)); // "Değer: 84"


        Try<Integer> chained = successTry.flatMap(x -> Try.of(() -> x + 100));
        System.out.println("flatMapped: " + safeGet(chained)); // 142

        Try<Integer> flatMapFail = successTry.flatMap(x -> Try.of(() -> x / 0));
        System.out.println("flatMapped: " + safeGet(chained)); // 142
        System.out.println("mapFail.isFailure: " + flatMapFail.isFailure()); // true
        System.out.println("mapFail exception: " + flatMapFail.getException());

        Try<Integer> mapFail = successTry.map(x -> 10 / 0);
        System.out.println("mapFail.isFailure: " + mapFail.isFailure()); // true
        System.out.println("mapFail exception: " + mapFail.getException());
    }


    private static <T> Object safeGet(Try<T> t) {
        try {
            return t.get();
        } catch (Throwable e) {
            return e.toString();
        }
    }

}


