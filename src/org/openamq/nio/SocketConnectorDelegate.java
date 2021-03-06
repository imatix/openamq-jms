/*
 *   @(#) $Id: SocketConnectorDelegate.java 379044 2006-02-20 07:40:37Z trustin $
 *
 *   Copyright 2004 The Apache Software Foundation
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */
package org.openamq.nio;

import org.apache.mina.common.*;
import org.apache.mina.common.support.BaseIoConnector;
import org.apache.mina.common.support.DefaultConnectFuture;
import org.apache.mina.transport.socket.nio.SocketConnectorConfig;
import org.apache.mina.util.Queue;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * {@link IoConnector} for socket transport (TCP/IP).
 *
 * @author The Apache Directory Project (dev@directory.apache.org)
 * @version $Rev: 379044 $, $Date: 2006-02-20 07:40:37 +0000 (Mon, 20 Feb 2006) $
 */
public class SocketConnectorDelegate extends BaseIoConnector
{
    private static volatile int nextId = 0;

    private final IoConnector wrapper;
    private final int id = nextId++;
    private final String threadName = "SocketConnector-" + id;
    private final IoServiceConfig defaultConfig = new SocketConnectorConfig();
    private Selector selector;
    private final Queue connectQueue = new Queue();
    private final Set managedSessions = Collections.synchronizedSet(new HashSet());
    private Worker worker;

    /**
     * Creates a new instance.
     */
    public SocketConnectorDelegate(IoConnector wrapper)
    {
        this.wrapper = wrapper;
    }

    public ConnectFuture connect(SocketAddress address, IoHandler handler, IoServiceConfig config)
    {
        return connect(address, null, handler, config);
    }

    public ConnectFuture connect(SocketAddress address, SocketAddress localAddress,
                                 IoHandler handler, IoServiceConfig config)
    {
        if (address == null)
        {
            throw new NullPointerException("address");
        }
        if (handler == null)
        {
            throw new NullPointerException("handler");
        }

        if (! (address instanceof InetSocketAddress))
        {
            throw new IllegalArgumentException("Unexpected address type: "
                    + address.getClass());
        }

        if (localAddress != null && !(localAddress instanceof InetSocketAddress))
        {
            throw new IllegalArgumentException("Unexpected local address type: "
                    + localAddress.getClass());
        }

        if (config == null)
        {
            config = getDefaultConfig();
        }

        SocketChannel ch = null;
        boolean success = false;
        try
        {
            ch = SocketChannel.open();
            ch.socket().setReuseAddress(true);
            if (localAddress != null)
            {
                ch.socket().bind(localAddress);
            }

            ch.configureBlocking(false);

            if (ch.connect(address))
            {
                SocketSessionImpl session = newSession(ch, handler, config);
                success = true;
                ConnectFuture future = new DefaultConnectFuture();
                future.setSession(session);
                return future;
            }

            success = true;
        }
        catch (IOException e)
        {
            return DefaultConnectFuture.newFailedFuture(e);
        }
        finally
        {
            if (!success && ch != null)
            {
                try
                {
                    ch.close();
                }
                catch (IOException e)
                {
                    ExceptionMonitor.getInstance().exceptionCaught(e);
                }
            }
        }

        ConnectionRequest request = new ConnectionRequest(ch, handler, config);
        synchronized (this)
        {
            try
            {
                startupWorker();
            }
            catch (IOException e)
            {
                try
                {
                    ch.close();
                }
                catch (IOException e2)
                {
                    ExceptionMonitor.getInstance().exceptionCaught(e2);
                }

                return DefaultConnectFuture.newFailedFuture(e);
            }
            synchronized (connectQueue)
            {
                connectQueue.push(request);
            }
            selector.wakeup();
        }

        return request;
    }

    public IoServiceConfig getDefaultConfig()
    {
        return defaultConfig;
    }

    private synchronized void startupWorker() throws IOException
    {
        if (worker == null)
        {
            selector = Selector.open();
            worker = new Worker();
            worker.start();
        }
    }

    private void registerNew()
    {
        if (connectQueue.isEmpty())
        {
            return;
        }

        for (; ;)
        {
            ConnectionRequest req;
            synchronized (connectQueue)
            {
                req = (ConnectionRequest) connectQueue.pop();
            }

            if (req == null)
            {
                break;
            }

            SocketChannel ch = req.channel;
            try
            {
                ch.register(selector, SelectionKey.OP_CONNECT, req);
            }
            catch (IOException e)
            {
                req.setException(e);
            }
        }
    }

