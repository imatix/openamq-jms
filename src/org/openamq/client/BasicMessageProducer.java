package org.openamq.client;

import org.apache.log4j.Logger;
import org.apache.mina.common.ByteBuffer;
import org.openamq.client.protocol.AMQProtocolHandler;
import org.openamq.client.state.listener.SpecificMethodFrameListener;
import org.openamq.client.message.AbstractJMSMessage;
import org.openamq.client.message.JMSBytesMessage;
import org.openamq.AMQException;
import org.openamq.framing.*;

import javax.jms.*;
import java.io.UnsupportedEncodingException;

public class BasicMessageProducer extends Closeable implements org.openamq.jms.MessageProducer
{
    protected final Logger _logger = Logger.getLogger(getClass());

    private AMQConnection _connection;

    /**
     * If true, messages will not get a timestamp.
     */
    private boolean _disableTimestamps;

    /**
     * Priority of messages created by this producer.
     */
    private int _messagePriority;

    /**
     * Time to live of messages. Specified in milliseconds but AMQ has 1 second resolution.
     */
    private long _timeToLive;

    /**
     * Delivery mode used for this producer.
     */
    private int _deliveryMode = DeliveryMode.PERSISTENT;

    /**
     * The Destination used for this consumer, if specified upon creation.
     */
    protected AMQDestination _destination;

    /**
     * Default encoding used for messages produced by this producer.
     */
    private String _encoding;

    /**
     * Default encoding used for message produced by this producer.
     */
    private String _mimeType;

    private AMQProtocolHandler _protocolHandler;

    /**
     * True if this producer was created from a transacted session
     */
    private boolean _transacted;

    private int _channelId;

    /**
     * This is an id generated by the session and is used to tie individual producers to the session. This means we
     * can deregister a producer with the session when the producer is clsoed. We need to be able to tie producers
     * to the session so that when an error is propagated to the session it can close the producer (meaning that
     * a client that happens to hold onto a producer reference will get an error if he tries to use it subsequently).
     */
    private long _producerId;

    /**
     * The session used to create this producer
     */
    private AMQSession _session;

    private final boolean _immediate;

    private final boolean _mandatory;

    private final boolean _waitUntilSent;

    protected BasicMessageProducer(AMQConnection connection, AMQDestination destination, boolean transacted,
                                   int channelId, AMQSession session, AMQProtocolHandler protocolHandler,
                                   long producerId, boolean immediate, boolean mandatory, boolean waitUntilSent)
            throws AMQException
    {
        _connection = connection;
        _destination = destination;
        _transacted = transacted;
        _protocolHandler = protocolHandler;
        _channelId = channelId;
        _session = session;
        _producerId = producerId;
        if (destination != null)
        {
            declareDestination(destination);
        }
        _immediate = immediate;
        _mandatory = mandatory;
        _waitUntilSent = waitUntilSent;
    }

    void resubscribe() throws AMQException
    {
         if (_destination != null)
         {
             declareDestination(_destination);
         }
    }

    private void declareDestination(AMQDestination destination) throws AMQException
    {
        // Declare the exchange
        // Note that the durable and internal arguments are ignored since passive is set to false
        AMQFrame declare = ExchangeDeclareBody.createAMQFrame(_channelId, 0, destination.getExchangeName(),
                                                              destination.getExchangeClass(), false,
                                                              false, false, false, true, null);
        _protocolHandler.writeFrame(declare);
    }

    public void setDisableMessageID(boolean b) throws JMSException
    {
        checkNotClosed();
        // IGNORED
    }

    public boolean getDisableMessageID() throws JMSException
    {
        checkNotClosed();
        // Always false for OpenAMQ
        return false;
    }

    public void setDisableMessageTimestamp(boolean b) throws JMSException
    {
        checkNotClosed();
        _disableTimestamps = b;
    }

    public boolean getDisableMessageTimestamp() throws JMSException
    {
        checkNotClosed();
        return _disableTimestamps;
    }

    public void setDeliveryMode(int i) throws JMSException
    {
        checkNotClosed();
        if (i != DeliveryMode.NON_PERSISTENT && i != DeliveryMode.PERSISTENT)
        {
            throw new JMSException("DeliveryMode must be either NON_PERSISTENT or PERSISTENT. Value of " + i +
                                   " is illegal");
        }
        _deliveryMode = i;
    }

    public int getDeliveryMode() throws JMSException
    {
        checkNotClosed();
        return _deliveryMode;
    }

