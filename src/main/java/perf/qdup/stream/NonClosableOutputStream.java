package perf.qdup.stream;

import java.io.IOException;
import java.io.OutputStream;

public class NonClosableOutputStream extends OutputStream {

    private final OutputStream stream;

    public NonClosableOutputStream(OutputStream stream) {
        this.stream = stream;
    }

    @Override
    public void write(int i) throws IOException {
        stream.write(i);
    }

    @Override
    public void write(byte b[]) throws IOException {
        write(b, 0, b.length);
    }

    @Override
    public void write(byte b[], int off, int len) throws IOException {
        stream.write(b, off, len);
    }

    @Override
    public void close() {
    }
}
