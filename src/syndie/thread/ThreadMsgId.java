package syndie.thread;

import net.i2p.data.Hash;

public final class ThreadMsgId {
    public final long msgId;
    public long messageId;
    public long authorScopeId;
    public Hash scope;
    public boolean unreadable;
    public Boolean authorized;

    public ThreadMsgId(long id) {
        msgId = id;
        messageId = -1;
        authorScopeId = -1;
        scope = null;
        unreadable = false;
        authorized = null;
    }

    public int hashCode() { return messageId >= 0 ? (int)messageId : (int)msgId; }

    public boolean equals(Object obj) throws ClassCastException {
        if (obj == this) return true;
        if (obj == null) return false;
        ThreadMsgId tmi = (ThreadMsgId)obj;
        if ( (tmi.msgId >= 0) && (msgId >= 0) )
            return tmi.msgId == msgId;
        else
            return (tmi.messageId == messageId) && (tmi.scope != null) && (tmi.scope.equals(scope));
    }

    public String toString() {
        return msgId + "/" + (scope != null ? scope.toBase64().substring(0,6) + ":" + messageId : "");
    }
}
