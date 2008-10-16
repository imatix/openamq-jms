package org.openamq.client;

import javax.jms.Destination;

public abstract class AMQDestination implements Destination
{
    public final static String TOPIC_EXCHANGE_NAME = "amq.topic";

    public final static String TOPIC_EXCHANGE_CLASS = "topic";

    public final static String QUEUE_EXCHANGE_NAME = "amq.direct";

    public final static String QUEUE_EXCHANGE_CLASS = "direct";

    public final static String HEADERS_EXCHANGE_NAME = "amq.headers";

    public final static String HEADERS_EXCHANGE_CLASS = "headers";

    protected final String _destinationName;

    protected boolean _isDurable;

    protected final boolean _isExclusive;

    protected final boolean _isAutoDelete;

    protected String _queueName;

    protected String _exchangeName;

    protected String _exchangeClass;

    protected final boolean _isExchangeDurable;

    protected final boolean _isExchangeAutoDelete;

    protected AMQDestination(String exchangeName, String exchangeClass, String destinationName, String queueName)
    {
        this(exchangeName, exchangeClass, false, false, destinationName, false, false, false, queueName);
    }

    protected AMQDestination(String exchangeName, String exchangeClass, String destinationName)
    {
        this(exchangeName, exchangeClass, false, false, destinationName, false, false, true, destinationName);
    }

    protected AMQDestination(String exchangeName, String exchangeClass, String destinationName, boolean isExclusive,
                             boolean isAutoDelete, String queueName)
    {
        this(exchangeName, exchangeClass, false, false, destinationName, false, isExclusive, isAutoDelete, queueName);
    }

    protected AMQDestination(String exchangeName, String exchangeClass, String destinationName, boolean isDurable, boolean isExclusive, boolean isAutoDelete, String queueName)
    {
        this(exchangeName, exchangeClass, false, false, destinationName, isDurable, isExclusive, isAutoDelete, queueName);
    }

    protected AMQDestination(String exchangeName, String exchangeClass, boolean isExchangeDurable, boolean isExchangeAutoDelete, String destinationName, boolean isDurable, boolean isExclusive, boolean isAutoDelete, String queueName)
    {
        if (destinationName == null)
        {
            throw new IllegalArgumentException("Destination name must not be null");
        }
        if (exchangeName == null)
        {
            throw new IllegalArgumentException("Exchange name must not be null");
        }
        if (exchangeClass == null)
        {
            throw new IllegalArgumentException("Exchange class must not be null");
        }
        _exchangeName = exchangeName;
        _exchangeClass = exchangeClass;
        _destinationName = destinationName;
        _isExclusive = isExclusive;
        _isAutoDelete = isAutoDelete;
        _queueName = queueName;
        _isDurable = isDurable;
        _isExchangeDurable = isExchangeDurable;
        _isExchangeAutoDelete = isExchangeAutoDelete;
    }

    public abstract String getEncodedName();

    public boolean isDurable()
    {
        return _isDurable;
    }

    public String getExchangeName()
    {
        return _exchangeName;
    }

    public String getExchangeClass()
    {
        return _exchangeClass;
    }

    public boolean getExchangeDurable()
    {
        return _isExchangeDurable;
    }

    public boolean getExchangeAutoDelete()
    {
        return _isExchangeAutoDelete;
    }

    public void setExchangeName(String exchangeName)
    {
        _exchangeName = exchangeName;
    }

    public void setExchangeClass(String exchangeClass)
    {
        _exchangeClass = exchangeClass;
    }

    public boolean isTopic()
    {
        return TOPIC_EXCHANGE_CLASS.equals(_exchangeClass);
    }

    public boolean isQueue()
    {
        return QUEUE_EXCHANGE_CLASS.equals(_exchangeClass);
    }

    public String getDestinationName()
    {
        return _destinationName;
    }

    public String getQueueName()
    {
        return _queueName;
    }

    public void setQueueName(String queueName)
    {
        _queueName = queueName;
    }

    public abstract String getRoutingKey();

    public boolean isExclusive()
    {
        return _isExclusive;
    }

    public boolean isAutoDelete()
    {
        return _isAutoDelete;
    }

    public boolean isExchangeDurable()
    {
        return _isExchangeDurable;
    }

    public boolean isExchangeAutoDelete()
    {
        return _isExchangeAutoDelete;
    }

    public abstract boolean isNameRequired();

    public String toString()
    {
        return "Destination: " + _destinationName + ", " +
               "Queue Name: " + _queueName + ", Exchange: " + _exchangeName +
               ", Exchange class: " + _exchangeClass + ", Exclusive: " + _isExclusive +
               ", AutoDelete: " + _isAutoDelete + ", Routing  Key: " + getRoutingKey();
    }

    public boolean equals(Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (o == null || getClass() != o.getClass())
        {
            return false;
        }

        final AMQDestination that = (AMQDestination) o;

        if (!_destinationName.equals(that._destinationName))
        {
            return false;
        }
        if (!_exchangeClass.equals(that._exchangeClass))
        {
            return false;
        }
        if (!_exchangeName.equals(that._exchangeName))
        {
            return false;
        }
        if ((_queueName == null && that._queueName != null) ||
            (_queueName != null && !_queueName.equals(that._queueName)))
        {
            return false;
        }
        if (_isExclusive != that._isExclusive)
        {
            return false;
        }
        if (_isAutoDelete != that._isAutoDelete)
        {
            return false;
        }
        return true;
    }

    public int hashCode()
    {
        int result;
        result = _exchangeName.hashCode();
        result = 29 * result + _exchangeClass.hashCode();
        result = 29 * result + _destinationName.hashCode();
        if (_queueName != null)
        {
            result = 29 * result + _queueName.hashCode();
        }
        result = result * (_isExclusive?13:7);
        result = result * (_isAutoDelete?13:7);
        return result;
    }
}
