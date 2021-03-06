package org.openamq.nio;

import java.io.IOException;

import org.apache.mina.common.ByteBuffer;
import org.apache.mina.common.IoFilterChain;
import org.apache.mina.common.IoSession;
import org.apache.mina.common.IoFilter.WriteRequest;
import org.apache.mina.common.support.AbstractIoFilterChain;
import org.apache.mina.util.Queue;

/**
 * An {@link IoFilterChain} for socket transport (TCP/IP).
 * 
 * @author The Apache Directory Project
 */
class SocketFilterChain extends AbstractIoFilterChain {

    public SocketFilterChain( IoSession parent )
    {
        super( parent );
    }

    protected void doWrite( IoSession session, WriteRequest writeRequest )
    {
        SocketSessionImpl s = ( SocketSessionImpl ) session;
        Queue writeRequestQueue = s.getWriteRequestQueue();
        
        // SocketIoProcessor.doFlush() will reset it after write is finished
        // because the buffer will be passed with messageSent event. 
        ( ( ByteBuffer ) writeRequest.getMessage() ).mark();
        synchronized( writeRequestQueue )
        {
            writeRequestQueue.push( writeRequest );
            if( writeRequestQueue.size() == 1 && session.getTrafficMask().isWritable() )
            {
                // Notify SocketIoProcessor only when writeRequestQueue was empty.
                s.getIoProcessor().flush( s );
            }
        }
    }

    protected void doClose( IoSession session ) throws IOException
    {
        SocketSessionImpl s = ( SocketSessionImpl ) session;
        s.getIoProcessor().remove( s );
    }
}