    public void setPriority(int i) throws JMSException
    {
        checkNotClosed();
        if (i < 0 || i > 9)
        {
            throw new IllegalArgumentException("Priority of " + i + " is illegal. Value must be in range 0 to 9");
        }
        _messagePriority = i;
    }

    public int getPriority() throws JMSException
    {
        checkNotClosed();
        return _messagePriority;
    }

    public void setTimeToLive(long l) throws JMSException
    {
        checkNotClosed();
        if (l < 0)
        {
            throw new IllegalArgumentException("Time to live must be non-negative - supplied value was " + l);
        }
        _timeToLive = l;
    }

    public long getTimeToLive() throws JMSException
    {
        checkNotClosed();
        return _timeToLive;
    }

    public Destination getDestination() throws JMSException
    {
        checkNotClosed();
        return _destination;
    }

    public void close() throws JMSException
    {
        _closed.set(true);
        _session.deregisterProducer(_producerId);
    }

    public void send(Message message) throws JMSException
    {
        synchronized (_connection.getFailoverMutex())
        {
            sendImpl(_destination, (AbstractJMSMessage) message, _deliveryMode, _messagePriority, _timeToLive,
                     _mandatory, _immediate);
        }
    }

    public void send(Message message, int deliveryMode, int priority,
                     long timeToLive) throws JMSException
    {
        synchronized (_connection.getFailoverMutex())
        {
            sendImpl(_destination, (AbstractJMSMessage)message, deliveryMode, priority, timeToLive, _mandatory,
                     _immediate);
        }
    }

    public void send(Destination destination, Message message) throws JMSException
    {
        checkNotClosed();
        synchronized (_connection.getFailoverMutex())
        {
            validateDestination(destination);
            sendImpl((AMQDestination) destination, (AbstractJMSMessage) message, _deliveryMode, _messagePriority, _timeToLive,
                     _mandatory, _immediate);
        }
    }

    public void send(Destination destination, Message message, int deliveryMode,
                     int priority, long timeToLive)
            throws JMSException
    {
        checkNotClosed();
        synchronized (_connection.getFailoverMutex())
        {
            validateDestination(destination);
            sendImpl((AMQDestination) destination, (AbstractJMSMessage) message, deliveryMode, priority, timeToLive,
                     _mandatory, _immediate);
        }
    }

    public void send(Destination destination, Message message, int deliveryMode,
                     int priority, long timeToLive, boolean mandatory)
            throws JMSException
    {
        checkNotClosed();
        synchronized (_connection.getFailoverMutex())
        {
            validateDestination(destination);
            sendImpl((AMQDestination) destination, (AbstractJMSMessage) message, deliveryMode, priority, timeToLive,
                     mandatory, _immediate);
        }
    }

    public void send(Destination destination, Message message, int deliveryMode,
                     int priority, long timeToLive, boolean mandatory, boolean immediate)
            throws JMSException
    {
        checkNotClosed();
        synchronized (_connection.getFailoverMutex())
        {
            validateDestination(destination);
            sendImpl((AMQDestination) destination, (AbstractJMSMessage) message, deliveryMode, priority, timeToLive,
                     mandatory, immediate);
        }
    }

    public void send(Destination destination, Message message, int deliveryMode,
                     int priority, long timeToLive, boolean mandatory,
                     boolean immediate, boolean waitUntilSent)
            throws JMSException
    {
        checkNotClosed();
        synchronized (_connection.getFailoverMutex())
        {
            validateDestination(destination);
            sendImpl((AMQDestination) destination, (AbstractJMSMessage) message, deliveryMode, priority, timeToLive,
                     mandatory, immediate, waitUntilSent);
        }
    }

    private void validateDestination(Destination destination) throws JMSException
    {
        if (!(destination instanceof AMQDestination))
        {
            throw new JMSException("Unsupported destination class: " +
                                   (destination != null?destination.getClass():null));
        }
        try
        {
            declareDestination((AMQDestination)destination);
        }
        catch (AMQException e)
        {
            throw new JMSException("Unable to declare destination " + destination + ": " + e);
        }
    }

