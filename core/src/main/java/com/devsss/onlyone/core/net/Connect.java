package com.devsss.onlyone.core.net;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public interface Connect {

    InputStream getInputStream() throws IOException;

    OutputStream getOutStream() throws IOException;

    boolean stop();

    boolean start();

    void write(byte[] msg) throws IOException;

}