    private void processSessions(Set keys)
    {
        Iterator it = keys.iterator();

        while (it.hasNext())
        {
            SelectionKey key = (SelectionKey) it.next();

            if (!key.isConnectable())
            {
                continue;
            }

            SocketChannel ch = (SocketChannel) key.channel();
            ConnectionRequest entry = (ConnectionRequest) key.attachment();

            boolean success = false;
            try
            {
                ch.finishConnect();
                SocketSessionImpl session = newSession(ch, entry.handler, entry.config);
                entry.setSession(session);
                success = true;
            }
            catch (Throwable e)
            {
                entry.setException(e);
            }
            finally
            {
                key.cancel();
                if (!success)
                {
                    try
                    {
                        ch.close();
                    }
                    catch (IOException e)
                    {
                        ExceptionMonitor.getInstance().exceptionCaught(e);
                    }
                }
            }
        }

        keys.clear();
    }

    private void processTimedOutSessions(Set keys)
    {
        long currentTime = System.currentTimeMillis();
        Iterator it = keys.iterator();

        while (it.hasNext())
        {
            SelectionKey key = (SelectionKey) it.next();

            if (!key.isValid())
            {
                continue;
            }

            ConnectionRequest entry = (ConnectionRequest) key.attachment();

            if (currentTime >= entry.deadline)
            {
                entry.setException(new ConnectException());
                key.cancel();
            }
        }
    }

    private SocketSessionImpl newSession(SocketChannel ch, IoHandler handler, IoServiceConfig config) throws IOException
    {
        SocketSessionImpl session = new SocketSessionImpl(
                wrapper, managedSessions,
                config.getSessionConfig(),
                ch, handler, ch.socket().getRemoteSocketAddress());
        try
        {
            getFilterChainBuilder().buildFilterChain(session.getFilterChain());
            config.getFilterChainBuilder().buildFilterChain(session.getFilterChain());
            ((SocketFilterChain) session.getFilterChain()).sessionCreated(session);
        }
        catch (Throwable e)
        {
            throw (IOException) new IOException("Failed to create a session.").initCause(e);
        }
        session.getManagedSessions().add(session);
        session.getIoProcessor().addNew(session);
        return session;
    }

    private class Worker extends Thread
    {
        public Worker()
        {
            super(SocketConnectorDelegate.this.threadName);
        }

        public void run()
        {
            for (; ;)
            {
                try
                {
                    int nKeys = selector.select(1000);

                    registerNew();

                    if (nKeys > 0)
                    {
                        processSessions(selector.selectedKeys());
                    }

                    processTimedOutSessions(selector.keys());

                    if (selector.keys().isEmpty())
                    {
                        synchronized (SocketConnectorDelegate.this)
                        {
                            if (selector.keys().isEmpty() &&
                                    connectQueue.isEmpty())
                            {
                                worker = null;
                                try
                                {
                                    selector.close();
                                }
                                catch (IOException e)
                                {
                                    ExceptionMonitor.getInstance().exceptionCaught(e);
                                }
                                finally
                                {
                                    selector = null;
                                }
                                break;
                            }
                        }
                    }
                }
                catch (IOException e)
                {
                    ExceptionMonitor.getInstance().exceptionCaught(e);

                    try
                    {
                        Thread.sleep(1000);
                    }
                    catch (InterruptedException e1)
                    {
                    }
                }
            }
        }
    }

    private class ConnectionRequest extends DefaultConnectFuture
    {
        private final SocketChannel channel;
        private final long deadline;
        private final IoHandler handler;
        private final IoServiceConfig config;

        private ConnectionRequest(SocketChannel channel, IoHandler handler, IoServiceConfig config)
        {
            this.channel = channel;
            long timeout;
            if (config instanceof IoConnectorConfig)
            {
                timeout = ((IoConnectorConfig) config).getConnectTimeoutMillis();
            }
            else
            {
                timeout = ((IoConnectorConfig) getDefaultConfig()).getConnectTimeoutMillis();
            }
            this.deadline = System.currentTimeMillis() + timeout;
            this.handler = handler;
            this.config = config;
        }
    }
}