    protected void sendImpl(AMQDestination destination, AbstractJMSMessage message, int deliveryMode, int priority,
                          long timeToLive, boolean mandatory, boolean immediate) throws JMSException
    {
        sendImpl(destination, message, deliveryMode, priority, timeToLive, mandatory, immediate, _waitUntilSent);
    }
    /**
     * The caller of this method must hold the failover mutex.
     * @param destination
     * @param message
     * @param deliveryMode
     * @param priority
     * @param timeToLive
     * @param mandatory
     * @param immediate
     * @throws JMSException
     */
    protected void sendImpl(AMQDestination destination, AbstractJMSMessage message, int deliveryMode, int priority,
                          long timeToLive, boolean mandatory, boolean immediate, boolean wait) throws JMSException
    {
        AMQFrame publishFrame = BasicPublishBody.createAMQFrame(_channelId, 0, destination.getExchangeName(),
                                                                destination.getRoutingKey(), mandatory, immediate);

        long currentTime = 0;
        if (!_disableTimestamps)
        {
            currentTime = System.currentTimeMillis();
            message.setJMSTimestamp(currentTime);
        }
        //
        // Very nasty temporary hack for GRM-206. Will be altered ASAP.
        //
        if(message instanceof JMSBytesMessage)
        {
            JMSBytesMessage msg = (JMSBytesMessage) message;
            if(!msg.isReadable())
            {
                msg.reset();
            }
        }
        ByteBuffer payload = message.getData();
        BasicContentHeaderProperties contentHeaderProperties = message.getJmsContentHeaderProperties();

        if (timeToLive > 0)
        {
            if (!_disableTimestamps)
            {
                contentHeaderProperties.setExpiration(currentTime + timeToLive);
            }
        }
        else
        {
            if (!_disableTimestamps)
            {
                contentHeaderProperties.setExpiration(0);
            }
        }
        contentHeaderProperties.setDeliveryMode((byte) deliveryMode);
        contentHeaderProperties.setPriority((byte) priority);

        int size = payload.limit();
        ContentBody[] contentBodies = createContentBodies(payload);
        AMQFrame[] frames = new AMQFrame[2 + contentBodies.length];
        for (int i = 0; i < contentBodies.length; i++)
        {
            frames[2 + i] = ContentBody.createAMQFrame(_channelId, contentBodies[i]);
        }
        if (contentBodies.length > 0 && _logger.isDebugEnabled())
        {
            _logger.debug("Sending content body frames to " + destination);
        }

        // weight argument of zero indicates no child content headers, just bodies
        AMQFrame contentHeaderFrame = ContentHeaderBody.createAMQFrame(_channelId, BasicConsumeBody.CLASS_ID, 0,
                                                                       contentHeaderProperties,
                                                                       size);
        if (_logger.isDebugEnabled())
        {
            _logger.debug("Sending content header frame to " + destination);
        }

        frames[0] = publishFrame;
        frames[1] = contentHeaderFrame;
        CompositeAMQDataBlock compositeFrame = new CompositeAMQDataBlock(frames);
        _protocolHandler.writeFrame(compositeFrame, wait);
    }

    /**
     * Create content bodies. This will split a large message into numerous bodies depending on the negotiated
     * maximum frame size.
     * @param payload
     * @return the array of content bodies
     */
    private ContentBody[] createContentBodies(ByteBuffer payload)
    {
        if (payload == null)
        {
            return null;
        }
        else if (payload.remaining() == 0)
        {
            return new ContentBody[0];
        }
        // we substract one from the total frame maximum size to account for the end of frame marker in a body frame
        // (0xCE byte).
        int dataLength = payload.remaining();
        final long framePayloadMax = _session.getAMQConnection().getMaximumFrameSize() - 1;
        int lastFrame = (dataLength % framePayloadMax) > 0 ? 1 : 0;
        int frameCount = (int) (dataLength/framePayloadMax) + lastFrame;
        final ContentBody[] bodies = new ContentBody[frameCount];

        if (frameCount == 1)
        {
            bodies[0] = new ContentBody();
            bodies[0].payload = payload;
        }
        else
        {
            long remaining = dataLength;
            for (int i = 0; i < bodies.length; i++)
            {
                bodies[i] = new ContentBody();
                payload.position((int)framePayloadMax * i);
                int length = (remaining >= framePayloadMax) ? (int)framePayloadMax : (int)remaining;
                payload.limit(payload.position() + length);
                bodies[i].payload = payload.slice();
                remaining -= length;
            }
        }
        return bodies;
    }

    public void setMimeType(String mimeType)
    {
        checkNotClosed();
        _mimeType = mimeType;
    }

    public void setEncoding(String encoding) throws UnsupportedEncodingException
    {
        checkNotClosed();
        _encoding = encoding;
    }
}
