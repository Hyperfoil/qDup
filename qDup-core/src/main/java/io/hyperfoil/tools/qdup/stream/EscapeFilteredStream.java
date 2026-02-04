package io.hyperfoil.tools.qdup.stream;

import org.jline.jansi.AnsiColors;
import org.jline.jansi.AnsiMode;
import org.jline.jansi.AnsiType;
import org.jline.jansi.io.AnsiOutputStream;
import org.jline.jansi.io.AnsiProcessor;
import org.jboss.logging.Logger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;

public class EscapeFilteredStream extends MultiStream {

    private final static Logger logger = Logger.getLogger(MethodHandles.lookup().lookupClass());
    private final AnsiOutputStream jansiStream;
    private final ByteArrayOutputStream barrierBuffer = new ByteArrayOutputStream();
    // Long term memory that doesn't get reset
    private final ByteArrayOutputStream storageBuffer = new ByteArrayOutputStream();

    private static final int ESC = 27;
    private static final int SHIFT_IN = 15;
    private static final int SHIFT_OUT = 14;

    public EscapeFilteredStream() {
        this("");
    }

    public EscapeFilteredStream(String name) {
        super(name);

        OutputStream optStream = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                barrierBuffer.write(b);
            }
            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                barrierBuffer.write(b, off, len);
            }
        };

        AnsiProcessor strippingProcessor = new AnsiProcessor(optStream) {
            @Override protected void processSetAttribute(int attribute) {}
            @Override protected void processSetForegroundColor(int color) {}
            @Override protected void processSetBackgroundColor(int color) {}
            @Override protected void processEraseScreen(int eraseOption) {}
            @Override protected void processEraseLine(int eraseOption) {}
        };

        this.jansiStream = new AnsiOutputStream(
                optStream,
                () -> 0,
                AnsiMode.Strip,
                strippingProcessor,
                AnsiType.Unsupported,
                AnsiColors.TrueColor,
                StandardCharsets.UTF_8,
                null,
                null,
                false
        );
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        byte[] filtered = new byte[len];
        int fIdx = 0;

        for (int i = 0; i < len; i++) {
            byte c = b[off + i];

            if (c == 0) {
                continue;
            }

            if (c == SHIFT_IN || c == SHIFT_OUT) {
                continue;
            }

            if (c == '#' && i + 1 < len && b[off + i + 1] == ESC) {
                continue;
            }

            if (c == ESC && i + 1 < len) {
                byte next = b[off + i + 1];
                if (next == '=' || next == '>') {
                    i++;
                    continue;
                }
            }

            filtered[fIdx++] = c;
        }

        if (fIdx > 0) {
            jansiStream.write(filtered, 0, fIdx);
            jansiStream.flush();

            if (barrierBuffer.size() > 0) {
                byte[] data = barrierBuffer.toByteArray();
                //System.out.println("DEBUG: Capturing to Storage buffer: " + new String(data, StandardCharsets.UTF_8));
                logger.debug("Capturing to Storage buffer:" + new String(data, StandardCharsets.UTF_8));

                //Resetting storagebuffer
                if(storageBuffer.size() + data.length > 20 * 1024){
                    storageBuffer.reset();
                }
                storageBuffer.write(data);

                superWrite(data, 0, barrierBuffer.size());
                barrierBuffer.reset();
            }
        }
    }

    @Override
    public void write(byte[] b) throws IOException {
        write(b, 0, b.length);
    }

    @Override
    public void flush() throws IOException {
        jansiStream.flush();
    }

    @Override
    public void close() throws IOException {
        //Resetting the indexes of barrierBuffer
        if (barrierBuffer.size() > 0) {
            byte[] data = barrierBuffer.toByteArray();

            if (storageBuffer.size() + data.length <= 20 * 1024) {
                storageBuffer.write(data);
            }
            superWrite(data, 0, data.length);
            barrierBuffer.reset();
        }
        jansiStream.close();
    }

    protected void superWrite(byte[] b, int off, int len) throws IOException {
        if (len < 0 || b.length - off < len) {
            logger.error("superWrite invalid write");
        }
        super.write(b, off, len);
    }

    public String getBuffered() {
        return storageBuffer.toString(StandardCharsets.UTF_8);
    }
}