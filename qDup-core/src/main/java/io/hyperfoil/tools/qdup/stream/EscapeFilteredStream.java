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
    private final ByteArrayOutputStream storageBuffer = new ByteArrayOutputStream();

    private static final int ESC = 27;
    private static final int SHIFT_IN = 15;
    private static final int SHIFT_OUT = 14;
    private boolean skipNext = false;

    public EscapeFilteredStream() {
        this("");
    }

    public EscapeFilteredStream(String name) {
        super(name);

        OutputStream optStream = new OutputStream() {
            @Override
            public void write(int b) throws IOException {

                superWrite(new byte[]{(byte)b}, 0, 1);
            }
            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                superWrite(b, off, len);
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
        //byte[] filtered = new byte[len];
        int fIdx = 0;


        for (int i = 0; i < len; i++) {

            if (skipNext) {
                skipNext = false;
                continue;
            }

            byte c = b[off + i];

            if (c == 0) {
                continue;
            }

            if(c == 8){
                if(fIdx>0){
                    fIdx --;
                }
                else{
                    skipNext = true;
                }
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

            jansiStream.write(c);
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
        jansiStream.close();
    }

        public void reset() {
        }


    protected void superWrite(byte[] b, int off, int len) throws IOException {
        if (len < 0 || b.length - off < len) {
            logger.error("superWrite invalid write");
        }
        super.write(b, off, len);
    }

    protected void superWrite(int b) throws IOException{
        super.write(b);
    }


    public String getBuffered() {
        return "";
    }
}