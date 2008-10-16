package org.openamq.client.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.openamq.AMQException;
import org.openamq.framing.BasicDeliverBody;
import org.openamq.client.state.AMQStateManager;
import org.openamq.client.state.StateAwareMethodListener;
import org.openamq.client.protocol.AMQMethodEvent;
import org.openamq.client.message.UnprocessedMessage;

public class BasicDeliverMethodHandler implements StateAwareMethodListener
{
    private static final Logger _logger = LoggerFactory.getLogger(BasicDeliverMethodHandler.class);

    private static final BasicDeliverMethodHandler _instance = new BasicDeliverMethodHandler();

    public static BasicDeliverMethodHandler getInstance()
    {
        return _instance;
    }

    public void methodReceived(AMQStateManager stateManager, AMQMethodEvent evt) throws AMQException
    {
        final UnprocessedMessage msg = new UnprocessedMessage();
        msg.deliverBody = (BasicDeliverBody) evt.getMethod();
        msg.channelId = evt.getChannelId();
        _logger.debug("New JmsDeliver method received");
        evt.getProtocolSession().unprocessedMessageReceived(msg);
    }
}
