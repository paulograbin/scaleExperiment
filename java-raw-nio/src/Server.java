package com.paulograbin.scale;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

public class Server {

    private static final byte[] RESPONSE_BYTES = (
            "HTTP/1.1 200 OK\r\n" +
            "Content-Type: text/plain\r\n" +
            "Content-Length: 13\r\n" +
            "Connection: keep-alive\r\n" +
            "\r\n" +
            "Hello, World!"
    ).getBytes();

    private static final byte[] HEALTH_RESPONSE_BYTES = (
            "HTTP/1.1 200 OK\r\n" +
            "Content-Type: application/json\r\n" +
            "Content-Length: 15\r\n" +
            "Connection: keep-alive\r\n" +
            "\r\n" +
            "{\"status\":\"UP\"}"
    ).getBytes();

    public static void main(String[] args) throws IOException {
        int threads = Runtime.getRuntime().availableProcessors();
        System.out.println("Starting NIO server on :8080 with " + threads + " event loops");

        var workers = new EventLoop[threads];
        for (int i = 0; i < threads; i++) {
            workers[i] = new EventLoop();
            var thread = new Thread(workers[i], "worker-" + i);
            thread.setDaemon(false);
            thread.start();
        }

        var serverChannel = ServerSocketChannel.open();
        serverChannel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
        serverChannel.configureBlocking(true);
        serverChannel.bind(new InetSocketAddress(8080), 4096);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down...");
            try { serverChannel.close(); } catch (IOException ignored) {}
        }));

        int next = 0;
        while (serverChannel.isOpen()) {
            try {
                var client = serverChannel.accept();
                client.configureBlocking(false);
                client.setOption(StandardSocketOptions.TCP_NODELAY, true);
                workers[next].register(client);
                next = (next + 1) % threads;
            } catch (IOException e) {
                if (serverChannel.isOpen()) e.printStackTrace();
            }
        }
    }

    static class EventLoop implements Runnable {
        private final Selector selector;

        EventLoop() throws IOException {
            this.selector = Selector.open();
        }

        void register(SocketChannel client) {
            try {
                client.register(selector, SelectionKey.OP_READ, ByteBuffer.allocate(1024));
            } catch (IOException e) {
                try { client.close(); } catch (IOException ignored) {}
            }
            selector.wakeup();
        }

        @Override
        public void run() {
            try {
                while (true) {
                    selector.select(100);
                    var keys = selector.selectedKeys();
                    Iterator<SelectionKey> iter = keys.iterator();

                    while (iter.hasNext()) {
                        var key = iter.next();
                        iter.remove();

                        if (key.isReadable()) {
                            read(key);
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void read(SelectionKey key) {
            var client = (SocketChannel) key.channel();
            var buffer = (ByteBuffer) key.attachment();

            try {
                buffer.clear();
                int bytesRead = client.read(buffer);
                if (bytesRead == -1) {
                    key.cancel();
                    client.close();
                    return;
                }

                byte[] response = routeRequest(buffer, bytesRead);
                client.write(ByteBuffer.wrap(response));
            } catch (IOException e) {
                key.cancel();
                try { client.close(); } catch (IOException ignored) {}
            }
        }

        private byte[] routeRequest(ByteBuffer buffer, int length) {
            if (length > 10 && buffer.get(5) == 'a') {
                return HEALTH_RESPONSE_BYTES;
            }
            return RESPONSE_BYTES;
        }
    }
}
