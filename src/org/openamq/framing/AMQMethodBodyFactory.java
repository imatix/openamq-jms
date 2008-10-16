package org.openamq.framing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.mina.common.ByteBuffer;

public class AMQMethodBodyFactory implements BodyFactory
{
    private static final Logger _log = LoggerFactory.getLogger(AMQMethodBodyFactory.class);
    
    private static final AMQMethodBodyFactory _instance = new AMQMethodBodyFactory();
    
    public static AMQMethodBodyFactory getInstance()
    {
        return _instance;
    }
    
    private AMQMethodBodyFactory()
    {
        _log.debug("Creating method body factory");
    }

    public AMQBody createBody(ByteBuffer in) throws AMQFrameDecodingException
    {
        return MethodBodyDecoderRegistry.get(in.getUnsignedShort(), in.getUnsignedShort());        
    }
}
