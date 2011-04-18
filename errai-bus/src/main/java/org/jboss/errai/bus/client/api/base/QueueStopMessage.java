package org.jboss.errai.bus.client.api.base;

import org.jboss.errai.bus.client.api.ErrorCallback;
import org.jboss.errai.bus.client.api.HasEncoded;
import org.jboss.errai.bus.client.api.Message;
import org.jboss.errai.bus.client.api.ResourceProvider;
import org.jboss.errai.bus.client.framework.MessageBus;
import org.jboss.errai.bus.client.framework.RequestDispatcher;
import org.jboss.errai.bus.client.framework.RoutingFlags;
import org.jboss.errai.bus.client.protocols.BusCommands;
import org.jboss.errai.bus.client.protocols.MessageParts;
import org.jboss.errai.bus.server.io.JSONEncoder;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Mike Brock .
 */
public class QueueStopMessage implements Message {
    static final Map<String, Object> parts;
    static final String encoded;

    static {
        Map<String, Object> p = new HashMap<String,Object>();
        p.put(MessageParts.ToSubject.name(), "ClientBus");
        p.put(MessageParts.CommandType.name(), BusCommands.SessionExpired.name());

        parts = Collections.unmodifiableMap(p);

        encoded = JSONEncoder.encode(p);
    }

    public Message toSubject(String subject) {
        return null;  
    }

    public String getSubject() {
        return null;  
    }

    public Message command(String type) {
        return null;  
    }

    public Message command(Enum<?> type) {
        return null;  
    }

    public String getCommandType() {
        return null;  
    }

    public Message set(String part, Object value) {
        return null;  
    }

    public Message set(Enum<?> part, Object value) {
        return null;  
    }

    public Message setProvidedPart(String part, ResourceProvider provider) {
        return null;  
    }

    public Message setProvidedPart(Enum<?> part, ResourceProvider provider) {
        return null;  
    }

    public boolean hasPart(String part) {
        return false;  
    }

    public boolean hasPart(Enum<?> part) {
        return false;  
    }

    public void remove(String part) {
        
    }

    public void remove(Enum<?> part) {
        
    }

    public Message copy(String part, Message m) {
        return null;  
    }

    public Message copy(Enum<?> part, Message m) {
        return null;  
    }

    public Message setParts(Map<String, Object> parts) {
        return null;  
    }

    public Message addAllParts(Map<String, Object> parts) {
        return null;  
    }

    public Message addAllProvidedParts(Map<String, ResourceProvider> provided) {
        return null;  
    }

    public Map<String, Object> getParts() {
        return parts;
    }

    public Map<String, ResourceProvider> getProvidedParts() {
        return null;  
    }

    public void addResources(Map<String, ?> resources) {
        
    }

    public Message setResource(String key, Object res) {
        return null;  
    }

    public <T> T getResource(Class<T> type, String key) {
        return null;  
    }

    public boolean hasResource(String key) {
        return false;  
    }

    public Message copyResource(String key, Message m) {
        return null;  
    }

    public Message errorsCall(ErrorCallback callback) {
        return null;  
    }

    public ErrorCallback getErrorCallback() {
        return null;  
    }

    public <T> T get(Class<T> type, String part) {
        return null;  
    }

    public <T> T get(Class<T> type, Enum<?> part) {
        return null;  
    }

    public void setFlag(RoutingFlags flag) {
        
    }

    public void unsetFlag(RoutingFlags flag) {
        
    }

    public boolean isFlagSet(RoutingFlags flag) {
        return false;  
    }

    public void commit() {
        
    }

    public boolean isCommited() {
        return true;
    }


    public String getEncoded() {
        return encoded;
    }

    public void sendNowWith(MessageBus viaThis) {
        
    }

    public void sendNowWith(RequestDispatcher viaThis) {
        
    }
}
