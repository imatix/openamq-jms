package org.openamq.client.protocol;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class HeartbeatConfig
{
    private static final Logger _logger = LoggerFactory.getLogger(HeartbeatConfig.class);
    static final HeartbeatConfig CONFIG = new HeartbeatConfig();

    /**
     * The factor used to get the timeout from the delay between heartbeats.
     */
    private float timeoutFactor = 2;

    HeartbeatConfig()
    {
        String property = System.getProperty("amqj.heartbeat.timeoutFactor");
        if(property != null)
        {
            try
            {
                timeoutFactor = Float.parseFloat(property);
            }
            catch(NumberFormatException e)
            {
                _logger.warn("Invalid timeout factor (amqj.heartbeat.timeoutFactor): " + property);
            }
        }
    }

    float getTimeoutFactor()
    {
        return timeoutFactor;
    }

    int getTimeout(int writeDelay)
    {
        return (int) (timeoutFactor * writeDelay);
    }
}
