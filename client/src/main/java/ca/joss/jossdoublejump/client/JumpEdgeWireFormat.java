package ca.joss.jossdoublejump.client;

/**
 * Builds {@code jdj:v1:{"v":1,"seq":n,"t":"DOWN"|"UP"}} for {@link com.hypixel.hytale.protocol.packets.interface_.CustomPageEvent#data}.
 * Use from the Hytale client mod once wired to the jump key and packet send path.
 */
public final class JumpEdgeWireFormat {

    public static final String PREFIX = "jdj:v1:";

    private JumpEdgeWireFormat() {}

    /** Escape minimal JSON without pulling Gson on the client if undesired. */
    public static String build(long seq, boolean down) {
        String t = down ? "DOWN" : "UP";
        return PREFIX + "{\"v\":1,\"seq\":" + seq + ",\"t\":\"" + t + "\"}";
    }
}
